package com.iptv.input.util;

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

    private static String sPlaylistUrl = "";

    public static void setPlaylistUrl(String playlistUrl) {
        sPlaylistUrl = playlistUrl;
    }

    public static String getPlaylistUrl() {
        return sPlaylistUrl;
    }
}
