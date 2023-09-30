package com.iptv.input.player;

import android.util.Size;

import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
public class TracksImpl {
    private final ExoPlayer mPlayer;
    private final Map<Integer, List<Tracks.Group>> mGroups;
    private final Object mMutex;

    public TracksImpl(ExoPlayer player) {
        mPlayer = player;
        mGroups = new HashMap<>();
        mMutex = new Object();
    }

    private void processTracks(Tracks tracks) {
        for (int k : mGroups.keySet()) {
            mGroups.get(k).clear();
        }
        for (Tracks.Group g : tracks.getGroups()) {
            int type = g.getType();
            List<Tracks.Group> list = mGroups.get(type);
            if (null == list) {
                list = new ArrayList<>();
            }
            list.add(g);
            mGroups.put(type, list);
        }
    }

    public void onTracksChanged(Tracks tracks) {
        synchronized (mMutex) {
            processTracks(tracks);
        }
    }

    public int getTrackCount(int type) {
        int count = 0;
        synchronized (mMutex) {
            processTracks(mPlayer.getCurrentTracks());
            List<Tracks.Group> list = mGroups.get(type);
            if (null != list)
                count = list.size();
        }
        return count;
    }

    public TrackGroup getTrackGroup(int type, int index) {
        TrackGroup tg = null;
        synchronized (mMutex) {
            processTracks(mPlayer.getCurrentTracks());
            List<Tracks.Group> list = mGroups.get(type);
            if (null != list) {
                if (index < list.size()) {
                    Tracks.Group g = list.get(index);
                    if (null != g) {
                        tg = g.getMediaTrackGroup();
                    }
                }
            }
        }
        return tg;
    }

    public Format getTrackFormat(int type, int index) {
        Format fmt = null;
        synchronized (mMutex) {
            processTracks(mPlayer.getCurrentTracks());
            List<Tracks.Group> list = mGroups.get(type);
            if (null != list) {
                if (index < list.size()) {
                    fmt = list.get(index).getTrackFormat(0);
                }
            }
        }
        return fmt;
    }

    public int getSelectedTrack(int type) {
        int index = -1;
        synchronized (mMutex) {
            processTracks(mPlayer.getCurrentTracks());
            List<Tracks.Group> list = mGroups.get(type);
            if (null != list) {
                for (int i=0; i<list.size(); i++) {
                    if (list.get(i).isSelected()) {
                        index = i;
                        break;
                    }
                }
            }
        }
        return index;
    }

    public Size getMaxSize() {
        Size sz = new Size(Format.NO_VALUE, Format.NO_VALUE);
        synchronized (mMutex) {
            processTracks(mPlayer.getCurrentTracks());
            List<Tracks.Group> list = mGroups.get(ExoPlayerImpl.TRACK_TYPE_VIDEO);
            if (null != list) {
                Tracks.Group g = list.get(0);
                for (int i =0; i<g.length; i++) {
                    Format fmt = g.getTrackFormat(i);
                    if ((sz.getWidth() < fmt.width) || (sz.getHeight() < fmt.height)) {
                        sz = new Size(fmt.width, fmt.height);
                    }
                }
            }
        }
        return sz;
    }
}
