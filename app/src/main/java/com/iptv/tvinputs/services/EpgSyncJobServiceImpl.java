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

import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * EpgSyncJobService that periodically runs to update channels and programs.
 */
public class EpgSyncJobServiceImpl extends EpgSyncJobService {
    @Override
    public List<Channel> getChannels() {
        Log.i("swidebug", "> EpgSyncJobServiceImpl getChannels()");
        List<Channel> channelList = new ArrayList<>();
        // Add a channel programmatically
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setRepeatable(true);
        Channel channelTears = new Channel.Builder()
                .setDisplayName("MPEG_DASH")
                .setDisplayNumber("1")
                .setChannelLogo("https://storage.googleapis.com/android-tv/images/mpeg_dash.png")
                .setOriginalNetworkId(101)
                .setInternalProviderData(internalProviderData)
                .build();
        channelList.add(channelTears);
        Log.i("swidebug", "< EpgSyncJobServiceImpl getChannels()");
        return channelList;
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel,
            long startMs, long endMs) {
        Log.i("swidebug", "> EpgSyncJobServiceImpl getProgramsForChannel() channelUri: " +
                channelUri + ", channel: " + channel + ", time: " + startMs + " - " + endMs);
        // Programatically add channel
        List<Program> programsTears = new ArrayList<>();
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_MPEG_DASH);
        internalProviderData.setVideoUrl("ts222");
        Date utcDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTime();
        long timeMillis = utcDate.getTime() - 10000;
        programsTears.add(new Program.Builder()
                .setTitle("11331")
                .setStartTimeUtcMillis(timeMillis)
                .setEndTimeUtcMillis(timeMillis + 3*60*1000)
                .setDescription("check for mpeg dash")
                .setCanonicalGenres(new String[] {TvContract.Programs.Genres.TECH_SCIENCE,
                        TvContract.Programs.Genres.MOVIES})
                .setPosterArtUri("https://storage.googleapis.com/gtv-videos-bucket/sample/images/tears.jpg")
                .setThumbnailUri("https://storage.googleapis.com/gtv-videos-bucket/sample/images/tears.jpg")
                .setInternalProviderData(internalProviderData)
                .build());
        Log.i("swidebug", "< EpgSyncJobServiceImpl getProgramsForChannel() program");
        return programsTears;
    }
}
