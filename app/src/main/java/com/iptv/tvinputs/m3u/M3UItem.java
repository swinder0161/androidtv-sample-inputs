package com.iptv.tvinputs.m3u;

public class M3UItem {
    /**
     * The channel name.
     */
    private String mChannelName;
    private String mChannelId;
    /**
     * The stream duration time, it's unit is second.
     */
    private int mDuration;
    /**
     * The stream url.
     */
    private String mStreamURL;
    /**
     * The url to the logo icon.
     */
    private String mLogoURL;
    /**
     * The group name.
     */
    private String mGroupTitle;
    /**
     * The media type. It can be one of the following types: avi, asf, wmv, mp4,
     * mpeg, mpeg1, mpeg2, ts, mp2t, mp2p, mov, mkv, 3gp, flv, aac, ac3, mp3,
     * ogg, wma.
     */
    private String mType;
    /**
     * The DLNA profile. It can be set as none, mpeg_ps_pal, mpeg_ps_pal_ac3,
     * mpeg_ps_ntsc, mpeg_ps_ntsc_ac3, mpeg1, mpeg_ts_sd, mpeg_ts_hd, avchd,
     * wmv_med_base, wmv_med_full, wmv_med_pro, wmv_high_full, wmv_high_pro,
     * asf_mpeg4_sp, asf_mpeg4_asp_l4, asf_mpeg4_asp_l5, asf_vc1_l1,
     * mp4_avc_sd_mp3, mp4_avc_sd_ac3, mp4_avc_hd_ac3, mp4_avc_sd_aac,
     * mpeg_ts_hd_mp3, mpeg_ts_hd_ac3, mpeg_ts_mpeg4_asp_mp3,
     * mpeg_ts_mpeg4_asp_ac3, avi, divx5, mp3, ac3, wma_base, wma_full, wma_pro.
     */
    private String mDLNAExtras;

    private String mLicenseType;
    private String mLicenseKeyUrl;
    /**
     * The media plugin (handler).
     */
    private String mPlugin;

    public void setChannelName(String name) {
        mChannelName = name;
    }

    public String getChannelName() {
        return mChannelName;
    }

    public void setDuration(int duration) {
        mDuration = duration;
    }

    public int getDuration() {
        return mDuration;
    }

    public void setStreamURL(String url) {
        mStreamURL = url;
    }

    public String getStreamURL() {
        return mStreamURL;
    }

    public void setLogoURL(String url) {
        mLogoURL = url;
    }

    public String getLogoURL() {
        return mLogoURL;
    }

    public void setChannelID(String id) {
        mChannelId = id;
    }

    public String getChannelID() {
        return mChannelId;
    }

    public void setGroupTitle(String title) {
        mGroupTitle = title;
    }

    public String getGroupTitle() {
        return mGroupTitle;
    }

    public void setType(String type) {
        mType = type;
    }

    public String getType() {
        return mType;
    }

    public void setDLNAExtras(String profile) {
        mDLNAExtras = profile;
    }

    public String getDLNAExtras() {
        return mDLNAExtras;
    }

    public void setPlugin(String plugin) {
        mPlugin = plugin;
    }

    public String getPlugin() {
        return mPlugin;
    }

    public void setLicenseType(String licType) {
        if (licType != null)
            mLicenseType = licType;
    }

    public String getLicenseType() {
        return mLicenseType;
    }

    public void setLicenseKeyUrl(String licKeyUrl) {
        if (licKeyUrl != null)
            mLicenseKeyUrl = licKeyUrl;
    }

    public String getLicenseKeyUrl() {
        return mLicenseKeyUrl;
    }

    @Override
    public String toString() {
        return "[M3UItem@" + Integer.toHexString(hashCode()) + "]: Channel Name: " + mChannelName +
                ", Channel ID: " + mChannelId + ", Duration: " + mDuration + ", Stream URL: " +
                mStreamURL + ", Group: " + mGroupTitle + ", Logo: " + mLogoURL + ", Type: " + mType +
                ", DLNA Extras: " + mDLNAExtras + ", Plugin: " + mPlugin + ", License Type: " +
                mLicenseType + ", License Key Url: " + mLicenseKeyUrl;
    }
}