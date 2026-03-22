package com.sinproxy.android.utils;

/**
 * Platform-agnostic logging for SINproxy Desktop
 */
public class SINLog {
    private static final String TAG = "SINproxy";

    public static void i(String message) {
        System.out.println("[INFO] " + TAG + ": " + message);
    }

    public static void e(String message, Throwable t) {
        System.err.println("[ERROR] " + TAG + ": " + message);
        if (t != null) t.printStackTrace();
    }

    public static void d(String message) {
        System.out.println("[DEBUG] " + TAG + ": " + message);
    }

    public static void w(String message) {
        System.out.println("[WARN] " + TAG + ": " + message);
    }
}
