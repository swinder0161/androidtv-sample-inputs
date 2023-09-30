package com.iptv.input.player;

import android.content.Context;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.dash.DashChunkSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;

import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import com.iptv.input.m3u.EPGImpl;
import com.iptv.input.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@UnstableApi
public class ExoPlayerImpl implements Player.Listener, TvPlayer {
    /** Constants */
    public static final int TRACK_TYPE_VIDEO = C.TRACK_TYPE_VIDEO;
    public static final int TRACK_TYPE_AUDIO = C.TRACK_TYPE_AUDIO;
    public static final int TRACK_TYPE_TEXT = C.TRACK_TYPE_TEXT;
    public static final int TRACK_DISABLED = -1;

    /** Interfaces */
    public interface Listener {
        void onStateChanged(boolean playWhenReady, int playbackState);

        void onError(Exception e);

        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                float pixelWidthHeightRatio);
    }

    public interface CaptionListener {
        void onCues(List<Cue> cues);
    }

    private final Context mContext;
    private final ExoPlayer mPlayer;
    private PlaybackParams mPlaybackParams;
    private final CopyOnWriteArrayList<Listener> mListeners;
    private final List<TvPlayer.Callback> mTvPlayerCallbacks;
    private CaptionListener mCaptionListener;
    private int mPlaybackState;
    private boolean mPlayWhenReady;
    private final TracksImpl mTracks;

    public ExoPlayerImpl(Context context, int contentType, String channelId) {
        Log.i("swidebug", "> ExoPlayerImpl ExoPlayerImpl() contentType: " + contentType +
                ", channelId: " + channelId);
        mContext = context;
        mPlayer = new ExoPlayer.Builder(mContext).setSeekForwardIncrementMs(10000)
                .setSeekBackIncrementMs(10000).build();
        mPlaybackParams = null;
        mListeners = new CopyOnWriteArrayList<>();
        mTvPlayerCallbacks = new CopyOnWriteArrayList<>();
        mCaptionListener = null;
        mPlaybackState = ExoPlayer.STATE_IDLE;
        mPlayWhenReady = false;
        mTracks = new TracksImpl(mPlayer);

        mPlayer.addListener(this);

        String videoUrl = EPGImpl.getInstance().getChannelUrl(channelId);
        String licenseUrl = EPGImpl.getInstance().getChannelLicenseUrl(channelId);

        MediaSource mediaSource = getMediaSource(contentType, videoUrl, licenseUrl);
        if (null != mediaSource) {
            mPlayer.setMediaSource(mediaSource, true);
        }
        Log.i("swidebug", "< ExoPlayerImpl ExoPlayerImpl()");
    }

    public void prepare() {
        Log.i("swidebug", "> ExoPlayerImpl prepare()");
        if (mPlaybackState != ExoPlayer.STATE_IDLE)
            mPlayer.stop();
        maybeReportPlayerState();
        Log.i("swidebug", "< ExoPlayerImpl prepare()");
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        Log.i("swidebug", "> ExoPlayerImpl setPlayWhenReady()");
        mPlayer.setPlayWhenReady(playWhenReady);
        Log.i("swidebug", "< ExoPlayerImpl setPlayWhenReady()");
    }

    public int getTrackCount(int trackType) {
        Log.i("swidebug", "> ExoPlayerImpl getTrackCount() type: " + getTrackType(trackType));
        int cnt = mTracks.getTrackCount(trackType);
        Log.i("swidebug", "< ExoPlayerImpl getTrackCount() cnt: " + cnt);
        return cnt;
    }

    public Format getTrackFormat(int trackType, int index) {
        Log.i("swidebug", "> ExoPlayerImpl getTrackFormat() type: " + getTrackType(trackType) +
                ", index: " + index);
        Format fmt = mTracks.getTrackFormat(trackType, index);
        if ((fmt != null) && (trackType == TRACK_TYPE_VIDEO)) {
            fmt = mPlayer.getVideoFormat();
        }
        Log.i("swidebug", "< ExoPlayerImpl getTrackFormat() fmt: " + fmt);
        return fmt;
    }

    public int getSelectedTrack(int trackType) {
        Log.i("swidebug", "> ExoPlayerImpl getSelectedTrack() type: " + getTrackType(trackType));
        int index = mTracks.getSelectedTrack(trackType);
        Log.i("swidebug", "< ExoPlayerImpl getSelectedTrack() index: " + index);
        return index;
    }

    public void setSelectedTrack(int trackType, int index) {
        Log.i("swidebug", "> ExoPlayerImpl setSelectedTrack() type: " + getTrackType(trackType) +
                ", index: " + index);
        int cnt = getTrackCount(trackType);
        if (index >= cnt) {
            index = -1;
        }

        TrackSelectionParameters.Builder builder = mPlayer.getTrackSelectionParameters().buildUpon();

        if (index < 0) {
            Log.e("swidebug", ". ExoPlayerImpl setSelectedTrack() disabling track: " + getTrackType(trackType));
            builder.setTrackTypeDisabled(trackType, true);

            if (trackType == TRACK_TYPE_TEXT && mCaptionListener != null) {
                mCaptionListener.onCues(Collections.emptyList());
            }
        } else {
            Log.e("swidebug", ". ExoPlayerImpl setSelectedTrack() " + getTrackFormat(trackType, index));
            builder.setTrackTypeDisabled(trackType, false)
                    .setOverrideForType(new TrackSelectionOverride(mTracks.getTrackGroup(trackType, index), 0));
        }
        mPlayer.setTrackSelectionParameters(builder.build());
        Log.i("swidebug", "< ExoPlayerImpl setSelectedTrack()");
    }

    public float getPlaybackSpeed() {
        Log.i("swidebug", "> ExoPlayerImpl getPlaybackSpeed()");
        float s = mPlaybackParams == null ? DefaultAudioSink.DEFAULT_PLAYBACK_SPEED : mPlaybackParams.getSpeed();
        Log.i("swidebug", "< ExoPlayerImpl getPlaybackSpeed() speed: " + s);
        return s;
    }

    public void addListener(Listener listener) {
        Log.i("swidebug", "> ExoPlayerImpl addListener()");
        mListeners.add(listener);
        Log.i("swidebug", "< ExoPlayerImpl addListener()");
    }
    public void removeListener(Listener listener) {
        Log.i("swidebug", "> ExoPlayerImpl removeListener()");
        mListeners.remove(listener);
        Log.i("swidebug", "< ExoPlayerImpl removeListener()");
    }

    public void setCaptionListener(CaptionListener listener) {
        Log.i("swidebug", "> ExoPlayerImpl setCaptionListener()");
        mCaptionListener = listener;
        Log.i("swidebug", "< ExoPlayerImpl setCaptionListener()");
    }

    public void stop() {
        Log.i("swidebug", "> ExoPlayerImpl stop()");
        mPlayer.stop();
        Log.i("swidebug", "< ExoPlayerImpl stop()");
    }

    public void release() {
        Log.i("swidebug", "> ExoPlayerImpl release()");
        mPlaybackState = ExoPlayer.STATE_IDLE;
        mPlayer.release();
        Log.i("swidebug", "< ExoPlayerImpl release()");
    }

    public Size getMaxSize() {
        Log.i("swidebug", "> ExoPlayerImpl getMaxSize()");
        Size sz = mTracks.getMaxSize();
        Log.i("swidebug", "< ExoPlayerImpl getMaxSize(): " + sz);
        return sz;
    }

    private MediaSource getMediaSource(int contentType, String videoUrl, String licenseUrl) {
        Log.i("swidebug", "> ExoPlayerImpl getMediaSource() contentType: " + contentType +
                ", videoUrl: " + videoUrl + ", licenseUrl: " + licenseUrl);
        final String USER_AGENT = "ExoPlayer-Drm";
        DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(USER_AGENT)
                .setTransferListener(
                        new DefaultBandwidthMeter.Builder(mContext)
                                .setResetOnNetworkTypeChange(false)
                                .build()
                );
        DefaultHttpDataSource.Factory manifestDataSourceFactory = new DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT);

        MediaSource mediaSource;
        switch (contentType) {
            case TvContractUtils.SOURCE_TYPE_MPEG_DASH: {
                Log.i("swidebug", ". ExoPlayerImpl getMediaSource() SOURCE_TYPE_MPEG_DASH");
                DashChunkSource.Factory dashChunkSourceFactory = new DefaultDashChunkSource.Factory(defaultHttpDataSourceFactory);

                mediaSource = new DashMediaSource.Factory(dashChunkSourceFactory, manifestDataSourceFactory)
                        .createMediaSource(
                                new MediaItem.Builder()
                                        .setUri(Uri.parse(videoUrl))
                                        .setDrmConfiguration(
                                                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                                        .setLicenseUri(licenseUrl).build())
                                        .setMimeType(MimeTypes.APPLICATION_MPD)
                                        .setTag(null)
                                        .build());
            } break;
            case TvContractUtils.SOURCE_TYPE_SS: {
                Log.i("swidebug", ". ExoPlayerImpl getMediaSource() SOURCE_TYPE_SS");
                mediaSource = null;
            } break;
            case TvContractUtils.SOURCE_TYPE_HLS: {
                Log.i("swidebug", ". ExoPlayerImpl getMediaSource() SOURCE_TYPE_HLS");
                mediaSource = new HlsMediaSource.Factory(manifestDataSourceFactory)
                        .createMediaSource(
                                new MediaItem.Builder()
                                        .setUri(Uri.parse(videoUrl))
                                        .setDrmConfiguration(
                                                new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                                        .setLicenseUri(licenseUrl).build())
                                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                                        .setTag(null)
                                        .build());
            } break;
            case TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE: {
                Log.i("swidebug", ". ExoPlayerImpl getMediaSource() SOURCE_TYPE_HTTP_PROGRESSIVE");
                mediaSource = null;
            } break;
            default: {
                mediaSource = null;
            }
        }
        Log.i("swidebug", "< ExoPlayerImpl getMediaSource()");
        return mediaSource;
    }

    private String getTrackType(int trackType) {
        switch(trackType) {
            case TRACK_TYPE_VIDEO: return "TRACK_TYPE_VIDEO";
            case TRACK_TYPE_AUDIO: return "TRACK_TYPE_AUDIO";
            case TRACK_TYPE_TEXT: return "TRACK_TYPE_TEXT";
        }
        return "TRACK_TYPE_" + trackType;
    }

    private String strPlaybackState(int state) {
        switch(state) {
            case ExoPlayer.STATE_IDLE:
                return "STATE_IDLE";
            case ExoPlayer.STATE_BUFFERING:
                return "STATE_BUFFERING";
            case ExoPlayer.STATE_READY:
                return "STATE_READY";
            case ExoPlayer.STATE_ENDED:
                return "STATE_ENDED";
        }
        return "STATE_" + state;
    }

    private void maybeReportPlayerState() {
        Log.i("swidebug", "> ExoPlayerImpl maybeReportPlayerState()");
        boolean playWhenReady = mPlayer.getPlayWhenReady();
        int playbackState = mPlayer.getPlaybackState();
        Log.i("swidebug", ". ExoPlayerImpl maybeReportPlayerState() playWhenReady: " + playWhenReady +
                ", playbackState: " + strPlaybackState(playbackState));
        if (mPlayWhenReady != playWhenReady ||
                mPlaybackState != playbackState) {
            for (Listener listener : mListeners) {
                listener.onStateChanged(playWhenReady, playbackState);
            }
            mPlayWhenReady = playWhenReady;
            mPlaybackState = playbackState;
        }
        Log.i("swidebug", "< ExoPlayerImpl maybeReportPlayerState()");
    }

    /** Overrides from Player.Listener start*/
    @Override
    public void onPlaybackStateChanged(int playbackState) {
        Player.Listener.super.onPlaybackStateChanged(playbackState);
        Log.i("swidebug", "> ExoPlayerImpl onPlaybackStateChanged() playbackState: " + strPlaybackState(playbackState));
        for (Callback tvCallback : mTvPlayerCallbacks) {
            if (mPlayWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                tvCallback.onCompleted();
            } else if (mPlayWhenReady && playbackState == ExoPlayer.STATE_READY) {
                tvCallback.onStarted();
            }
        }
        maybeReportPlayerState();
        Log.i("swidebug", "< ExoPlayerImpl onPlaybackStateChanged()");
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        Player.Listener.super.onPlayWhenReadyChanged(playWhenReady, reason);
        Log.i("swidebug", "> ExoPlayerImpl onPlayWhenReadyChanged() playWhenReady: " +
                playWhenReady + ", reason: " + reason);
        for (Callback tvCallback : mTvPlayerCallbacks) {
            if (playWhenReady && mPlaybackState == ExoPlayer.STATE_ENDED) {
                tvCallback.onCompleted();
            } else if (mPlayWhenReady && mPlaybackState == ExoPlayer.STATE_READY) {
                tvCallback.onStarted();
            }
        }
        maybeReportPlayerState();
        Log.i("swidebug", "< ExoPlayerImpl onPlayWhenReadyChanged()");
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        Player.Listener.super.onPlayerError(error);
        Log.i("swidebug", "> ExoPlayerImpl onPlayerError() error: " + error.getMessage());
        for (Callback tvCallback : mTvPlayerCallbacks) {
            tvCallback.onError(error);
        }
        for (Listener listener : mListeners) {
            listener.onError(error);
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            Log.i("swidebug", "> ExoPlayerImpl onPlayerError() run()");
            mPlayer.seekTo((long) (mPlayer.getCurrentPosition() + 1000));
            mPlayer.prepare();
            mPlayer.setPlayWhenReady(true);
            Log.i("swidebug", "< ExoPlayerImpl onPlayerError() run()");
        });
        //maybeReportPlayerState();
        Log.i("swidebug", "< ExoPlayerImpl onPlayerError()");
    }

    @Override
    public void onCues(@NonNull CueGroup cueGroup) {
        Player.Listener.super.onCues(cueGroup);
        Log.i("swidebug", "> ExoPlayerImpl onCues() cueGroup: " + cueGroup);
        if (mCaptionListener != null && getSelectedTrack(TRACK_TYPE_TEXT) != TRACK_DISABLED) {
            mCaptionListener.onCues(cueGroup.cues);
        }
        Log.i("swidebug", "< ExoPlayerImpl onCues()");
    }

    @Override
    public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
        Player.Listener.super.onVideoSizeChanged(videoSize);
        Log.i("swidebug", "> ExoPlayerImpl onVideoSizeChanged() videoSize: " + videoSize.width + " X " + videoSize.height);
        for (Listener listener : mListeners) {
            listener.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees,
                    videoSize.pixelWidthHeightRatio);
        }
        Log.i("swidebug", "< ExoPlayerImpl onVideoSizeChanged()");
    }

    @Override
    public void onTracksChanged(@NonNull Tracks tracks) {
        Log.i("swidebug", "> ExoPlayerImpl onTracksChanged()");
        mTracks.onTracksChanged(tracks);
        Log.i("swidebug", "< ExoPlayerImpl onTracksChanged()");
    }
    /* Overrides from Player.Listener end*/

    /** Overrides from TvPlayer start*/
    @Override
    public void seekTo(long positionMs) {
        Log.i("swidebug", "> ExoPlayerImpl seekTo()");
        mPlayer.seekTo(positionMs);
        Log.i("swidebug", "< ExoPlayerImpl seekTo()");
    }

    @Override
    public void setPlaybackParams(PlaybackParams params) {
        Log.i("swidebug", "> ExoPlayerImpl setPlaybackParams() params: " + params);
        mPlaybackParams = params;
        Log.i("swidebug", "< ExoPlayerImpl setPlaybackParams()");
    }

    @Override
    public long getCurrentPosition() {
        Log.i("swidebug", "> ExoPlayerImpl getCurrentPosition()");
        long r = 0;
        if (null != mPlayer) {
            r = mPlayer.getCurrentPosition();
        }
        Log.i("swidebug", "< ExoPlayerImpl getCurrentPosition() pos: " + r);
        return r;
    }

    @Override
    public long getDuration() {
        Log.i("swidebug", "> ExoPlayerImpl getDuration()");
        long r = 0;
        if (null != mPlayer) {
            r = mPlayer.getDuration();
        }
        Log.i("swidebug", "< ExoPlayerImpl getDuration() dur: " + r);
        return r;
    }

    @Override
    public void setSurface(Surface surface) {
        Log.i("swidebug", "> ExoPlayerImpl setSurface() surface: " + surface);
        //mPlayer.setPlayWhenReady(false);
        mPlayer.setVideoSurface(surface);
        if (null != surface) {
            //mPlayer.setPlayWhenReady(true);
            mPlayer.seekToDefaultPosition();
            mPlayer.prepare();
        }
        Log.i("swidebug", "< ExoPlayerImpl setSurface()");
    }

    @Override
    public void setVolume(float volume) {
        Log.i("swidebug", ". ExoPlayerImpl setVolume() ignore: " + volume);
    }

    @Override
    public void play() {
        Log.i("swidebug", "> ExoPlayerImpl play()");
        mPlayer.setPlayWhenReady(true);
        Log.i("swidebug", "< ExoPlayerImpl play()");
    }

    @Override
    public void pause() {
        Log.i("swidebug", "> ExoPlayerImpl pause()");
        mPlayer.setPlayWhenReady(false);
        Log.i("swidebug", "< ExoPlayerImpl pause()");
    }

    @Override
    public void registerCallback(TvPlayer.Callback callback) {
        Log.i("swidebug", "> ExoPlayerImpl registerCallback()");
        mTvPlayerCallbacks.add(callback);
        Log.i("swidebug", "< ExoPlayerImpl registerCallback()");
    }

    @Override
    public void unregisterCallback(TvPlayer.Callback callback) {
        Log.i("swidebug", "> ExoPlayerImpl unregisterCallback()");
        mTvPlayerCallbacks.remove(callback);
        Log.i("swidebug", "< ExoPlayerImpl unregisterCallback()");
    }
    /* Overrides from TvPlayer end*/
}
