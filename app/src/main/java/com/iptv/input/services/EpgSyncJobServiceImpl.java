/*
 * Copyright 2016 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.iptv.input.services;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;

import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.iptv.input.m3u.EPGImpl;
import com.iptv.input.m3u.M3UParser;
import com.iptv.input.util.Log;
import com.iptv.input.util.Utils;

import java.util.List;

/**
 * EpgSyncJobService that periodically runs to update channels and programs.
 */
public class EpgSyncJobServiceImpl extends EpgSyncJobService {
    public static final long DEFAULT_SYNC_PERIOD_MILLIS = 1000 * 60 * 60 * 12; // 12 hour
    public static final long DEFAULT_PERIODIC_EPG_DURATION_MILLIS = 1000 * 60 * 60 * 48; // 48 Hour

    @Override
    public List<Channel> getChannels() {
        Log.i("swidebug", "> EpgSyncJobServiceImpl getChannels()");
        M3UParser.getInstance().parse(Utils.getPlaylistUrl(), M3UParser.PARSE_FULL);
        Log.i("swidebug", ". EpgSyncJobServiceImpl getChannels() EPG parsed");
        List<Channel> channelList = EPGImpl.getInstance().getChannels();
        Log.i("swidebug", "< EpgSyncJobServiceImpl getChannels() count: " + channelList.size());
        //Log.v("swidebug", "< EpgSyncJobServiceImpl getChannels() " + channelList);
        return channelList;
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel,
            long startMs, long endMs) {
        Log.i("swidebug", "> EpgSyncJobServiceImpl getProgramsForChannel() channelUri: " +
                channelUri + ", channel: " + channel +", time: " + startMs + " - " + endMs);
        List<Program> list = EPGImpl.getInstance().getPrograms(channel, startMs, endMs);
        Log.i("swidebug", "< EpgSyncJobServiceImpl getProgramsForChannel() count: " + list.size());
        //Log.v("swidebug", "< EpgSyncJobServiceImpl getProgramsForChannel() list: " + list);
        return list;
    }

    public static void requestImmediateSync(final Context context) {
        Log.i("swidebug", "> EpgSyncJobServiceImpl requestImmediateSync()");
        String inputId = context.getSharedPreferences(EpgSyncJobService.PREFERENCE_EPG_SYNC,
                Context.MODE_PRIVATE).getString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, null);
        Log.i("swidebug", ". EpgSyncJobServiceImpl requestImmediateSync() inputId: " + inputId);
        if (inputId != null) {
            Thread th = new Thread(()-> {
                EpgSyncJobService.requestImmediateSync(context, inputId,
                        EpgSyncJobServiceImpl.DEFAULT_PERIODIC_EPG_DURATION_MILLIS,
                        new ComponentName(context, EpgSyncJobServiceImpl.class));
                Log.i("swidebug", ". EpgSyncJobServiceImpl requestImmediateSync() requested");
            });
            th.start();
        }
        Log.i("swidebug", "< EpgSyncJobServiceImpl requestImmediateSync()");
    }
}
