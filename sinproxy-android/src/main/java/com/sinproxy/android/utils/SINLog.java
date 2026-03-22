package com.sinproxy.android.utils;

import android.util.Log;

/**
 * Abstraction layer for logging to ensure compatibility with Android Logcat
 */
public class SINLog {
    private static final String TAG = "SINproxy";

    public static void i(String message) {
        Log.i(TAG, message);
        System.out.println("[INFO] " + message);
    }

    public static void e(String message, Throwable t) {
        Log.e(TAG, message, t);
        System.err.println("[ERROR] " + message);
        if (t != null) t.printStackTrace();
    }

    public static void d(String message) {
        Log.d(TAG, message);
        System.out.println("[DEBUG] " + message);
    }

    public static void w(String message) {
        Log.w(TAG, message);
        System.out.println("[WARN] " + message);
    }
}
