/*
 * Copyright 2015 The Android Open Source Project.
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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.media.tv.companionlibrary.BaseTvInputService;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.ModelUtils;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.model.RecordedProgram;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import com.iptv.tvinputs.R;
import com.iptv.tvinputs.player.ExoPlayerImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TvInputService which provides a full implementation of EPG, subtitles, multi-audio, parental
 * controls, and overlay view.
 */
public class TvInputServiceImpl extends BaseTvInputService {
    private static final String TAG = "TvInputServiceImpl";
    private static final boolean DEBUG = false;
    private static final long EPG_SYNC_DELAYED_PERIOD_MS = 1000 * 2; // 2 Seconds

    private CaptioningManager mCaptioningManager;

    private final Map<Integer, Integer> mTrackTypes = new HashMap<Integer, Integer>() {{
        put(TvTrackInfo.TYPE_AUDIO, ExoPlayerImpl.TRACK_TYPE_AUDIO);
        put(TvTrackInfo.TYPE_VIDEO, ExoPlayerImpl.TRACK_TYPE_VIDEO);
        put(TvTrackInfo.TYPE_SUBTITLE, ExoPlayerImpl.TRACK_TYPE_TEXT);
    }};

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("swidebug", "> TvInputServiceImpl onCreate()");
        mCaptioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        Log.i("swidebug", "< TvInputServiceImpl onCreate()");
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.i("swidebug", "> TvInputServiceImpl onCreateSession()");
        TvInputSessionImpl session = new TvInputSessionImpl(this, inputId);
        session.setOverlayViewEnabled(true);
        Session s = super.sessionCreated(session);
        Log.i("swidebug", "< TvInputServiceImpl onCreateSession()");
        return s;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Nullable
    @Override
    public TvInputService.RecordingSession onCreateRecordingSession(String inputId) {
        Log.i("swidebug", "> TvInputServiceImpl onCreateRecordingSession()");
        TvInputService.RecordingSession r = new RecordingSessionImpl(this, inputId);
        Log.i("swidebug", "> TvInputServiceImpl onCreateRecordingSession()");
        return r;
    }

    /**
     * Gets the track id of the track type and track index.
     *
     * @param trackType  the type of the track e.g. TvTrackInfo.TYPE_AUDIO
     * @param trackIndex the index of that track within the media. e.g. 0, 1, 2...
     * @return the track id for the type & index combination.
     */
    private static String getTrackId(int tvTrackType, int trackIndex) {
        Log.i("swidebug", "> TvInputServiceImpl getTrackId()");
        Log.i("swidebug", "< TvInputServiceImpl getTrackId()");
        return tvTrackType + "-" + trackIndex;
    }

    /**
     * Gets the index of the track for a given track id.
     *
     * @param trackId the track id.
     * @return the track index for the given id, as an integer.
     */
    private static int getIndexFromTrackId(String trackId) {
        Log.i("swidebug", "> TvInputServiceImpl getIndexFromTrackId()");
        Log.i("swidebug", "< TvInputServiceImpl getIndexFromTrackId()");
        return Integer.parseInt(trackId.split("-")[1]);
    }

    class TvInputSessionImpl extends Session implements
            ExoPlayerImpl.Listener, ExoPlayerImpl.CaptionListener {
        private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
        private static final int TEXT_UNIT_PIXELS = 0;
        private static final String UNKNOWN_LANGUAGE = "und";
        private final Context mContext;
        private final String mInputId;

        private int mSelectedSubtitleTrackIndex;
        private SubtitleView mSubtitleView;
        private ExoPlayerImpl mPlayer;
        private boolean mCaptionEnabled;

            TvInputSessionImpl(Context context, String inputId) {
            super(context, inputId);
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl TvInputSessionImpl() inputId: " + inputId);
            mCaptionEnabled = mCaptioningManager.isEnabled();
            mContext = context;
            mInputId = inputId;
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl TvInputSessionImpl()");
        }

        @Override
        public View onCreateOverlayView() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onCreateOverlayView()");
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            mSubtitleView = (SubtitleView) inflater.inflate(R.layout.subtitleview, null);

            // Configure the subtitle view.
            CaptionStyleCompat captionStyle;
            float captionTextSize = getCaptionFontSize();
            captionStyle = CaptionStyleCompat
                    .createFromCaptionStyle(mCaptioningManager.getUserStyle());
            captionTextSize *= mCaptioningManager.getFontScale();
            mSubtitleView.setStyle(captionStyle);
            mSubtitleView.setFixedTextSize(TEXT_UNIT_PIXELS, captionTextSize);
            mSubtitleView.setVisibility(View.VISIBLE);

            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onCreateOverlayView()");
            return mSubtitleView;
        }

        private List<TvTrackInfo> getAllTracks() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl getAllTracks()");
            String trackId;
            List<TvTrackInfo> tracks = new ArrayList<>();

            for (int tvTrackType : mTrackTypes.keySet()) {
                int playerTrackType = mTrackTypes.get(tvTrackType);
                int count = mPlayer.getTrackCount(playerTrackType);
                Log.v("swidebug", ". TvInputServiceImpl TvInputSessionImpl getAllTracks() type: " + tvTrackType + ", count: " + count);
                for (int i = 0; i < count; i++) {
                    Format format = mPlayer.getTrackFormat(playerTrackType, i);
                    Log.v("swidebug", ". TvInputServiceImpl TvInputSessionImpl getAllTracks() format: " + format);
                    trackId = getTrackId(playerTrackType, i);
                    TvTrackInfo.Builder builder = new TvTrackInfo.Builder(tvTrackType, trackId);

                    if (playerTrackType == ExoPlayerImpl.TRACK_TYPE_VIDEO) {
                        Log.v("swidebug", ". TvInputServiceImpl TvInputSessionImpl getAllTracks() if Video");
                        if (format.width != Format.NO_VALUE) {
                            builder.setVideoWidth(format.width);
                        }
                        if (format.height != Format.NO_VALUE) {
                            builder.setVideoHeight(format.height);
                        }
                    } else if (playerTrackType == ExoPlayerImpl.TRACK_TYPE_AUDIO) {
                        Log.v("swidebug", ". TvInputServiceImpl TvInputSessionImpl getAllTracks() if Audio ch: " + format.channelCount);
                        builder.setAudioChannelCount(format.channelCount);
                        builder.setAudioSampleRate(format.sampleRate);
                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
                            // TvInputInfo expects {@code null} for unknown language.
                            builder.setLanguage(format.language);
                        }
                    } else if (playerTrackType == ExoPlayerImpl.TRACK_TYPE_TEXT) {
                        Log.v("swidebug", ". TvInputServiceImpl TvInputSessionImpl getAllTracks() if Text");
                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
                            // TvInputInfo expects {@code null} for unknown language.
                            builder.setLanguage(format.language);
                        }
                    }

                    tracks.add(builder.build());
                }
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl getAllTracks()");
            return tracks;
        }

        @Override
        public boolean onPlayProgram(Program program, long startPosMs) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onPlayProgram() program" + program + ", startPosMs: " + startPosMs);
            Uri channelUri = getCurrentChannelUri();
            Channel channel =
                    ModelUtils.getChannel(
                            mContext.getContentResolver(), channelUri);
            Log.i("swidebug", ". TvInputServiceImpl TvInputSessionImpl onPlayProgram() channel" + channel);
            if (program == null) {
                requestEpgSync(channelUri);
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onPlayProgram() if");
                return false;
            }
            createPlayer(program.getInternalProviderData().getVideoType(),
                    program.getInternalProviderData().getVideoUrl());
            if (startPosMs > 0) {
                mPlayer.seekTo(startPosMs);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }
            mPlayer.setPlayWhenReady(true);
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onPlayProgram()");
            return true;
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        public boolean onPlayRecordedProgram(RecordedProgram recordedProgram) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onPlayRecordedProgram()");
            createPlayer(recordedProgram.getInternalProviderData().getVideoType(),
                    recordedProgram.getInternalProviderData().getVideoUrl());

            long recordingStartTime = recordedProgram.getInternalProviderData()
                    .getRecordedProgramStartTime();
            mPlayer.seekTo(recordingStartTime - recordedProgram.getStartTimeUtcMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }
            mPlayer.setPlayWhenReady(true);
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onPlayRecordedProgram()");
            return true;
        }

        public TvPlayer getTvPlayer() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl getTvPlayer()");
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl getTvPlayer() mPlayer: " + mPlayer);
            return mPlayer;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onTune() uri: " + channelUri);
            if (DEBUG) {
                Log.d(TAG, "Tune to " + channelUri.toString());
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            releasePlayer();
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onTune()");
            return super.onTune(channelUri);
        }

        @Override
        public void onPlayAdvertisement(Advertisement advertisement) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onPlayAdvertisement()");
            createPlayer(TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE,
                    advertisement.getRequestUrl());
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onPlayAdvertisement()");
        }

        private void createPlayer(int videoType, String videoId) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl createPlayer() videoType: " + videoType + ", videoId: " + videoId);
            //ToDo
            String videoUrl = "https://bitmovin-a.akamaihd.net/content/art-of-motion_drm/mpds/11331.mpd";
            //TvXmlCreator.getChannelUrl(videoUrl.toString());
            String licenseUrl = "https://proxy.uat.widevine.com/proxy?provider=widevine_test";
            //TvXmlCreator.getChannelLicense(videoUrl.toString());
            Log.i("swidebug", ". TvInputServiceImpl TvInputSessionImpl createPlayer() videoUrl: " + videoUrl + ", licenseUrl: " + licenseUrl);
            releasePlayer();
            mPlayer = new ExoPlayerImpl(mContext, videoType, videoUrl, licenseUrl);
            mPlayer.addListener(this);
            mPlayer.setCaptionListener(this);
            mPlayer.prepare();
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl createPlayer()");
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onSetCaptionEnabled() en: " + enabled);
            mCaptionEnabled = enabled;
            if (mPlayer != null) {
                if (mCaptionEnabled) {
                    mPlayer.setSelectedTrack(ExoPlayerImpl.TRACK_TYPE_TEXT, mSelectedSubtitleTrackIndex);
                } else {
                    mPlayer.setSelectedTrack(ExoPlayerImpl.TRACK_TYPE_TEXT, ExoPlayerImpl.TRACK_DISABLED);
                }
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onSetCaptionEnabled()");
        }

        @Override
        public boolean onSelectTrack(int tvTrackType, String trackId) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onSelectTrack()");
            if (trackId == null) {
                Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onSelectTrack() track null");
                return true;
            }

            int trackIndex = getIndexFromTrackId(trackId);
            if (mPlayer != null) {
                if (tvTrackType == TvTrackInfo.TYPE_SUBTITLE) {
                    if (! mCaptionEnabled) {
                        Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onSelectTrack() no captions");
                        return false;
                    }
                    mSelectedSubtitleTrackIndex = trackIndex;
                }

                mPlayer.setSelectedTrack(mTrackTypes.get(tvTrackType), trackIndex);
                notifyTrackSelected(tvTrackType, trackId);
                Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onSelectTrack() true");
                return true;
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onSelectTrack() false");
            return false;
        }

        private void releasePlayer() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl releasePlayer()");
            if (mPlayer != null) {
                mPlayer.removeListener(this);
                mPlayer.setSurface(null);
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl releasePlayer()");
        }

        @Override
        public void onRelease() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onRelease()");
            super.onRelease();
            releasePlayer();
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onRelease()");
        }

        @Override
        public void onBlockContent(TvContentRating rating) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onBlockContent()");
            super.onBlockContent(rating);
            releasePlayer();
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onBlockContent()");
        }

        private float getCaptionFontSize() {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl getCaptionFontSize()");
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point displaySize = new Point();
            display.getSize(displaySize);
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl getCaptionFontSize()");
            return Math.max(getResources().getDimension(R.dimen.subtitle_minimum_font_size),
                    CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
        }

        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onStateChanged() pvr: " +
                    playWhenReady + ", state: " + playbackState);
            if (mPlayer == null) {
                Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onStateChanged() mPlayer null");
                return;
            }

            if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
                notifyTracksChanged(getAllTracks());
                String audioId = getTrackId(TvTrackInfo.TYPE_AUDIO,
                        mPlayer.getSelectedTrack(ExoPlayerImpl.TRACK_TYPE_AUDIO));
                String videoId = getTrackId(TvTrackInfo.TYPE_VIDEO,
                        mPlayer.getSelectedTrack(ExoPlayerImpl.TRACK_TYPE_VIDEO));
                String textId = getTrackId(TvTrackInfo.TYPE_SUBTITLE,
                        mPlayer.getSelectedTrack(ExoPlayerImpl.TRACK_TYPE_TEXT));

                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioId);
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, videoId);
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, textId);
                notifyVideoAvailable();
            } else if (Math.abs(mPlayer.getPlaybackSpeed() - 1) < 0.1 &&
                    playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onStateChanged()");
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                float pixelWidthHeightRatio) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onVideoSizeChanged()");
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onVideoSizeChanged()");
            // Do nothing.
        }

        @Override
        public void onError(Exception e) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onError()");
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onError()");
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onCues(List<Cue> cues) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl onCues()");
            if (mSubtitleView != null) {
                mSubtitleView.setCues(cues);
            }
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl onCues()");
        }

        public void requestEpgSync(final Uri channelUri) {
            Log.i("swidebug", "> TvInputServiceImpl TvInputSessionImpl requestEpgSync()");
            EpgSyncJobService.requestImmediateSync(TvInputServiceImpl.this, mInputId,
                    new ComponentName(TvInputServiceImpl.this, EpgSyncJobServiceImpl.class));
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    onTune(channelUri);
                }
            }, EPG_SYNC_DELAYED_PERIOD_MS);
            Log.i("swidebug", "< TvInputServiceImpl TvInputSessionImpl requestEpgSync()");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private class RecordingSessionImpl extends RecordingSession {
        private static final String TAG = "RecordingSession";
        private String mInputId;
        private long mStartTimeMs;

        public RecordingSessionImpl(Context context, String inputId) {
            super(context, inputId);
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl RecordingSessionImpl()");
            mInputId = inputId;
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl RecordingSessionImpl()");
        }

        @Override
        public void onTune(Uri uri) {
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl onTune() uri: " + uri);
            super.onTune(uri);
            if (DEBUG) {
                Log.d(TAG, "Tune recording session to " + uri);
            }
            // By default, the number of tuners for this service is one. When a channel is being
            // recorded, no other channel from this TvInputService will be accessible. Developers
            // should call notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY) to alert
            // the framework that this recording cannot be completed.
            // Developers can update the tuner count in xml/richtvinputservice or programmatically
            // by adding it to TvInputInfo.updateTvInputInfo.
            notifyTuned(uri);
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl onTune()");
        }

        @Override
        public void onStartRecording(final Uri uri) {
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl onStartRecording()");
            super.onStartRecording(uri);
            if (DEBUG) {
                Log.d(TAG, "onStartRecording");
            }
            mStartTimeMs = System.currentTimeMillis();
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl onStartRecording()");
        }

        @Override
        public void onStopRecording(Program programToRecord) {
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl onStopRecording()");
            if (DEBUG) {
                Log.d(TAG, "onStopRecording");
            }
            // In this sample app, since all of the content is VOD, the video URL is stored.
            // If the video was live, the start and stop times should be noted using
            // RecordedProgram.Builder.setStartTimeUtcMillis and .setEndTimeUtcMillis.
            // The recordingstart time will be saved in the InternalProviderData.
            // Additionally, the stream should be recorded and saved as
            // a new file.
            long currentTime = System.currentTimeMillis();
            InternalProviderData internalProviderData = programToRecord.getInternalProviderData();
            internalProviderData.setRecordingStartTime(mStartTimeMs);
            RecordedProgram recordedProgram = new RecordedProgram.Builder(programToRecord)
                        .setInputId(mInputId)
                        .setRecordingDataUri(
                                programToRecord.getInternalProviderData().getVideoUrl())
                        .setRecordingDurationMillis(currentTime - mStartTimeMs)
                        .setInternalProviderData(internalProviderData)
                        .build();
            notifyRecordingStopped(recordedProgram);
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl onStopRecording()");
        }

        @Override
        public void onStopRecordingChannel(Channel channelToRecord) {
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl onStopRecordingChannel()");
            if (DEBUG) {
                Log.d(TAG, "onStopRecording");
            }
            // Program sources in this sample always include program info, so execution here
            // indicates an error.
            notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl onStopRecordingChannel()");
        }

        @Override
        public void onRelease() {
            Log.i("swidebug", "> TvInputServiceImpl RecordingSessionImpl onRelease()");
            if (DEBUG) {
                Log.d(TAG, "onRelease");
            }
            Log.i("swidebug", "< TvInputServiceImpl RecordingSessionImpl onRelease()");
        }
    }
}
