package com.iptv.input;

import android.app.Application;

import com.iptv.input.util.Log;

public class IPTVApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("swidebug", ". IPTVApplication onCreate()");
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.i("swidebug", ". IPTVApplication onLowMemory()");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.i("swidebug", ". IPTVApplication onTerminate()");
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.i("swidebug", ". IPTVApplication onTrimMemory()");
    }
}
