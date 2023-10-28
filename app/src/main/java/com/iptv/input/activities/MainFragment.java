/*
 * Copyright 2015 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.iptv.input.activities;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.iptv.input.R;
import com.iptv.input.m3u.EPGImpl;
import com.iptv.input.util.Log;
import com.iptv.input.util.Utils;

/**
 * Fragment that shows a web page for Sample TV Input introduction.
 */
public class MainFragment extends Fragment {
    private void updateUrlSaveButton(Button urlSaveButton, String url) {
        String savedUrl = Utils.getPlaylistUrl();
        if (0 == savedUrl.compareTo(url)) {
            urlSaveButton.setText(R.string.saved);
        } else {
            urlSaveButton.setText(R.string.save);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Log.i("swidebug", "> MainFragment onCreateView()");
        View v = inflater.inflate(R.layout.main_fragment, null);
        Log.i("swidebug", "< MainFragment onCreateView()");
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Log.i("swidebug", "> MainFragment onActivityCreated()");
        RelativeLayout layout = (RelativeLayout) getView();
        LinearLayout urlLayout = (LinearLayout) layout.findViewById(R.id.urlLayout);
        EditText editText = (EditText) urlLayout.findViewById(R.id.editText);
        Button urlSaveButton = (Button) urlLayout.findViewById(R.id.urlSaveButton);
        Button urlRestoreButton = (Button) urlLayout.findViewById(R.id.urlRestoreButton);
        LinearLayout cacheLayout = (LinearLayout) layout.findViewById(R.id.cacheLayout);
        Button clearCacheButton = (Button) cacheLayout.findViewById(R.id.clearCacheButton);

        editText.setText(Utils.getPlaylistUrl());
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateUrlSaveButton(urlSaveButton, "" + s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        urlSaveButton.setOnClickListener(view -> {
            String url = editText.getText().toString();
            Log.i("swidebug", ". MainFragment onActivityCreated() onClick() url: " + url);
            if (url.length() > 7) {
                Utils.setPlaylistUrl(url);
            }
            updateUrlSaveButton(urlSaveButton, url);
        });

        urlRestoreButton.setOnClickListener(view -> editText.setText(Utils.getPlaylistUrl()));

        clearCacheButton.setOnClickListener(view -> EPGImpl.getInstance().clearPref());
        Log.i("swidebug", "< MainFragment onActivityCreated()");
    }
}
