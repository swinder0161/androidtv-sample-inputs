package com.iptv.input.m3u;

import com.iptv.input.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class M3UParser {
    public interface M3UHandler {
        /**
         * When M3UParser get a M3UHead, this method will be called.
         *
         * @param header
         *            the instance of M3UHead.
         */
        boolean onReadEXTM3U(M3UHead header);

        /**
         * When M3UParser get a M3UItem, this method will be called.
         *
         * @param item
         *            the instance of M3UItem.
         */
        boolean onReadEXTINF(M3UItem item);
    }

    public static final int PARSE_MANIFEST = 0;
    public static final int PARSE_FULL = 1;

    private static final String PREFIX_EXTM3U = "#EXTM3U";
    private static final String PREFIX_EXTINF = "#EXTINF:";
    private static final String PREFIX_KODIPROP = "#KODIPROP:";
    private static final String PREFIX_COMMENT = "#";
    private static final String EMPTY_STRING = "";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_TYPE = "type";
    private static final String ATTR_DLNA_EXTRAS = "dlna_extras";
    private static final String ATTR_PLUGIN = "plugin";
    private static final String ATTR_TVG_URL = "x-tvg-url";
    private static final String ATTR_CHANNEL_NAME = "channel_name";
    private static final String ATTR_DURATION = "duration";
    private static final String ATTR_LOGO = "logo";
    private static final String ATTR_ID = "id";
    private static final String ATTR_GROUP_TITLE = "group-title";
    private static final String ATTR_TVG_PREFIX = "tvg-";
    private static final String ATTR_TVG_SUFFIX = "-tvg";
    private static final String INVALID_STREAM_URL = "http://0.0.0.0:1234";

    private static M3UParser mInstance = null;
    private M3UHandler mHandler = null;
    private M3UItem mTempItem = null;

    private long mLastSyncTime = 0;
    private long mLastSyncTimeManifest = 0;

    private final Object mutex = new Object();

    private M3UParser() {
    }

    public static M3UParser getInstance() {
        if (mInstance == null) {
            mInstance = new M3UParser();
        }
        return mInstance;
    }

    /**
     * Setup a default handler to handle the m3u file parse result.
     *
     * @param handler
     *            a M3UHandler instance.
     */
    public void setHandler(M3UHandler handler) {
        mHandler = handler;
    }

    /**
     * Use the default handler to parse a m3u file.
     *
     * @param url
     *            a file to be parsed.
     */
    public boolean parse(String url, int syncType) {
        if (mHandler == null) {
            mHandler = new M3UHandler() {
                @Override
                public boolean onReadEXTM3U(M3UHead header) {
                    Log.i("swidebug", "M3UParser onReadEXTM3U() M3UHead: " + header);
                    return EPGImpl.getInstance().parseEPG(header.getTVGUrl());
                }

                @Override
                public boolean onReadEXTINF(M3UItem item) {
                    //Log.i("swidebug", "M3UParser onReadEXTINF() M3UItem: " + item);
                    return EPGImpl.getInstance().updateChannel(item);
                }
            };
        }
        boolean ret;
        synchronized (mutex) {
            ret = parse(url, mHandler, syncType);
        }
        return ret;
    }

    /**
     * Use a specific handler to parse a m3u file.
     *
     * @param url
     *            a file to be parsed.
     * @param handler
     *            a specific handler which will not change the default handler.
     */
    public boolean parse(String url, M3UHandler handler, int syncType) {
        if (handler == null) { // No need do anything, if no handler.
            return false;
        }
        long timeNow = System.currentTimeMillis()/1000;
        //wait 1 hour for resync
        if (((syncType == PARSE_FULL) && (timeNow - mLastSyncTime < 6*60*60)) || // 6 hours
                ((syncType == PARSE_MANIFEST) && (timeNow - mLastSyncTimeManifest < 2*60*60))) { // 2 hours
            Log.i("swidebug", ". M3UParser parse() too early to sync type: " + syncType);
            return false;
        }
        Log.i("swidebug", ". M3UParser parse() sync type: " + syncType);
        boolean success = true;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        con.getInputStream()));
                String tmp;
                while ((tmp = trim(br.readLine())) != null) {
                    //Log.i("swidebug", "line: " + tmp);
                    try {
                        if (tmp.startsWith(PREFIX_EXTM3U) && (PARSE_FULL == syncType)) {
                            success &= handler.onReadEXTM3U(parseHead(trim(tmp.replaceFirst(
                                    PREFIX_EXTM3U, EMPTY_STRING))));
                        } else if (tmp.startsWith(PREFIX_KODIPROP)) {
                            mTempItem = parseKodiProp(trim(tmp.replaceFirst(
                                    PREFIX_KODIPROP, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_EXTINF)) {
                            // The old item must be committed when we meet a new item.
                            //flush(handler);
                            mTempItem = parseItem(trim(tmp.replaceFirst(
                                    PREFIX_EXTINF, EMPTY_STRING)));
                        } else if (tmp.startsWith(PREFIX_COMMENT)) {
                            // Do nothing.
                        } else if (tmp.equals(EMPTY_STRING)) {
                            // Do nothing.
                        } else { // The single line is treated as the stream URL.
                            //Log.i("swidebug", "updateURL: " + tmp);
                            updateURL(tmp);
                            success &= flush(handler);
                        }
                    } catch (Exception ex) {
                        success = false;
                        Log.e("swidebug", ". M3UParser parse() exception: " + ex.getMessage());
                    }
                }
                success = flush(handler);
                br.close();
            }
            if (success) {
                mLastSyncTimeManifest = System.currentTimeMillis() / 1000;
                if (syncType == PARSE_FULL) mLastSyncTime = mLastSyncTimeManifest;
            }
        } catch (FileNotFoundException e) {
            Log.e("swidebug", ". M3UParser parse() file not found exception: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("swidebug", ". M3UParser parse() io exception: " + e.getMessage());
            e.printStackTrace();
        }
        return success;
    }

    private String trim(String str) {
        return str == null ? null : str.trim();
    }

    private boolean flush(M3UHandler handler) {
        boolean success = true;
        if (mTempItem != null) {
            // The invalid item must be skipped.
            if (mTempItem.getStreamURL() != null) {
                success = handler.onReadEXTINF(mTempItem);
            }
            mTempItem = null;
        }
        return success;
    }

    private void updateURL(String url) {
        //Log.i("swidebug", "updateURL mTempItem: " + mTempItem);
        if (mTempItem != null && !INVALID_STREAM_URL.equals(url)) {
            //Log.i("swidebug", "updateURL url: " + url);
            mTempItem.setStreamURL(url);
        }
    }

    private void putAttr(Map<String, String> map, String key, String value) {
        map.put(key, value);
    }

    private String getAttr(Map<String, String> map, String key) {
        String value = map.get(key);
        if (value == null) {
            value = map.get(ATTR_TVG_PREFIX + key);
            if (value == null) {
                value = map.get(key + ATTR_TVG_SUFFIX);
            }
        }
        return value;
    }

    private M3UHead parseHead(String words) {
        Map<String, String> attr = parseAttributes(words);
        M3UHead header = new M3UHead();
        header.setName(getAttr(attr, ATTR_NAME));
        header.setType(getAttr(attr, ATTR_TYPE));
        header.setDLNAExtras(getAttr(attr, ATTR_DLNA_EXTRAS));
        header.setPlugin(getAttr(attr, ATTR_PLUGIN));
        header.setTVGUrl(getAttr(attr, ATTR_TVG_URL));
        return header;
    }

    private M3UItem parseItem(String words) {
        Map<String, String> attr = parseAttributes(words);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        item.setChannelName(getAttr(attr, ATTR_CHANNEL_NAME));
        item.setDuration(convert2int(getAttr(attr, ATTR_DURATION)));
        item.setLogoURL(getAttr(attr, ATTR_LOGO));
        item.setChannelID(getAttr(attr, ATTR_ID));
        item.setGroupTitle(getAttr(attr, ATTR_GROUP_TITLE));
        item.setType(getAttr(attr, ATTR_TYPE));
        item.setDLNAExtras(getAttr(attr, ATTR_DLNA_EXTRAS));
        item.setPlugin(getAttr(attr, ATTR_PLUGIN));
        return item;
    }

    private M3UItem parseKodiProp(String words) {
        //Log.i("swidebug", "parseKodiProp words: " + words);
        Map<String, String> attr = parseAttributes(words);
        //Log.i("swidebug", "parseKodiProp attr: " + attr);
        M3UItem item;
        if(mTempItem == null) {
            item = new M3UItem();
        } else {
            item = mTempItem;
        }
        item.setLicenseType(getAttr(attr, "inputstream.adaptive.license_type"));
        item.setLicenseKeyUrl(getAttr(attr, "inputstream.adaptive.license_key"));
        return item;
    }

    private Map<String, String> parseAttributes(String words) {
        Map<String, String> attr = new HashMap<>();
        if (words == null || words.equals(EMPTY_STRING)) {
            return attr;
        }
        Status status = Status.READY;
        String tmp = words;
        StringBuffer connector = new StringBuffer();
        int i = 0;
        char c = tmp.charAt(i);
        if (c == '-' || Character.isDigit(c)) {
            connector.append(c);
            while (++i < tmp.length()) {
                c = tmp.charAt(i);
                if (Character.isDigit(c)) {
                    connector.append(c);
                } else {
                    break;
                }
            }
            putAttr(attr, ATTR_DURATION, connector.toString());
            tmp = trim(tmp.replaceFirst(connector.toString(), EMPTY_STRING));
            reset(connector);
            i = 0;
        }
        String key = EMPTY_STRING;
        boolean startWithQuota = false;
        while (i < tmp.length()) {
            c = tmp.charAt(i++);
            switch (status) {
                case READY:
                    if (Character.isWhitespace(c)) {
                        // Do nothing
                    } else if (c == ',') {
                        putAttr(attr, ATTR_CHANNEL_NAME, tmp.substring(i));
                        i = tmp.length();
                    } else {
                        connector.append(c);
                        status = Status.READING_KEY;
                    }
                    break;
                case READING_KEY:
                    if (c == '=') {
                        key = trim(key + connector);
                        reset(connector);
                        status = Status.KEY_READY;
                    } else {
                        connector.append(c);
                    }
                    break;
                case KEY_READY:
                    if (!Character.isWhitespace(c)) {
                        if (c == '"') {
                            startWithQuota = true;
                        } else {
                            connector.append(c);
                        }
                        status = Status.READING_VALUE;
                    }
                    break;
                case READING_VALUE:
                    if (startWithQuota) {
                        connector.append(c);
                        int end = tmp.indexOf("\"", i);
                        end = end == -1 ? tmp.length() : end;
                        connector.append(tmp.substring(i, end));
                        startWithQuota = false;
                        putAttr(attr, key, connector.toString());
                        i = end + 1;
                        reset(connector);
                        key = EMPTY_STRING;
                        status = Status.READY;
                        break;
                    }
                    if (Character.isWhitespace(c)) {
                        if (connector.length() > 0) {
                            putAttr(attr, key, connector.toString());
                            reset(connector);
                        }
                        key = EMPTY_STRING;
                        status = Status.READY;
                    } else {
                        connector.append(c);
                    }
                    break;
                default:
                    break;
            }
        }
        if (!key.equals(EMPTY_STRING) && connector.length() > 0) {
            putAttr(attr, key, connector.toString());
            reset(connector);
        }
        return attr;
    }

    private int convert2int(String value) {
        int ret;
        try {
            ret = Integer.parseInt(value);
        } catch (Exception e) {
            ret = -1;
        }
        return ret;
    }

    private void reset(StringBuffer buffer) {
        buffer.delete(0, buffer.length());
    }

    private enum Status {
        READY, READING_KEY, KEY_READY, READING_VALUE,
    }
}
