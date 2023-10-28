/*
 * Copyright 2017 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.iptv.input.activities;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist.Guidance;
import androidx.leanback.widget.GuidedAction;

import com.iptv.input.R;
import com.iptv.input.util.Log;
import com.iptv.input.util.Utils;

import java.util.List;

/** Introduction step in the input setup flow. */
public class GuidedStepFragmentImpl extends GuidedStepFragment {
    private boolean mRefresh = false;

    @Override
    @NonNull
    public Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onCreateGuidance()");
        String title = getString(R.string.tv_input_service_label);

        String description = Utils.getPlaylistUrl();
        if(description.length() < 7) {
            description = "Playlist Url not updated";
        }
        Drawable icon = getContext().getDrawable(R.drawable.android_48dp);
        Guidance g = new Guidance(title, description, null, icon);
        Log.i("swidebug", "< GuidedStepFragmentImpl onCreateGuidance()");
        return g;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i("swidebug", "> GuidedStepFragmentImpl onResume() mRefresh: " + mRefresh);
        if(mRefresh)
            getActivity().recreate();
        mRefresh = true;
        Log.i("swidebug", "< GuidedStepFragmentImpl onResume()");
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onCreateActions()");
        if(Utils.getPlaylistUrl().length() > 7) {
            actions.add(
                    new GuidedAction.Builder(getContext())
                            .id(GuidedAction.ACTION_ID_NEXT)
                            .title(R.string.setup_add_channel)
                            .hasNext(true)
                            .build());
        }
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_CONTINUE)
                        .title(R.string.setup_update_url)
                        .build());
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_CANCEL)
                        .title(R.string.setup_cancel)
                        .build());
        Log.i("swidebug", "< GuidedStepFragmentImpl onCreateActions()");
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onGuidedActionClicked()");
        if (action.getId() == GuidedAction.ACTION_ID_CONTINUE) {
            Log.i("swidebug", ". GuidedStepFragmentImpl onGuidedActionClicked() continue");
            Intent intent = new Intent(getContext(), MainActivity.class);
            getContext().startActivity(intent);
        } else if (action.getId() == GuidedAction.ACTION_ID_NEXT) {
            Log.i("swidebug", ". GuidedStepFragmentImpl onGuidedActionClicked() next");
            GuidedStepFragment.add(getFragmentManager(), new ChannelSetupStepFragmentImpl());
        } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
            Log.i("swidebug", ". GuidedStepFragmentImpl onGuidedActionClicked() cancel");
            getActivity().setResult(Activity.RESULT_CANCELED);
            getActivity().finishAfterTransition();
        }
        Log.i("swidebug", "< GuidedStepFragmentImpl onGuidedActionClicked()");
    }
}
