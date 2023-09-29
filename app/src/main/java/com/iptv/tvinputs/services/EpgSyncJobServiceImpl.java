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
package com.iptv.tvinputs.services;

import android.net.Uri;
import android.util.Log;

import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.iptv.tvinputs.m3u.EPGImpl;
import com.iptv.tvinputs.m3u.M3UParser;
import com.iptv.tvinputs.util.Utils;

import java.util.List;

/**
 * EpgSyncJobService that periodically runs to update channels and programs.
 */
public class EpgSyncJobServiceImpl extends EpgSyncJobService {
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
}
