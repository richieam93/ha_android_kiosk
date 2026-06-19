package com.hakiosk.android;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppSettings {
    private static final String PREFS = "ha_android_kiosk";

    private AppSettings() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String get(Context context, String key, String defValue) {
        try {
            Object value = prefs(context).getAll().get(key);
            return value == null ? defValue : String.valueOf(value);
        } catch (Exception ignored) {
            return defValue;
        }
    }

    public static boolean getBoolean(Context context, String key, boolean defValue) {
        try {
            Object value = prefs(context).getAll().get(key);
            if (value instanceof Boolean) return (Boolean) value;
            if (value instanceof String) {
                String text = ((String) value).trim();
                if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) return true;
                if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) return false;
            }
            return defValue;
        } catch (Exception ignored) {
            return defValue;
        }
    }

    public static int getInt(Context context, String key, int defValue) {
        try {
            Object value = prefs(context).getAll().get(key);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) return Integer.parseInt(((String) value).trim());
            return defValue;
        } catch (Exception ignored) {
            return defValue;
        }
    }

    public static long getLong(Context context, String key, long defValue) {
        try {
            Object value = prefs(context).getAll().get(key);
            if (value instanceof Number) return ((Number) value).longValue();
            if (value instanceof String) return Long.parseLong(((String) value).trim());
            return defValue;
        } catch (Exception ignored) {
            return defValue;
        }
    }

    public static void put(Context context, String key, String value) {
        prefs(context).edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    public static void putBoolean(Context context, String key, boolean value) {
        prefs(context).edit().putBoolean(key, value).apply();
    }

    public static void putInt(Context context, String key, int value) {
        prefs(context).edit().putInt(key, value).apply();
    }

    public static void putLong(Context context, String key, long value) {
        prefs(context).edit().putLong(key, value).apply();
    }

    public static String deviceId(Context context) {
        String existing = get(context, "device_id", "");
        if (!existing.isEmpty()) return existing;
        String id = "android_kiosk_" + android.provider.Settings.Secure.getString(
            context.getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        );
        put(context, "device_id", id);
        return id;
    }

    public static String baseUrl(Context context) {
        String value = get(context, "ha_url", "");
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static String token(Context context) {
        return get(context, "ha_token", "");
    }

    public static String refreshToken(Context context) {
        return get(context, "ha_refresh_token", "");
    }

    public static long tokenExpiresAt(Context context) {
        String value = get(context, "ha_token_expires_at", "0");
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static void saveTokens(Context context, String accessToken, String refreshToken, int expiresInSeconds) {
        long expiresAt = expiresInSeconds > 0 ? System.currentTimeMillis() + Math.max(60, expiresInSeconds - 60) * 1000L : 0L;
        SharedPreferences.Editor edit = prefs(context).edit();
        if (accessToken != null && !accessToken.trim().isEmpty()) edit.putString("ha_token", accessToken.trim());
        if (refreshToken != null && !refreshToken.trim().isEmpty()) edit.putString("ha_refresh_token", refreshToken.trim());
        edit.putString("ha_token_expires_at", String.valueOf(expiresAt));
        edit.apply();
    }

    public static boolean needsTokenRefresh(Context context) {
        if (refreshToken(context).isEmpty()) return false;
        if (token(context).isEmpty()) return true;
        long expiresAt = tokenExpiresAt(context);
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    public static boolean isConfigured(Context context) {
        return !baseUrl(context).isEmpty() && (!token(context).isEmpty() || !refreshToken(context).isEmpty());
    }
}
