package com.iptv.tvinputs.player;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.dash.DashChunkSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private ExoPlayer mPlayer;
    private PlaybackParams mPlaybackParams;
    private final CopyOnWriteArrayList<Listener> mListeners;
    private final List<TvPlayer.Callback> mTvPlayerCallbacks;
    private CaptionListener mCaptionListener;
    private int mPlaybackState;
    private boolean mPlayWhenReady;
    private Surface mSurface;

    public ExoPlayerImpl(Context context, int contentType, String videoUrl, String licenseUrl) {
        Log.i("swidebug", "> ExoPlayerImpl ExoPlayerImpl() contentType: " + contentType +
                ", videoUrl: " + videoUrl + ", licenseUrl" + licenseUrl);
        mContext = context;
        mPlayer = null;
        mPlaybackParams = null;
        mListeners = new CopyOnWriteArrayList<>();
        mTvPlayerCallbacks = new CopyOnWriteArrayList<>();
        mCaptionListener = null;
        mPlaybackState = ExoPlayer.STATE_IDLE;
        mPlayWhenReady = false;
        mSurface = null;

        MediaSource mediaSource = getMediaSource(contentType, videoUrl, licenseUrl);

        if (null != mediaSource) {
            mPlayer = new ExoPlayer.Builder(mContext)
                    .setSeekForwardIncrementMs(10000)
                    .setSeekBackIncrementMs(10000)
                    .build();
            mPlayer.addListener(this);
            mPlayer.setMediaSource(mediaSource, true);
        }
        Log.i("swidebug", "< ExoPlayerImpl ExoPlayerImpl()");
    }

    public void prepare() {
        Log.i("swidebug", "> ExoPlayerImpl prepare()");
        if (mPlaybackState != ExoPlayer.STATE_IDLE)
            mPlayer.stop();
        //mPlayer.prepare();
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
        int cnt = 0;
        TrackGroup tg = getTrackGroup(trackType);
        if (null != tg)
            cnt = tg.length;
        Log.i("swidebug", "> ExoPlayerImpl getTrackCount() cnt: " + cnt);
        return cnt;
    }

    public Format getTrackFormat(int trackType, int index) {
        Log.i("swidebug", "> ExoPlayerImpl getTrackFormat() type: " + getTrackType(trackType) +
                "index: " + index);
        Format fmt = null;
        TrackGroup tg = getTrackGroup(trackType);
        if (null != tg) {
            fmt = tg.getFormat(index);
        }
        Log.i("swidebug", "< ExoPlayerImpl getTrackFormat() fmt: " + fmt);
        return fmt;
    }

    public int getSelectedTrack(int trackType) {
        Log.i("swidebug", "> ExoPlayerImpl getSelectedTrack() type: " + getTrackType(trackType));
        int index = -1;
        Tracks ts = mPlayer.getCurrentTracks();
        for (Tracks.Group g : ts.getGroups()) {
            if (g.getType() != trackType)
                continue;
            Log.i("swidebug", ". ExoPlayerImpl getSelectedTrack() group: " +
                    g + ", cnt: " + g.length + ", type: " + getTrackType(g.getType()));
            Format tfmt = null;
            if (trackType == TRACK_TYPE_AUDIO) {
                tfmt = mPlayer.getAudioFormat();
            } else if (trackType == TRACK_TYPE_VIDEO) {
                tfmt = mPlayer.getVideoFormat();
            } else if (trackType == TRACK_TYPE_TEXT) {
                Log.i("swidebug", ". ExoPlayerImpl getSelectedTrack() cues: " + mPlayer.getCurrentCues());
            }
            if (null == tfmt)
                break;
            Log.i("swidebug", ".ExoPlayerImpl getSelectedTrack() tfmt: " + tfmt);
            for (int i=0; i<g.length; i++) {
                Format fmt = g.getTrackFormat(i);
                Log.i("swidebug", ". ExoPlayerImpl getSelectedTrack() fmt[" + i +"]: " + fmt);
                if (fmt.id == tfmt.id) {
                    index = i;
                    Log.i("swidebug", ". ExoPlayerImpl getSelectedTrack() found match: " + i);
                    break;
                }
            }
        }
        Log.i("swidebug", "< ExoPlayerImpl getSelectedTrack() index: " + index);
        return index;
    }

    public void setSelectedTrack(int trackType, int index) {
        Log.i("swidebug", "> ExoPlayerImpl setSelectedTrack() type: " + getTrackType(trackType) +
                ", index: " + index);
        //TODO
        if (trackType == TRACK_TYPE_TEXT) {
            return;
        }
        TrackSelectionParameters.Builder builder = mPlayer.getTrackSelectionParameters().buildUpon();
        TrackSelectionOverride override = new TrackSelectionOverride(getTrackGroup(trackType), index);

        builder.addOverride(override);
        mPlayer.setTrackSelectionParameters(builder.build());
        if (trackType == TRACK_TYPE_TEXT && index < 0 && mCaptionListener != null) {
            mCaptionListener.onCues(Collections.<Cue>emptyList());
        }
        Log.i("swidebug", "< ExoPlayerImpl setSelectedTrack()");
    }

    @TargetApi(Build.VERSION_CODES.M)
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
                mediaSource = null;
            } break;
            case TvContractUtils.SOURCE_TYPE_HLS: {
                mediaSource = null;
            } break;
            case TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE: {
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

    private TrackGroup getTrackGroup(int trackType) {
        Log.i("swidebug", "> ExoPlayerImpl getTrackGroup() type: " + getTrackType(trackType));
        TrackGroup tg = null;
        Tracks tracks = mPlayer.getCurrentTracks();
        if (null != tracks) {
            for (Tracks.Group g : tracks.getGroups()) {
                Log.i("swidebug", ". ExoPlayerImpl getTrackGroup() group: " + g +
                        ", cnt: " + g.length + ", type: " + getTrackType(g.getType()));
                if (g.getType() == trackType) {
                    tg = g.getMediaTrackGroup();
                    break;
                }
            }
        }
        Log.i("swidebug", "< ExoPlayerImpl getTrackGroup()");
        return tg;
    }

    private void maybeReportPlayerState() {
        Log.i("swidebug", "> ExoPlayerImpl maybeReportPlayerState()");
        boolean playWhenReady = mPlayer.getPlayWhenReady();
        int playbackState = mPlayer.getPlaybackState();
        Log.i("swidebug", ". ExoPlayerImpl maybeReportPlayerState() playWhenReady: " + playWhenReady +
                ", playbackState: " + playbackState);
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
        Log.i("swidebug", "> ExoPlayerImpl onPlaybackStateChanged() playbackState: " + playbackState);
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
    public void onPlayerError(PlaybackException error) {
        Player.Listener.super.onPlayerError(error);
        Log.i("swidebug", "> ExoPlayerImpl onPlayerError() error: " + error.getMessage());
        for (Callback tvCallback : mTvPlayerCallbacks) {
            tvCallback.onError(error);
        }
        for (Listener listener : mListeners) {
            listener.onError(error);
        }
        maybeReportPlayerState();
        Log.i("swidebug", "< ExoPlayerImpl onPlayerError()");
    }

    @Override
    public void onCues(CueGroup cueGroup) {
        Player.Listener.super.onCues(cueGroup);
        Log.i("swidebug", "> ExoPlayerImpl onCues() cueGroup: " + cueGroup);
        if (mCaptionListener != null && getSelectedTrack(TRACK_TYPE_TEXT) != TRACK_DISABLED) {
            mCaptionListener.onCues(cueGroup.cues);
        }
        Log.i("swidebug", "< ExoPlayerImpl onCues()");
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        Player.Listener.super.onVideoSizeChanged(videoSize);
        Log.i("swidebug", "> ExoPlayerImpl onVideoSizeChanged() videoSize: " + videoSize.width + " X " + videoSize.height);
        for (Listener listener : mListeners) {
            listener.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees,
                    videoSize.pixelWidthHeightRatio);
        }
        Log.i("swidebug", "< ExoPlayerImpl onVideoSizeChanged()");
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
        mSurface = surface;
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
