package com.sdym.polyv;


import android.app.Application;

import com.easefun.polyv.livecommon.module.config.PLVLiveSDKConfig;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PLVLiveSDKConfig.init(
                new PLVLiveSDKConfig.Parameter(this)
                        .isOpenDebugLog(true)
                        .isEnableHttpDns(false)
        );
    }
}
