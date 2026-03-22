package com.sinproxy.android.utils;

/**
 * Platform-agnostic logging for SINproxy Desktop with GUI listener support.
 */
public class SINLog {
    private static final String TAG = "SINproxy";

    public interface LogListener {
        void onLog(String message);
    }

    private static LogListener listener;

    public static void setListener(LogListener l) {
        listener = l;
    }

    public static void i(String message) {
        String log = "[INFO] " + TAG + ": " + message;
        System.out.println(log);
        if (listener != null) listener.onLog(log);
    }

    public static void e(String message, Throwable t) {
        String log = "[ERROR] " + TAG + ": " + message;
        System.err.println(log);
        if (t != null) {
            t.printStackTrace();
            if (listener != null) listener.onLog(log + " (See console for stacktrace)");
        } else {
            if (listener != null) listener.onLog(log);
        }
    }

    public static void d(String message) {
        String log = "[DEBUG] " + TAG + ": " + message;
        System.out.println(log);
        if (listener != null) listener.onLog(log);
    }

    public static void w(String message) {
        String log = "[WARN] " + TAG + ": " + message;
        System.out.println(log);
        if (listener != null) listener.onLog(log);
    }
}
