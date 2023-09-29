package com.iptv.tvinputs.util;

public class Log {
    public static void i(String tag, String msg) {
        android.util.Log.i(tag, msg);
    }
    public static void e(String tag, String msg) {
        android.util.Log.e(tag, msg);
    }
    public static void e(String tag, String msg, Throwable tr) {
        android.util.Log.e(tag, msg, tr);
    }
    public static void d(String tag, String msg) {
        android.util.Log.d(tag, msg);
    }
    public static void v(String tag, String msg) {
        android.util.Log.v(tag, msg);
    }
}
