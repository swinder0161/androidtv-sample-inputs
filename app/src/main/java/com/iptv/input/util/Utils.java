package com.iptv.input.util;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

public class Utils {
    public static class Tuple<F, S, T> {
        public F first;
        public S second;
        public T third;

        public Tuple(F ff, S ss, T tt) {
            first = ff;
            second = ss;
            third = tt;
        }
    }

    private static final String pref_name = "utils";
    private static final String pref_url = "url";

    private static Application sApplication;
    private static String sPlaylistUrl = "";

    public static void setApplication(Application app) {
        sApplication = app;
    }

    public static Context getApplicationContext() {
        return sApplication.getApplicationContext();
    }

    public static void setPlaylistUrl(String playlistUrl) {
        sPlaylistUrl = playlistUrl;
        SharedPreferences prefs  = getApplicationContext().getSharedPreferences(pref_name, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(pref_url, playlistUrl).apply();
    }

    public static String getPlaylistUrl() {
        SharedPreferences prefs = getApplicationContext().getSharedPreferences(pref_name, Context.MODE_PRIVATE);
        sPlaylistUrl = prefs.getString(pref_url, sPlaylistUrl);
        return sPlaylistUrl;
    }
}
