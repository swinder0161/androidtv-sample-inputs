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
package com.iptv.tvinputs.activity;

import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.widget.GuidanceStylist.Guidance;

import com.google.android.media.tv.companionlibrary.setup.ChannelSetupStepFragment;
import com.iptv.tvinputs.R;
import com.iptv.tvinputs.services.EpgSyncJobServiceImpl;
import com.iptv.tvinputs.util.Log;

/**
 * Fragment which shows a sample UI for registering channels and setting up EpgSyncJobServiceImpl to
 * provide program information in the background.
 */
public class SetupFragment extends ChannelSetupStepFragment<EpgSyncJobServiceImpl> {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("swidebug", "> SetupFragment onCreate()");
        String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        Log.i("swidebug", "< SetupFragment onCreate() inputId: " + inputId);
    }

    @Override
    public Class<EpgSyncJobServiceImpl> getEpgSyncJobServiceClass() {
        Log.i("swidebug", "> SetupFragment getEpgSyncJobServiceClass()");
        Log.i("swidebug", "< SetupFragment getEpgSyncJobServiceClass()");
        return EpgSyncJobServiceImpl.class;
    }

    @NonNull
    @Override
    public Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        Log.i("swidebug", "> SetupFragment onCreateGuidance()");
        String title = getString(R.string.tv_input_service_label);
        String description = getString(com.google.android.media.tv.companionlibrary.R.string.tif_channel_setup_description);
        Drawable icon = getActivity().getDrawable(R.drawable.android_48dp);
        Guidance g =  new Guidance(title, description, null, icon);
        Log.i("swidebug", "< SetupFragment onCreateGuidance()");
        return g;
    }

    @Override
    public long getFullSyncWindowSec() {
        return 1000 * 60 * 60 * 24 * 3; // 3 days
    }

    @Override
    public long getFullSyncFrequencyMillis() {
        return 1000 * 60 * 60 * 6; // 6 hrs
    }
}
