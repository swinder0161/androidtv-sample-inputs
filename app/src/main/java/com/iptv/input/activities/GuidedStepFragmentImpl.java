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
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.leanback.app.GuidedStepFragment;
import androidx.leanback.widget.GuidanceStylist.Guidance;
import androidx.leanback.widget.GuidedAction;

import com.iptv.input.R;
import com.iptv.input.util.Log;

import java.util.List;

/** Introduction step in the input setup flow. */
public class GuidedStepFragmentImpl extends GuidedStepFragment {

    @Override
    @NonNull
    public Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onCreateGuidance()");
        String title = getString(R.string.tv_input_service_label);

        String description = getString(R.string.setup_first_step_description);
        Drawable icon = getActivity().getDrawable(R.drawable.android_48dp);
        Guidance g = new Guidance(title, description, null, icon);
        Log.i("swidebug", "< GuidedStepFragmentImpl onCreateGuidance()");
        return g;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onCreateActions()");
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_NEXT)
                        .title(R.string.setup_add_channel)
                        .hasNext(true)
                        .build());
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_CANCEL)
                        .title(R.string.setup_cancel)
                        .build());
        //TODO add about screen
        Log.i("swidebug", "< GuidedStepFragmentImpl onCreateActions()");
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        Log.i("swidebug", "> GuidedStepFragmentImpl onGuidedActionClicked()");
        if (action.getId() == GuidedAction.ACTION_ID_NEXT) {
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
