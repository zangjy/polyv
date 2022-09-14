package com.easefun.polyv.livecloudclass;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.easefun.polyv.livecloudclass.scenes.PLVLCCloudClassActivity;
import com.easefun.polyv.livecommon.module.config.PLVLiveChannelConfigFiller;
import com.easefun.polyv.livecommon.module.config.PLVLiveScene;
import com.easefun.polyv.livecommon.module.utils.result.PLVLaunchResult;
import com.plv.foundationsdk.utils.PLVUtils;
import com.plv.livescenes.config.PLVLiveChannelType;
import com.plv.livescenes.feature.login.IPLVSceneLoginManager;
import com.plv.livescenes.feature.login.PLVLiveLoginResult;
import com.plv.livescenes.feature.login.PLVPlaybackLoginResult;
import com.plv.livescenes.feature.login.PLVSceneLoginManager;
import com.plv.livescenes.playback.video.PLVPlaybackListType;
import com.plv.thirdpart.blankj.utilcode.util.StringUtils;

public class SimpleJumpActivity extends Activity {
    private PLVSceneLoginManager plvSceneLoginManager;
    private static final String TYPE_KEY = "TYPE_KEY";
    public static final String TYPE_LIVE = "TYPE_LIVE";
    public static final String TYPE_PLAY_BACK = "TYPE_PLAY_BACK";
    private static final String APP_ID_KEY = "APP_ID_KEY";
    private static final String APP_SECRET_KEY = "APP_SECRET_KEY";
    private static final String USER_ID_KEY = "USER_ID_KEY";
    private static final String CHANNEL_ID_KEY = "CHANNEL_ID_KEY";
    private static final String VIDEO_ID_KEY = "VIDEO_ID_KEY";
    private static final String VIEWER_ID_KEY = "VIEWER_ID_KEY";
    private static final String VIEWER_NAME_KEY = "VIEWER_NAME_KEY";
    private static final String VIEWER_AVATAR_KEY = "VIEWER_AVATAR_KEY";
    private String viewerId;
    private String viewerName;
    private String viewerAvatar;

    /**
     * 构建一个Intent对象
     *
     * @param context      上下文
     * @param type         直播还是回放
     * @param appId        appId
     * @param appSecret    appSecret
     * @param userId       userId
     * @param channelId    房间号
     * @param videoId      回放的时候传
     * @param viewerId     用户ID，传null使用默认
     * @param viewerName   用户名称，传null使用默认
     * @param viewerAvatar 用户头像，传null使用默认，需要严格校验是否是网络路径
     * @return
     */
    public static Intent createIntent(Context context, String type, String appId, String appSecret, String userId, String channelId, String videoId, String viewerId, String viewerName, String viewerAvatar) {
        Intent intent = new Intent(context, SimpleJumpActivity.class);
        intent.putExtra(TYPE_KEY, type);
        intent.putExtra(APP_ID_KEY, appId);
        intent.putExtra(APP_SECRET_KEY, appSecret);
        intent.putExtra(USER_ID_KEY, userId);
        intent.putExtra(CHANNEL_ID_KEY, channelId);
        intent.putExtra(VIDEO_ID_KEY, videoId);
        intent.putExtra(VIEWER_ID_KEY, viewerId);
        intent.putExtra(VIEWER_NAME_KEY, viewerName);
        intent.putExtra(VIEWER_AVATAR_KEY, viewerAvatar);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_jump);
        plvSceneLoginManager = new PLVSceneLoginManager();
        Intent intent = getIntent();
        //必要参数
        String type = intent.getStringExtra(TYPE_KEY);
        String appId = intent.getStringExtra(APP_ID_KEY);
        String appSecret = intent.getStringExtra(APP_SECRET_KEY);
        String userId = intent.getStringExtra(USER_ID_KEY);
        String channelId = intent.getStringExtra(CHANNEL_ID_KEY);
        //回放的时候需要的参数
        String videoId = intent.getStringExtra(VIDEO_ID_KEY);
        //用户信息
        viewerId = intent.getStringExtra(VIEWER_ID_KEY);
        viewerName = intent.getStringExtra(VIEWER_NAME_KEY);
        viewerAvatar = intent.getStringExtra(VIEWER_AVATAR_KEY);
        //校验必要参数是否完整
        if (StringUtils.isEmpty(type) || StringUtils.isEmpty(appId) || StringUtils.isEmpty(appSecret) || StringUtils.isEmpty(userId) || StringUtils.isEmpty(channelId)) {
            Toast.makeText(this, "缺失必要参数", Toast.LENGTH_SHORT).show();
            finish();
        }
        //如果VideoId缺失则补全
        if (videoId == null) {
            videoId = "";
        }
        //用户信息如果缺失则补全
        if (viewerId == null) {
            viewerId = "" + PLVUtils.getAndroidId(this);
        }
        if (viewerName == null) {
            viewerName = "观众" + PLVUtils.getAndroidId(this);
        }
        if (viewerAvatar == null) {
            viewerAvatar = "";
        }
        //判断具体进入哪个页面
        switch (type) {
            case TYPE_LIVE:
                loginLiveNew(appId, appSecret, userId, channelId);
                break;
            case TYPE_PLAY_BACK:
                loginPlaybackNew(appId, appSecret, userId, channelId, videoId);
                break;
            default:
                Toast.makeText(this, "直播类型错误", Toast.LENGTH_SHORT).show();
                finish();
                break;
        }
    }

    /**
     * 进入直播页面
     */
    private void loginLiveNew(final String appId, final String appSecret, final String userId, final String channelId) {
        plvSceneLoginManager.loginLiveNew(appId, appSecret, userId, channelId, new IPLVSceneLoginManager.OnLoginListener<PLVLiveLoginResult>() {
            @Override
            public void onLoginSuccess(PLVLiveLoginResult plvLiveLoginResult) {
                PLVLiveChannelConfigFiller.setupAccount(userId, appId, appSecret);
                PLVLiveChannelType channelType = plvLiveLoginResult.getChannelTypeNew();
                if (PLVLiveScene.isCloudClassSceneSupportType(channelType)) {
                    PLVLaunchResult launchResult = PLVLCCloudClassActivity.launchLive(SimpleJumpActivity.this, channelId, channelType, viewerId, viewerName, viewerAvatar);
                    if (!launchResult.isSuccess()) {
                        Toast.makeText(SimpleJumpActivity.this, launchResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SimpleJumpActivity.this, com.easefun.polyv.livecommon.R.string.plv_scene_login_toast_cloudclass_no_support_type, Toast.LENGTH_SHORT).show();
                }
                finish();
            }

            @Override
            public void onLoginFailed(String s, Throwable throwable) {
                Toast.makeText(SimpleJumpActivity.this, s, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * 回放
     */
    private void loginPlaybackNew(final String appId, final String appSecret, final String userId, final String channelId, final String videoId) {
        plvSceneLoginManager.loginPlaybackNew(appId, appSecret, userId, channelId, videoId, new IPLVSceneLoginManager.OnLoginListener<PLVPlaybackLoginResult>() {
            @Override
            public void onLoginSuccess(PLVPlaybackLoginResult plvPlaybackLoginResult) {
                PLVLiveChannelConfigFiller.setupAccount(userId, appId, appSecret);
                PLVLiveChannelType channelType = plvPlaybackLoginResult.getChannelTypeNew();
                if (PLVLiveScene.isCloudClassSceneSupportType(channelType)) {
                    PLVLaunchResult plvLaunchResult = PLVLCCloudClassActivity.launchPlayback(SimpleJumpActivity.this, channelId, channelType, videoId, null, viewerId, viewerName, viewerAvatar, PLVPlaybackListType.PLAYBACK);
                    if (!plvLaunchResult.isSuccess()) {
                        Toast.makeText(SimpleJumpActivity.this, plvLaunchResult.getErrorMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
                finish();
            }

            @Override
            public void onLoginFailed(String s, Throwable throwable) {
                Toast.makeText(SimpleJumpActivity.this, s, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }
}