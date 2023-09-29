package com.iptv.tvinputs.m3u;

import android.media.tv.TvContract;

import androidx.annotation.NonNull;

import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import com.iptv.tvinputs.util.Log;
import com.iptv.tvinputs.util.Utils;
import com.iptv.tvinputs.util.Utils.Tuple;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

public class EPGImpl {
    private static class MyProgram {
        @NonNull
        @Override
        public String toString() {
            return "[MyProgram@" + this.hashCode() + "]:{channel=" + channel + ", start=" + str_start +
                    "(" + start + "), stop=" + str_stop + "(" + stop + "), title=" + title + ", desc=" + desc + "}";
        }
        String channel; String str_start; String str_stop; String title; String desc; String icon;
        Long start; Long stop;
    }

    private static class MyChannel {
        @NonNull
        @Override
        public String toString() {
            return "[MyChannel@" + this.hashCode() + "]:{M3UItem: " + mM3UItem + ", Programs: " + mProgramMap + "}";
        }
        MyChannel() {
            mProgramMap = new TreeMap<>();
        }
        M3UItem mM3UItem = null; String tvGenre = "Others";
        SortedMap<Long, MyProgram> mProgramMap;
    }

    /**          Map<TvGenre, Tuple<EpgGenre,    Map<Ch Name, MyChannel>, Ch No Start>> */
    private final Map<String, Tuple<String, SortedMap<String, MyChannel>, Integer>> mGenres = new LinkedHashMap<>();

    /**           Map<ch id,  MyChannel> */
    private final Map<String, MyChannel> mChannelMap = new HashMap<>();
    /*Map for channelid, genre*/

    private static EPGImpl instance = null;
    public static EPGImpl getInstance() {
        if (null == instance)
            instance = new EPGImpl();
        return instance;
    }

    private EPGImpl() {
        Log.i("swidebug", ". EPGImpl EPGImpl()");
        mGenres.put(TvContract.Programs.Genres.MOVIES, new Tuple<>("movie", new TreeMap<>(), 1));
        mGenres.put(TvContract.Programs.Genres.SPORTS, new Tuple<>("sport", new TreeMap<>(), 101));
        mGenres.put(TvContract.Programs.Genres.ENTERTAINMENT, new Tuple<>("entertainment", new TreeMap<>(), 201));
        mGenres.put(TvContract.Programs.Genres.PREMIER, new Tuple<>("punjab", new TreeMap<>(),301));
        mGenres.put(TvContract.Programs.Genres.DRAMA, new Tuple<>("hindi", new TreeMap<>(), 401));
        mGenres.put(TvContract.Programs.Genres.NEWS, new Tuple<>("news", new TreeMap<>(), 501));
        mGenres.put(TvContract.Programs.Genres.MUSIC, new Tuple<>("music", new TreeMap<>(), 651));
        mGenres.put(TvContract.Programs.Genres.TECH_SCIENCE, new Tuple<>("knowledge", new TreeMap<>(), 701));
        mGenres.put(TvContract.Programs.Genres.FAMILY_KIDS, new Tuple<>("kid", new TreeMap<>(), 801));
        mGenres.put(TvContract.Programs.Genres.LIFE_STYLE, new Tuple<>("lifestyle", new TreeMap<>(), 901));
        mGenres.put(TvContract.Programs.Genres.EDUCATION, new Tuple<>("spiritual", new TreeMap<>(), 951));
        mGenres.put("Others", new Tuple<>("Others", new TreeMap<>(), 1001));
    }

    private String trim(String str) {
        return str == null ? null : str.trim();
    }

    private long stringToUtcMillis(String s) {
        s = s.split(" ")[0] + " GMT";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss z");
        LocalDateTime dateTime = LocalDateTime.parse(s, formatter);
        return dateTime.atZone(ZoneId.of("GMT")).toInstant().toEpochMilli();
    }

    public boolean parseEPG(String url) {
        boolean ret = true;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            //con.setRequestProperty("Accept-Encoding", "gzip");
            int responseCode = con.getResponseCode();
            Log.i("swidebug", ". EPGImpl httpget() responseCode: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                XmlPullParserFactory xmlFactoryObject = XmlPullParserFactory.newInstance();
                XmlPullParser xmlParser = xmlFactoryObject.newPullParser();
                Log.i("swidebug", ". EPGImpl httpget() Length : " + con.getContentLength());
                try (InputStream inputStream = con.getInputStream();
                     GZIPInputStream gis = new GZIPInputStream(inputStream)) {
                    xmlParser.setInput(gis, null);
                    int event = xmlParser.getEventType();
                    MyProgram program = null;
                    while (event != XmlPullParser.END_DOCUMENT)  {
                        try {
                            //Log.v("swidebug", ". EPGImpl httpget() event : " + event);
                            String name = xmlParser.getName();
                            switch (event) {
                                case XmlPullParser.START_TAG:
                                    //Log.v("swidebug", "Start Tag: " + name);
                                    switch (name) {
                                        case "programme":
                                            program = new MyProgram();
                                            String attr_channel = xmlParser.getAttributeValue(null, "channel");
                                            program.channel = trim(attr_channel);
                                            String attr_start = xmlParser.getAttributeValue(null, "start");
                                            program.str_start = trim(attr_start);
                                            program.start = stringToUtcMillis(program.str_start);
                                            String attr_stop = xmlParser.getAttributeValue(null, "stop");
                                            program.str_stop = trim(attr_stop);
                                            program.stop = stringToUtcMillis(program.str_stop);
                                            //Log.v("swidebug", "Start Tag attr_channel: " + attr_channel + " " + attr_start + " to " + attr_stop);
                                            break;
                                        case "title":
                                            xmlParser.next();
                                            String text_title = xmlParser.getText();
                                            if (null != program)
                                                program.title = trim(text_title);
                                            //Log.v("swidebug", "Start Tag text_title: " + text_title);
                                            break;
                                        case "desc":
                                            xmlParser.next();
                                            String text_desc = xmlParser.getText();
                                            if (null != program)
                                                program.desc = trim(text_desc);
                                            //Log.v("swidebug", "Start Tag text_desc: " + text_desc);
                                            break;
                                        case "icon":
                                            String attr_icon = xmlParser.getAttributeValue(null, "src");
                                            if (null != program)
                                                program.icon = trim(attr_icon);
                                            //Log.v("swidebug", "Start Tag text_desc: " + text_desc);
                                            break;
                                    }
                                    break;
                                case XmlPullParser.END_TAG:
                                    //Log.v("swidebug", "End Tag: " + name);
                                    if (name.equals("programme") && (null != program)) {
                                        MyChannel ch = mChannelMap.get(program.channel);
                                        if (null == ch) {
                                            ch = new MyChannel();
                                        }
                                        ch.mProgramMap.put(program.start, program);
                                        mChannelMap.put(program.channel, ch);
                                        //Log.d("swidebug", "EPGImpl httpget() add program: " + program);
                                    }
                                    break;
                            }
                        } catch (Exception e1) {
                            ret = false;
                            Log.e("swidebug", ". EPGImpl httpget() got exception 1: " + e1.getMessage());
                        }
                        event = xmlParser.next();
                    }
                }
            }
            con.disconnect();
        } catch (Exception e2) {
            ret = false;
            Log.e("swidebug", ". EPGImpl httpget() got exception 2: " + e2.getMessage());
        }
        return ret;
    }

    private int a2i(String a) {
        StringBuilder aa = new StringBuilder();
        for (int i=0; i < a.length(); i++) {
            char ch = a.charAt(i);
            if (ch >= '0' && ch <= '9') {
                aa.append(ch);
            }
        }
        if (aa.length() == 0) {
            return 0;
        }
        return Integer.parseInt(aa.toString());
    }
    public boolean updateChannel(M3UItem item) {
        //Log.v("swidebug", "> EPGImpl updateChannel() M3UItem: " + item);
        boolean ret = true;
        try {
            MyChannel ch = mChannelMap.get(item.getChannelID());
            if (ch == null) {
                ch = new MyChannel();
            }
            ch.mM3UItem = item;
            for (String k : mGenres.keySet()) {
                if (item.getGroupTitle().toLowerCase().contains(mGenres.get(k).first)) {
                    ch.tvGenre = k;
                }
                if (ch.tvGenre.equals("Others")) {
                    if (item.getChannelName().toLowerCase().contains(mGenres.get(k).first)) {
                        ch.tvGenre = k;
                    }
                }
            }
            mChannelMap.put(item.getChannelID(), ch);
        } catch (Exception ex) {
            ret = false;
            Log.e("swidebug", ". EPGImpl updateChannel() got exception: " + ex.getMessage());
        }
        return ret;
    }

    private void refreshEPG(int syncType) {
        try {
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    M3UParser.getInstance().parse(Utils.getPlaylistUrl(), syncType);
                }
            });
            th.start();
            th.join();
        } catch (Exception ex) {
            Log.e("swidebug", ". EPGImpl refreshEPG() exception: " + ex.getMessage());
        }
    }

    private void updateGenreLists() {
        for(String k : mGenres.keySet()) {
            mGenres.get(k).second.clear();
        }
        for(String k : mChannelMap.keySet()) {
            MyChannel ch = mChannelMap.get(k);
            if (ch.mM3UItem == null) {
                Log.e("swidebug", ". EPGImpl updateGenreLists() Skipping Channel as not present in manifest: " + ch);
                continue;
            }
            String channelName = ch.mM3UItem.getChannelName().toLowerCase() + ch.mM3UItem.getChannelID();
            MyChannel c = mGenres.get(ch.tvGenre).second.get(channelName);
            if (c != null) {
                Log.e("swidebug", ". EPGImpl updateGenreLists() Channel already in map: " + c);
                Log.e("swidebug", ". EPGImpl updateGenreLists() Channel skipped: " + ch);
            } else {
                mGenres.get(ch.tvGenre).second.put(channelName, ch);
            }
        }
    }

    public List<Channel> getChannels() {
        refreshEPG(M3UParser.PARSE_MANIFEST);
        updateGenreLists();
        List<Channel> list = new ArrayList<>();
        for (String k : mGenres.keySet()) {
            int chno = mGenres.get(k).third;
            for (MyChannel ch : mGenres.get(k).second.values()) {
                if (null == ch.mM3UItem) {
                    Log.e("swidebug", ". EPGImpl getChannels() Skipping Channel as not present in manifest: " + ch);
                    continue;
                }
                try {
                    InternalProviderData internalProviderData = new InternalProviderData();
                    internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_MPEG_DASH);
                    internalProviderData.setVideoUrl(ch.mM3UItem.getChannelID());
                    Channel c = new Channel.Builder()
                            .setDisplayName(ch.mM3UItem.getChannelName())
                            .setDisplayNumber("" + chno)
                            .setChannelLogo(ch.mM3UItem.getLogoURL())
                            .setOriginalNetworkId(a2i(ch.mM3UItem.getChannelID()))
                            .setInternalProviderData(internalProviderData)
                            .build();
                    list.add(c);
                    chno++;
                } catch (Exception ex) {
                    Log.e("swidebug", ". EPGImpl getChannels() got exception: " + ex.getMessage());
                    Log.e("swidebug", ". EPGImpl getChannels() got exception in ch: " + ch);
                    ex.printStackTrace();
                }
            }
        }
        return list;
    }

    private Program makeProgramFromMyProgram(MyChannel ch, MyProgram p) {
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_MPEG_DASH);
        internalProviderData.setVideoUrl(ch.mM3UItem.getChannelID());
        return new Program.Builder()
                .setTitle(p.title)
                .setStartTimeUtcMillis(p.start)
                .setEndTimeUtcMillis(p.stop)
                .setDescription(p.desc)
                .setCanonicalGenres(new String[] {ch.tvGenre})
                .setPosterArtUri(p.icon)
                .setThumbnailUri(p.icon)
                .setInternalProviderData(internalProviderData)
                .build();
    }

    public List<Program> getPrograms(Channel channel, long startMs, long endMs) {
        refreshEPG(M3UParser.PARSE_FULL);
        List<Program> list = new ArrayList<>();
        MyChannel ch = mChannelMap.get(channel.getInternalProviderData().getVideoUrl());
        if (null != ch) {
            MyProgram prevP = null;
            for (Long k : ch.mProgramMap.keySet()) {
                MyProgram p = ch.mProgramMap.get(k);
                if (p.start < startMs) {
                    prevP = p;
                    continue;
                }
                if (prevP != null && prevP.stop >= startMs) {
                    list.add(makeProgramFromMyProgram(ch, prevP));
                    prevP = null;
                }
                /*if (p.start > endMs) {
                    break;
                }*/

                list.add(makeProgramFromMyProgram(ch, p));
            }
            if (list.size() == 0) {
                MyProgram p = new MyProgram();
                p.start = System.currentTimeMillis(); p.stop = p.start + 60 * 60 * 1000;
                p.title = ch.mM3UItem.getChannelName();
                list.add(makeProgramFromMyProgram(ch, p));
            }
        }
        return list;
    }

    public String getChannelUrl(String channelid) {
        Log.i("swidebug", "> EPGImpl getChannelUrl(): " + channelid);
        refreshEPG(M3UParser.PARSE_MANIFEST);
        MyChannel ch = mChannelMap.get(channelid);
        String u = ch == null ? null :
                ch.mM3UItem == null ? null : ch.mM3UItem.getStreamURL();
        Log.i("swidebug", "< EPGImpl getChannelUrl(): " + u);
        return u;
    }

    public String getChannelLicenseUrl(String channelid) {
        refreshEPG(M3UParser.PARSE_MANIFEST);
        MyChannel ch = mChannelMap.get(channelid);
        String u = ch == null ? null :
                ch.mM3UItem == null ? null : ch.mM3UItem.getLicenseKeyUrl();
        Log.i("swidebug", ". EPGImpl getChannelLicUrl(): " + u);
        return u;
    }
}
