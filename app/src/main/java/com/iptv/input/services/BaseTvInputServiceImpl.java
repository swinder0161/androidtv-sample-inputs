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

package com.iptv.input.services;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.SubtitleView;

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
import com.iptv.input.R;
import com.iptv.input.player.ExoPlayerImpl;
import com.iptv.input.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TvInputService which provides a full implementation of EPG, subtitles, multi-audio, parental
 * controls, and overlay view.
 */
@UnstableApi
public class BaseTvInputServiceImpl extends BaseTvInputService {
    private static final String TAG = "BaseTvInputServiceImpl";
    private static final boolean DEBUG = false;
    private static final long EPG_SYNC_DELAYED_PERIOD_MS = 1000 * 2; // 2 Seconds
    private CaptioningManager mCaptioningManager;
    private String mPrevChannel = "";

    private final Map<Integer, Integer> mTrackTypes = new HashMap<Integer, Integer>() {{
        put(TvTrackInfo.TYPE_AUDIO, ExoPlayerImpl.TRACK_TYPE_AUDIO);
        put(TvTrackInfo.TYPE_VIDEO, ExoPlayerImpl.TRACK_TYPE_VIDEO);
        put(TvTrackInfo.TYPE_SUBTITLE, ExoPlayerImpl.TRACK_TYPE_TEXT);
    }};

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("swidebug", "> BaseTvInputServiceImpl onCreate()");
        mCaptioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        Log.i("swidebug", "< BaseTvInputServiceImpl onCreate()");
    }

    @Override
    public final Session onCreateSession(String inputId) {
        Log.i("swidebug", "> BaseTvInputServiceImpl onCreateSession() inputId: " + inputId);
        TvInputSessionImpl session = new TvInputSessionImpl(this, inputId);
        session.setOverlayViewEnabled(true);
        Session s = super.sessionCreated(session);
        Log.i("swidebug", "< BaseTvInputServiceImpl onCreateSession()");
        return s;
    }

    @Nullable
    @Override
    public TvInputService.RecordingSession onCreateRecordingSession(String inputId) {
        Log.i("swidebug", "> BaseTvInputServiceImpl onCreateRecordingSession()");
        TvInputService.RecordingSession r = new RecordingSessionImpl(this, inputId);
        Log.i("swidebug", "> BaseTvInputServiceImpl onCreateRecordingSession()");
        return r;
    }

    /**
     * Gets the track id of the track type and track index.
     *
     * @param tvTrackType  the type of the track e.g. TvTrackInfo.TYPE_AUDIO
     * @param trackIndex the index of that track within the media. e.g. 0, 1, 2...
     * @return the track id for the type & index combination.
     */
    private static String getTrackId(int tvTrackType, int trackIndex) {
        Log.i("swidebug", "> BaseTvInputServiceImpl getTrackId()");
        Log.i("swidebug", "< BaseTvInputServiceImpl getTrackId()");
        return tvTrackType + "-" + trackIndex;
    }

    /**
     * Gets the index of the track for a given track id.
     *
     * @param trackId the track id.
     * @return the track index for the given id, as an integer.
     */
    private static int getIndexFromTrackId(String trackId) {
        Log.i("swidebug", "> BaseTvInputServiceImpl getIndexFromTrackId()");
        Log.i("swidebug", "< BaseTvInputServiceImpl getIndexFromTrackId()");
        return Integer.parseInt(trackId.split("-")[1]);
    }

    @UnstableApi
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
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl TvInputSessionImpl() inputId: " + inputId);
            mCaptionEnabled = mCaptioningManager.isEnabled();
            mContext = context;
            mInputId = inputId;
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl TvInputSessionImpl()");
        }

        @Override
        public View onCreateOverlayView() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onCreateOverlayView()");
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

            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onCreateOverlayView()");
            return mSubtitleView;
        }

        private List<TvTrackInfo> getAllTracks() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl getAllTracks()");
            String trackId;
            List<TvTrackInfo> tracks = new ArrayList<>();

            for (int tvTrackType : mTrackTypes.keySet()) {
                int playerTrackType = mTrackTypes.get(tvTrackType);
                int count = mPlayer.getTrackCount(playerTrackType);
                for (int i = 0; i < count; i++) {
                    Format format = mPlayer.getTrackFormat(playerTrackType, i);
                    trackId = getTrackId(tvTrackType, i);
                    TvTrackInfo.Builder builder = new TvTrackInfo.Builder(tvTrackType, trackId);

                    if (playerTrackType == ExoPlayerImpl.TRACK_TYPE_VIDEO) {
                        Size sz = mPlayer.getMaxSize();
                        if (sz.getWidth() != Format.NO_VALUE) {
                            builder.setVideoWidth(sz.getWidth());
                        } else if (format.width != Format.NO_VALUE) {
                            builder.setVideoWidth(format.width);
                        }
                        if (sz.getHeight() != Format.NO_VALUE) {
                            builder.setVideoHeight(sz.getHeight());
                        } else if (format.height != Format.NO_VALUE) {
                            builder.setVideoHeight(format.height);
                        }
                    } else if (playerTrackType == ExoPlayerImpl.TRACK_TYPE_AUDIO) {
                        builder.setAudioChannelCount(format.channelCount);
                        builder.setAudioSampleRate(format.sampleRate);
                        Log.i("swidebug", ". BaseTvInputServiceImpl TvInputSessionImpl getAllTracks() AUDIO language: " + format.language);
                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
                            // TvInputInfo expects {@code null} for unknown language.
                            builder.setLanguage(format.language);
                        }
                    } else { //playerTrackType == ExoPlayerImpl.TRACK_TYPE_TEXT
                        Log.i("swidebug", ". BaseTvInputServiceImpl TvInputSessionImpl getAllTracks() TEXT language: " + format.language);
                        if (format.language != null && !UNKNOWN_LANGUAGE.equals(format.language)) {
                            // TvInputInfo expects {@code null} for unknown language.
                            builder.setLanguage(format.language);
                        }
                    }

                    tracks.add(builder.build());
                }
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl getAllTracks()");
            return tracks;
        }

        private boolean onPlayProgramError(String e) {
            requestEpgSync(getCurrentChannelUri());
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            Log.i("swidebug", "< BaseTvInputServiceImpl BaseTvInputSessionImpl onPlayProgram() " + e + " null");
            return false;
        }

        @Override
        public boolean onPlayProgram(Program program, long startPosMs) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onPlayProgram() program: " + program + ", startPosMs: " + startPosMs);
            if (program == null) {
                Channel ch = ModelUtils.getChannel(mContext.getContentResolver(), getCurrentChannelUri());
                InternalProviderData data;
                if (ch != null) {
                    data = ch.getInternalProviderData();
                    if (data != null) {
                        String channelId = data.getVideoUrl();
                        if (channelId != null) {
                            data.setVideoType(TvContractUtils.SOURCE_TYPE_MPEG_DASH);
                            program = new Program.Builder()
                                    .setStartTimeUtcMillis(System.currentTimeMillis())
                                    .setEndTimeUtcMillis(System.currentTimeMillis()+10000)
                                    .setInternalProviderData(data)
                                    .build();
                        } else {
                            return onPlayProgramError("program and channelId");
                        }
                    } else {
                        return onPlayProgramError("program and channel internal data");
                    }
                } else {
                    return onPlayProgramError("program and channel");
                }
                EpgSyncJobServiceImpl.requestImmediateSync(mContext.getApplicationContext());
            }
            InternalProviderData data = program.getInternalProviderData();
            if (data == null) {
                return onPlayProgramError("data");
            }
            String videoUrl = data.getVideoUrl();
            Log.i("swidebug", ". BaseTvInputServiceImpl TvInputSessionImpl onPlayProgram() videoUrl: " +
                    videoUrl + ", mPrevChannel: " + mPrevChannel);
            if(mPlayer!= null && 0 == mPrevChannel.compareToIgnoreCase(videoUrl) && mPlayer.heartBeat()) {
                return true;
            }
            createPlayer(data.getVideoType(), videoUrl);
            if (startPosMs > 0) {
                mPlayer.seekTo(startPosMs);
            }
            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            mPlayer.setPlayWhenReady(true);
            mPrevChannel = videoUrl;
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onPlayProgram()");
            return true;
        }

        public boolean onPlayRecordedProgram(RecordedProgram recordedProgram) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onPlayRecordedProgram()");
            createPlayer(recordedProgram.getInternalProviderData().getVideoType(),
                    recordedProgram.getInternalProviderData().getVideoUrl());

            long recordingStartTime = recordedProgram.getInternalProviderData()
                    .getRecordedProgramStartTime();
            mPlayer.seekTo(recordingStartTime - recordedProgram.getStartTimeUtcMillis());
            notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            mPlayer.setPlayWhenReady(true);
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onPlayRecordedProgram()");
            return true;
        }

        public TvPlayer getTvPlayer() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl getTvPlayer()");
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl getTvPlayer() mPlayer: " + mPlayer);
            return mPlayer;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onTune() uri: " + channelUri);
            if (DEBUG) {
                Log.d(TAG, "Tune to " + channelUri.toString());
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            releasePlayer();
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onTune()");
            return super.onTune(channelUri);
        }

        @Override
        public void onPlayAdvertisement(Advertisement advertisement) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onPlayAdvertisement()");
            createPlayer(TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE,
                    advertisement.getRequestUrl());
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onPlayAdvertisement()");
        }

        private void createPlayer(int videoType, String videoId) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl createPlayer() videoType: " + videoType + ", videoId: " + videoId);
            releasePlayer();
            mPlayer = new ExoPlayerImpl(mContext, videoType, videoId);
            mPlayer.addListener(this);
            mPlayer.setCaptionListener(this);
            mPlayer.prepare();
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl createPlayer()");
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onSetCaptionEnabled() en: " + enabled);
            mCaptionEnabled = enabled;
            if (mPlayer != null) {
                if (mCaptionEnabled) {
                    mPlayer.setSelectedTrack(ExoPlayerImpl.TRACK_TYPE_TEXT, mSelectedSubtitleTrackIndex);
                } else {
                    mPlayer.setSelectedTrack(ExoPlayerImpl.TRACK_TYPE_TEXT, ExoPlayerImpl.TRACK_DISABLED);
                }
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onSetCaptionEnabled()");
        }

        @Override
        public boolean onSelectTrack(int tvTrackType, String trackId) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onSelectTrack()");
            if (trackId == null) {
                Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onSelectTrack() track null");
                return true;
            }

            int trackIndex = getIndexFromTrackId(trackId);
            if (mPlayer != null) {
                if (tvTrackType == TvTrackInfo.TYPE_SUBTITLE) {
                    if (! mCaptionEnabled) {
                        Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onSelectTrack() no captions");
                        return false;
                    }
                    mSelectedSubtitleTrackIndex = trackIndex;
                }

                mPlayer.setSelectedTrack(mTrackTypes.get(tvTrackType), trackIndex);
                notifyTrackSelected(tvTrackType, trackId);
                Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onSelectTrack() true");
                return true;
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onSelectTrack() false");
            return false;
        }

        private void releasePlayer() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl releasePlayer()");
            if (mPlayer != null) {
                mPlayer.removeListener(this);
                mPlayer.setSurface(null);
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl releasePlayer()");
        }

        @Override
        public void onRelease() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onRelease()");
            super.onRelease();
            releasePlayer();
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onRelease()");
        }

        @Override
        public void onBlockContent(TvContentRating rating) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onBlockContent()");
            super.onBlockContent(rating);
            releasePlayer();
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onBlockContent()");
        }

        private float getCaptionFontSize() {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl getCaptionFontSize()");
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point displaySize = new Point();
            display.getSize(displaySize);
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl getCaptionFontSize()");
            return Math.max(getResources().getDimension(R.dimen.subtitle_minimum_font_size),
                    CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
        }

        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onStateChanged() pvr: " +
                    playWhenReady + ", state: " + playbackState);
            if (mPlayer == null) {
                Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onStateChanged() mPlayer null");
                return;
            }

            if (playWhenReady && playbackState == Player.STATE_READY) {
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
                    playWhenReady && playbackState == Player.STATE_BUFFERING) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onStateChanged()");
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                float pixelWidthHeightRatio) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onVideoSizeChanged()");
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onVideoSizeChanged()");
            // Do nothing.
        }

        @Override
        public void onError(Exception e) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onError()");
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onError()");
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onCues(List<Cue> cues) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl onCues()");
            if (mSubtitleView != null) {
                mSubtitleView.setCues(cues);
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl onCues()");
        }

        public void requestEpgSync(final Uri channelUri) {
            Log.i("swidebug", "> BaseTvInputServiceImpl TvInputSessionImpl requestEpgSync()");
            EpgSyncJobService.requestImmediateSync(BaseTvInputServiceImpl.this, mInputId,
                    new ComponentName(BaseTvInputServiceImpl.this, EpgSyncJobServiceImpl.class));
            if (null != channelUri)
                new Handler(Looper.getMainLooper()).postDelayed(() -> onTune(channelUri), EPG_SYNC_DELAYED_PERIOD_MS);
            Log.i("swidebug", "< BaseTvInputServiceImpl TvInputSessionImpl requestEpgSync()");
        }
    }

    private static class RecordingSessionImpl extends RecordingSession {
        private static final String TAG = "RecordingSession";
        private final String mInputId;
        private long mStartTimeMs;

        public RecordingSessionImpl(Context context, String inputId) {
            super(context, inputId);
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl RecordingSessionImpl()");
            mInputId = inputId;
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl RecordingSessionImpl()");
        }

        @Override
        public void onTune(Uri uri) {
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl onTune() uri: " + uri);
            super.onTune(uri);
            if (DEBUG) {
                Log.d(TAG, "Tune recording session to " + uri);
            }
            // By default, the number of tuners for this service is one. When a channel is being
            // recorded, no other channel from this TvInputService will be accessible. Developers
            // should call notifyError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY) to alert
            // the framework that this recording cannot be completed.
            // Developers can update the tuner count in xml/base_tv_input_service_impl or programmatically
            // by adding it to TvInputInfo.updateTvInputInfo.
            notifyTuned(uri);
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl onTune()");
        }

        @Override
        public void onStartRecording(final Uri uri) {
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl onStartRecording()");
            super.onStartRecording(uri);
            if (DEBUG) {
                Log.d(TAG, "onStartRecording");
            }
            mStartTimeMs = System.currentTimeMillis();
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl onStartRecording()");
        }

        @Override
        public void onStopRecording(Program programToRecord) {
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl onStopRecording()");
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
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl onStopRecording()");
        }

        @Override
        public void onStopRecordingChannel(Channel channelToRecord) {
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl onStopRecordingChannel()");
            if (DEBUG) {
                Log.d(TAG, "onStopRecording");
            }
            // Program sources in this sample always include program info, so execution here
            // indicates an error.
            notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl onStopRecordingChannel()");
        }

        @Override
        public void onRelease() {
            Log.i("swidebug", "> BaseTvInputServiceImpl RecordingSessionImpl onRelease()");
            if (DEBUG) {
                Log.d(TAG, "onRelease");
            }
            Log.i("swidebug", "< BaseTvInputServiceImpl RecordingSessionImpl onRelease()");
        }
    }
}
