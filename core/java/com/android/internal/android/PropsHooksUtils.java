/*
 * Copyright (C) 2020 The Pixel Experience Project
 *               2025 the AxionAOSP Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.android;

import android.content.Context;
import android.os.Build;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class PropsHooksUtils {

    private static final boolean DEBUG = false;
    private static final String TAG = "PropsHooksUtils";
    private static final String PACKAGE_VENDING = "com.android.vending";
    private static final int VENDING_TARGET_SDK_INT = 32;
    private static final String VENDING_TARGET_RELEASE_VERSION = "12";
    public static final String SPOOF_PIXEL_GPHOTOS = "persist.sys.pixelprops.gphotos";
    public static final String SPOOF_VENDING_SDK32_ENABLED = "persist.sys.spoof.vending_sdk32";
    private static volatile boolean sIsPhotos;
    private static final Map<String, Object> propsToChangePixelXL;
    private static final Map<String, Field> fieldCache = new HashMap<>();
    private static Boolean isPixelDevice = null;

    static {
        propsToChangePixelXL = new HashMap<>();
        propsToChangePixelXL.put("BRAND", "google");
        propsToChangePixelXL.put("MANUFACTURER", "Google");
        propsToChangePixelXL.put("DEVICE", "marlin");
        propsToChangePixelXL.put("PRODUCT", "marlin");
        propsToChangePixelXL.put("HARDWARE", "marlin");
        propsToChangePixelXL.put("ID", "QP1A.191005.007.A3");
        propsToChangePixelXL.put("MODEL", "Pixel XL");
        propsToChangePixelXL.put("FINGERPRINT", "google/marlin/marlin:10/QP1A.191005.007.A3/5972272:user/release-keys");
    }

    public static void setProps(Context context) {
        if (context == null) return;
        String packageName = context.getPackageName();
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        if (packageName.equals(PACKAGE_VENDING)) {
            if (SystemProperties.getBoolean(SPOOF_VENDING_SDK32_ENABLED, true)) {
                try {
                    dlog("Spoofing SDK version for " + packageName + " to SDK " + VENDING_TARGET_SDK_INT);
                    setVersionFieldInt("SDK_INT", VENDING_TARGET_SDK_INT);
                    setVersionFieldString("RELEASE", VENDING_TARGET_RELEASE_VERSION);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to spoof vending SDK version", e);
                }
            }
        }
        sIsPhotos = packageName.equals("com.google.android.apps.photos");
        if (shouldSpoofPhotos()) {
            for (Map.Entry<String, Object> entry : propsToChangePixelXL.entrySet()) {
                setPropValue(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void setPropValue(String key, Object newValue) {
        try {
            Field field = getBuildClassField(key);
            if (field == null) {
                dlog("Field " + key + " not found in Build or Build.VERSION classes");
                return;
            }
            Object currentValue = field.get(null);
            if (isObjectEqual(currentValue, newValue)) {
                return;
            }
            if (field.getType() == int.class) {
                field.setInt(null, newValue instanceof Integer ? (Integer) newValue : Integer.parseInt(newValue.toString()));
            } else if (field.getType() == long.class) {
                field.setLong(null, newValue instanceof Long ? (Long) newValue : Long.parseLong(newValue.toString()));
            } else {
                field.set(null, newValue.toString());
            }
            dlog("Set prop " + key + " to " + newValue);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Failed to set prop " + key, e);
        }
    }

    private static boolean isObjectEqual(Object oldValue, Object newValue) {
        if (oldValue == null) return newValue == null;
        return oldValue.toString().equals(newValue != null ? newValue.toString() : null);
    }

    private static Field getBuildClassField(String key) {
        Field field = fieldCache.get(key);
        if (field != null) {
            return field;
        }
        try {
            field = Build.class.getDeclaredField(key);
            dlog("Field " + key + " found in Build.class");
        } catch (NoSuchFieldException e) {
            try {
                field = Build.VERSION.class.getDeclaredField(key);
                dlog("Field " + key + " found in Build.VERSION.class");
            } catch (NoSuchFieldException ex) {
                Log.e(TAG, "Field " + key + " not found", ex);
                return null;
            }
        }
        field.setAccessible(true);
        fieldCache.put(key, field);
        return field;
    }

    public static boolean hasSystemFeature(String name, int version, boolean hasSystemFeature) {
        if (shouldSpoofPhotos()) {
            if (!isPixelDevice()) return false;
            return true;
        }
        return hasSystemFeature;
    }

    private static boolean shouldSpoofPhotos() {
        return sIsPhotos && SystemProperties.getBoolean(SPOOF_PIXEL_GPHOTOS, true);
    }

    private static boolean isPixelDevice() {
        if (isPixelDevice == null) {
            isPixelDevice = Build.BRAND.equalsIgnoreCase("google")
                    && Build.MANUFACTURER.equalsIgnoreCase("Google");
        }
        return isPixelDevice;
    }

    private static void dlog(String msg) {
        if (DEBUG) Log.d(TAG, msg);
    }

    private static void setVersionFieldInt(String key, int value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.setInt(null, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set " + key + " to " + value, e);
        }
    }

    private static void setVersionFieldString(String key, String value) {
        try {
            Field field = Build.VERSION.class.getDeclaredField(key);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set " + key + " to " + value, e);
        }
    }
}
