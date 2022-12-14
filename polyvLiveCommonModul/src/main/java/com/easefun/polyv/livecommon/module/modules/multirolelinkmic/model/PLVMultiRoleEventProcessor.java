package com.easefun.polyv.livecommon.module.modules.multirolelinkmic.model;

import androidx.annotation.Nullable;
import android.text.TextUtils;

import com.easefun.polyv.livecommon.module.data.IPLVLiveRoomDataManager;
import com.plv.foundationsdk.log.PLVCommonLog;
import com.plv.foundationsdk.utils.PLVGsonUtil;
import com.plv.foundationsdk.utils.PLVSugarUtil;
import com.plv.linkmic.model.PLVMicphoneStatus;
import com.plv.linkmic.model.PLVNetworkStatusVO;
import com.plv.livescenes.document.event.PLVSwitchRoomEvent;
import com.plv.livescenes.linkmic.IPLVLinkMicManager;
import com.plv.livescenes.linkmic.listener.PLVLinkMicEventListener;
import com.plv.livescenes.socket.PLVSocketWrapper;
import com.plv.livescenes.streamer.linkmic.IPLVLinkMicEventSender;
import com.plv.socket.event.PLVEventConstant;
import com.plv.socket.event.PLVEventHelper;
import com.plv.socket.event.chat.PLVOTeacherInfoEvent;
import com.plv.socket.event.linkmic.PLVJoinLeaveSEvent;
import com.plv.socket.event.linkmic.PLVJoinResponseSEvent;
import com.plv.socket.event.linkmic.PLVOpenMicrophoneEvent;
import com.plv.socket.event.linkmic.PLVRemoveMicSiteEvent;
import com.plv.socket.event.linkmic.PLVTeacherSetPermissionEvent;
import com.plv.socket.event.linkmic.PLVUpdateMicSiteEvent;
import com.plv.socket.event.login.PLVLoginEvent;
import com.plv.socket.event.ppt.PLVFinishClassEvent;
import com.plv.socket.event.ppt.PLVOnSliceIDEvent;
import com.plv.socket.event.ppt.PLVOnSliceStartEvent;
import com.plv.socket.event.seminar.PLVDiscussAckResult;
import com.plv.socket.event.seminar.PLVHostSendToAllGroupEvent;
import com.plv.socket.event.seminar.PLVJoinDiscussEvent;
import com.plv.socket.event.seminar.PLVJoinSuccessEvent;
import com.plv.socket.event.seminar.PLVLeaveDiscussEvent;
import com.plv.socket.impl.PLVSocketMessageObserver;
import com.plv.socket.user.PLVClassStatusBean;
import com.plv.socket.user.PLVSocketUserConstant;

import java.util.Map;

import io.socket.client.Socket;

import static com.plv.foundationsdk.utils.PLVSugarUtil.nullable;

/**
 * socket???rtc???????????????
 */
public class PLVMultiRoleEventProcessor {
    // <editor-fold defaultstate="collapsed" desc="??????">
    private static final String TAG = "PLVMultiRoleEventProcessor";
    private IPLVLiveRoomDataManager liveRoomDataManager;
    //???????????????
    @Nullable
    private IPLVLinkMicManager linkMicManager;

    private String myLinkMicId;
    private boolean isTeacherType;

    private boolean sendJoinDiscussMsgFlag;
    private boolean isInClassStatusInDiscuss;
    private String groupLeaderId;
    private String groupId;

    //listener
    private OnEventProcessorListener onEventProcessorListener;
    private PLVSocketMessageObserver.OnMessageListener onMessageListener;
    private PLVLinkMicEventListener linkMicEventListener;
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="?????????">
    public PLVMultiRoleEventProcessor(IPLVLiveRoomDataManager liveRoomDataManager) {
        this.liveRoomDataManager = liveRoomDataManager;
        String userType = liveRoomDataManager.getConfig().getUser().getViewerType();
        this.isTeacherType = PLVSocketUserConstant.USERTYPE_TEACHER.equals(userType);
        observeSocketEvent();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="??????API">
    public void setOnEventProcessorListener(OnEventProcessorListener listener) {
        this.onEventProcessorListener = listener;
    }

    public void setMyLinkMicId(String myLinkMicId) {
        this.myLinkMicId = myLinkMicId;
    }

    public void observeRTCEvent(IPLVLinkMicManager linkMicManager) {
        this.linkMicManager = linkMicManager;
        observeRTCEventInner();
    }

    public void destroy() {
        PLVSocketWrapper.getInstance().getSocketObserver().removeOnMessageListener(onMessageListener);
        if (linkMicManager != null) {
            linkMicManager.removeEventHandler(linkMicEventListener);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????????">
    private boolean isMyLinkMicId(String linkMicId) {
        return linkMicId != null && linkMicId.equals(myLinkMicId);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="???????????? - ??????socket??????">
    private void observeSocketEvent() {
        onMessageListener = new PLVSocketMessageObserver.OnMessageListener() {
            @Override
            public void onMessage(String listenEvent, String event, String message) {
                switch (event) {
                    //??????????????????
                    case PLVLoginEvent.EVENT:
                        PLVLoginEvent loginEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVLoginEvent.class);
                        acceptLoginEvent(loginEvent);
                        break;
                    //sliceId??????
                    case PLVOnSliceIDEvent.EVENT:
                        PLVOnSliceIDEvent onSliceIDEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVOnSliceIDEvent.class);
                        acceptOnSliceIDEvent(onSliceIDEvent);
                        break;
                    //??????????????????
                    case PLVEventConstant.Class.O_TEACHER_INFO:
                        PLVOTeacherInfoEvent oTeacherInfoEvent = PLVEventHelper.toMessageEventModel(message, PLVOTeacherInfoEvent.class);
                        acceptOTeacherInfoEvent(oTeacherInfoEvent);
                        break;
                    //????????????????????????
                    case PLVEventConstant.LinkMic.TEACHER_SET_PERMISSION:
                        PLVTeacherSetPermissionEvent teacherSetPermissionEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVTeacherSetPermissionEvent.class);
                        acceptTeacherSetPermissionEvent(teacherSetPermissionEvent);
                        break;
                    //??????????????????????????????
                    case PLVEventConstant.LinkMic.JOIN_RESPONSE_EVENT:
                        PLVJoinResponseSEvent joinResponseSEvent = PLVGsonUtil.fromJson(PLVJoinResponseSEvent.class, message);
                        acceptJoinResponseSEvent(joinResponseSEvent);
                        break;
                    //????????????????????????
                    case PLVEventConstant.LinkMic.JOIN_LEAVE_EVENT:
                        PLVJoinLeaveSEvent joinLeaveSEvent = PLVGsonUtil.fromJson(PLVJoinLeaveSEvent.class, message);
                        acceptJoinLeaveSEvent(joinLeaveSEvent);
                        break;
                    //???????????????/?????????????????????????????????????????????
                    case PLVEventConstant.LinkMic.EVENT_OPEN_MICROPHONE:
                        PLVMicphoneStatus micPhoneStatus = PLVGsonUtil.fromJson(PLVMicphoneStatus.class, message);
                        acceptMicphoneStatusEvent(micPhoneStatus);
                        break;
                    //??????????????????
                    case PLVEventConstant.Ppt.ON_SLICE_START_EVENT:
                        PLVOnSliceStartEvent onSliceStartEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVOnSliceStartEvent.class);
                        acceptOnSliceStartEvent(onSliceStartEvent);
                        break;
                    //????????????
                    case PLVEventConstant.Class.FINISH_CLASS:
                        PLVFinishClassEvent finishClassEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVFinishClassEvent.class);
                        acceptFinishClassEvent(finishClassEvent);
                        break;
                    //????????????
                    case PLVEventConstant.Seminar.EVENT_JOIN_DISCUSS:
                        PLVJoinDiscussEvent joinDiscussEvent = PLVGsonUtil.fromJson(PLVJoinDiscussEvent.class, message);
                        acceptJoinDiscussEvent(joinDiscussEvent);
                        break;
                    //????????????
                    case PLVEventConstant.Seminar.EVENT_LEAVE_DISCUSS:
                        PLVLeaveDiscussEvent leaveDiscussEvent = PLVGsonUtil.fromJson(PLVLeaveDiscussEvent.class, message);
                        acceptLeaveDiscussEvent(leaveDiscussEvent);
                        break;
                    //??????????????????
                    case PLVEventConstant.Seminar.EVENT_HOST_JOIN:
                        acceptHostJoinEvent();
                        break;
                    //??????????????????
                    case PLVEventConstant.Seminar.EVENT_HOST_LEAVE:
                        acceptHostLeaveEvent();
                        break;
                    //????????????????????????
                    case PLVEventConstant.Seminar.EVENT_HOST_SEND_TO_ALL_GROUP:
                        PLVHostSendToAllGroupEvent hostSendToAllGroupEvent = PLVGsonUtil.fromJson(PLVHostSendToAllGroupEvent.class, message);
                        acceptHostSendToAllGroupEvent(hostSendToAllGroupEvent);
                        break;
                    //??????????????????
                    case PLVEventConstant.Seminar.EVENT_GROUP_REQUEST_HELP:
                        acceptRequestHelp();
                        break;
                    //??????????????????(??????????????????????????????)
                    case PLVEventConstant.Seminar.EVENT_CANCEL_HELP:
                        acceptCancelHelp();
                        break;
                }
                // ???????????????????????????
                acceptUpdateLinkMicZoom(listenEvent, event, message);
                // ????????????????????????????????????
                acceptRemoveLinkMicZoom(listenEvent, event, message);
            }
        };
        PLVSocketWrapper.getInstance().getSocketObserver().addOnMessageListener(onMessageListener,
                PLVEventConstant.LinkMic.JOIN_REQUEST_EVENT,
                PLVEventConstant.LinkMic.JOIN_RESPONSE_EVENT,
                PLVEventConstant.LinkMic.JOIN_SUCCESS_EVENT,
                PLVEventConstant.LinkMic.JOIN_LEAVE_EVENT,
                PLVEventConstant.LinkMic.JOIN_ANSWER_EVENT,
                PLVEventConstant.Class.SE_SWITCH_MESSAGE,
                PLVEventConstant.Seminar.SEMINAR_EVENT,
                Socket.EVENT_MESSAGE,
                PLVEventConstant.LinkMic.EVENT_CHANGE_MIC_SITE);
    }

    private void acceptLoginEvent(PLVLoginEvent loginEvent) {
        if (onEventProcessorListener != null) {
            onEventProcessorListener.onUserLogin(loginEvent);
        }
    }

    private void acceptOnSliceIDEvent(PLVOnSliceIDEvent onSliceIDEvent) {
        if (onSliceIDEvent != null && onSliceIDEvent.getData() != null) {
            PLVClassStatusBean classStatusBean = onSliceIDEvent.getClassStatus();
            if (classStatusBean == null || !classStatusBean.isVoice()) {
                if (!isTeacherType) {
                    if (onEventProcessorListener != null) {
                        onEventProcessorListener.onAcceptMyJoinLeave(false);
                    }
                }
            }
            if (sendJoinDiscussMsgFlag && groupId != null && groupId.equals(onSliceIDEvent.getGroupId())) {
                groupLeaderId = onSliceIDEvent.getLeader();
            }

            final Map<String, PLVUpdateMicSiteEvent> updateMicSiteEventMap = onSliceIDEvent.getData().getParsedMicSite();
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onChangeLinkMicZoom(updateMicSiteEventMap);
            }
        }
    }

    private void acceptOTeacherInfoEvent(PLVOTeacherInfoEvent oTeacherInfoEvent) {
        if (oTeacherInfoEvent != null && oTeacherInfoEvent.getData() != null) {
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onTeacherInfo(oTeacherInfoEvent.getData().getNick());
            }
        }
    }

    private void acceptTeacherSetPermissionEvent(PLVTeacherSetPermissionEvent teacherSetPermissionEvent) {
        if (teacherSetPermissionEvent != null) {
            String type = teacherSetPermissionEvent.getType();
            String status = teacherSetPermissionEvent.getStatus();
            String userId = teacherSetPermissionEvent.getUserId();
            if (userId != null && userId.equals(liveRoomDataManager.getConfig().getUser().getViewerId())) {
                final boolean isZeroStatus = PLVTeacherSetPermissionEvent.STATUS_ZERO.equals(status);
                if (PLVTeacherSetPermissionEvent.TYPE_VIDEO.equals(type)) {
                    if (onEventProcessorListener != null) {
                        onEventProcessorListener.onTeacherMuteMyMedia(true, isZeroStatus);
                    }
                } else if (PLVTeacherSetPermissionEvent.TYPE_AUDIO.equals(type)) {
                    if (onEventProcessorListener != null) {
                        onEventProcessorListener.onTeacherMuteMyMedia(false, isZeroStatus);
                    }
                } else if (PLVTeacherSetPermissionEvent.TYPE_VOICE.equals(type)) {
                    if (!isZeroStatus && groupId != null && groupId.equals(teacherSetPermissionEvent.getRoomId())) {
                        if (sendJoinDiscussMsgFlag) {
                            isInClassStatusInDiscuss = true;
                        }
                        if (onEventProcessorListener != null) {
                            onEventProcessorListener.onResponseJoinForDiscuss();
                        }
                    }
                }
            }
        }
    }

    private void acceptJoinResponseSEvent(PLVJoinResponseSEvent joinResponseSEvent) {
        if (joinResponseSEvent != null) {
            if (!TextUtils.isEmpty(groupId) && !groupId.equals(joinResponseSEvent.getRoomId())) {
                return;
            }
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onResponseJoin(joinResponseSEvent.isNeedAnswer());
            }
        }
    }

    private void acceptJoinLeaveSEvent(PLVJoinLeaveSEvent joinLeaveSEvent) {
        if (joinLeaveSEvent != null && joinLeaveSEvent.getUser() != null) {
            if (isMyLinkMicId(joinLeaveSEvent.getUser().getUserId())) {
                //?????????????????????????????????????????????teacherSetPermission??????
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onAcceptMyJoinLeave(true);
                }
            }
        }
    }

    private void acceptMicphoneStatusEvent(PLVMicphoneStatus micPhoneStatus) {
        if (micPhoneStatus != null) {
            String linkMicState = micPhoneStatus.getStatus();
            String userId = micPhoneStatus.getUserId();
            //???userId????????????????????????????????????????????????????????????????????????????????????????????????
            boolean isTeacherOpenOrCloseLinkMic = TextUtils.isEmpty(userId);
            if (!isTeacherOpenOrCloseLinkMic
                    && isMyLinkMicId(userId)) {
                //???????????????
                if (PLVOpenMicrophoneEvent.STATUS_CLOSE.equals(linkMicState)) {
                    if (onEventProcessorListener != null) {
                        onEventProcessorListener.onAcceptMyJoinLeave(true);
                    }
                }
            }
        }
    }

    private void acceptOnSliceStartEvent(PLVOnSliceStartEvent onSliceStartEvent) {
        if (onSliceStartEvent != null) {
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onSliceStart(onSliceStartEvent);
            }
        }
    }

    private void acceptFinishClassEvent(PLVFinishClassEvent finishClassEvent) {
        if (!isTeacherType) {
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onAcceptMyJoinLeave(false);
            }
        }
    }

    private void acceptJoinDiscussEvent(final PLVJoinDiscussEvent joinDiscussEvent) {
        if (joinDiscussEvent == null) {
            return;
        }
        groupId = joinDiscussEvent.getGroupId();
        sendJoinDiscussMsgFlag = true;
        isInClassStatusInDiscuss = false;
        groupLeaderId = null;
        PLVSocketWrapper.getInstance().emit(PLVEventConstant.Seminar.SEMINAR_EVENT, PLVGsonUtil.toJson(new PLVJoinDiscussEvent()), new IPLVLinkMicEventSender.PLVSMainCallAck() {
            @Override
            public void onCall(Object... args) {
                sendJoinDiscussMsgFlag = false;
                if (args != null && args.length != 0 && args[0] != null) {
                    PLVDiscussAckResult simpleAckResult = PLVGsonUtil.fromJson(PLVDiscussAckResult.class, args[0].toString());
                    if (simpleAckResult != null && simpleAckResult.isSuccess()) {
                        PLVSwitchRoomEvent switchRoomEvent = PLVSwitchRoomEvent.fromDataBean(simpleAckResult.getData());
                        if (onEventProcessorListener != null) {
                            onEventProcessorListener.onJoinDiscuss(joinDiscussEvent.getGroupId(), isInClassStatusInDiscuss, groupLeaderId, switchRoomEvent);
                        }
                        PLVSocketWrapper.getInstance().emit(PLVEventConstant.Seminar.EVENT_JOIN_SUCCESS, PLVGsonUtil.toJson(new PLVJoinSuccessEvent()), null);
                    }
                }
            }
        });
    }

    private void acceptLeaveDiscussEvent(PLVLeaveDiscussEvent leaveDiscussEvent) {
        groupId = null;
        PLVSocketWrapper.getInstance().emit(PLVEventConstant.Seminar.SEMINAR_EVENT, PLVGsonUtil.toJson(new PLVLeaveDiscussEvent()), new IPLVLinkMicEventSender.PLVSMainCallAck() {
            @Override
            public void onCall(Object... args) {
                if (args != null && args.length != 0 && args[0] != null) {
                    final PLVDiscussAckResult simpleAckResult = PLVGsonUtil.fromJson(PLVDiscussAckResult.class, args[0].toString());
                    if (simpleAckResult != null && simpleAckResult.isSuccess()) {
                        PLVSwitchRoomEvent switchRoomEvent = PLVSwitchRoomEvent.fromDataBean(simpleAckResult.getData());
                        if (onEventProcessorListener != null) {
                            onEventProcessorListener.onLeaveDiscuss(switchRoomEvent);
                        }
                    }

                    final Map<String, PLVUpdateMicSiteEvent> updateMicSiteEventMap = nullable(new PLVSugarUtil.Supplier<Map<String, PLVUpdateMicSiteEvent>>() {
                        @Override
                        public Map<String, PLVUpdateMicSiteEvent> get() {
                            return simpleAckResult.getData().getRoomsStatus().getParsedMicSite();
                        }
                    });
                    if (onEventProcessorListener != null) {
                        onEventProcessorListener.onChangeLinkMicZoom(updateMicSiteEventMap);
                    }
                }
            }
        });
    }

    private void acceptHostJoinEvent() {
        if (onEventProcessorListener != null) {
            onEventProcessorListener.onTeacherJoinDiscuss(true);
        }
    }

    private void acceptHostLeaveEvent() {
        if (onEventProcessorListener != null) {
            onEventProcessorListener.onTeacherJoinDiscuss(false);
        }
    }

    private void acceptHostSendToAllGroupEvent(PLVHostSendToAllGroupEvent hostSendToAllGroupEvent) {
        if (hostSendToAllGroupEvent != null) {
            if (onEventProcessorListener != null) {
                onEventProcessorListener.onTeacherSendBroadcast(hostSendToAllGroupEvent.getContent());
            }
        }
    }

    private void acceptRequestHelp() {
        if (onEventProcessorListener != null) {
            onEventProcessorListener.onLeaderRequestHelp();
        }
    }

    private void acceptCancelHelp() {
        if (onEventProcessorListener != null) {
            onEventProcessorListener.onLeaderCancelHelp();
        }
    }

    private void acceptUpdateLinkMicZoom(String listenEvent, String event, String message) {
        if (PLVUpdateMicSiteEvent.SOCKET_EVENT_TYPE.equals(listenEvent)
                && PLVUpdateMicSiteEvent.EVENT_NAME.equals(event)
                && onEventProcessorListener != null) {
            PLVUpdateMicSiteEvent updateMicSiteEvent = PLVUpdateMicSiteEvent.fromJson(message);
            onEventProcessorListener.onUpdateLinkMicZoom(updateMicSiteEvent);
        }
    }

    private void acceptRemoveLinkMicZoom(String listenEvent, String event, String message) {
        if (PLVRemoveMicSiteEvent.SOCKET_EVENT_TYPE.equals(listenEvent)
                && PLVRemoveMicSiteEvent.EVENT_NAME.equals(event)
                && onEventProcessorListener != null) {
            PLVRemoveMicSiteEvent removeMicSiteEvent = PLVRemoveMicSiteEvent.fromJson(message);
            onEventProcessorListener.onRemoveLinkMicZoom(removeMicSiteEvent);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="???????????? - ??????rtc??????">
    private void observeRTCEventInner() {
        linkMicEventListener = new PLVLinkMicEventListener() {
            @Override
            public void onJoinChannelSuccess(String uid) {
                PLVCommonLog.d(TAG, "onJoinChannelSuccess, uid=" + uid);
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onJoinChannelSuccess();
                }
            }

            @Override
            public void onLeaveChannel() {
                super.onLeaveChannel();
                PLVCommonLog.d(TAG, "onLeaveChannel");
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onLeaveChannel();
                }
            }

            @Override
            public void onNetworkQuality(final int quality) {
                super.onNetworkQuality(quality);
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onNetworkQuality(quality);
                }
            }

            @Override
            public void onUpstreamNetworkStatus(PLVNetworkStatusVO networkStatusVO) {
                super.onUpstreamNetworkStatus(networkStatusVO);
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onUpstreamNetworkStatus(networkStatusVO);
                }
            }

            @Override
            public void onRemoteNetworkStatus(PLVNetworkStatusVO networkStatusVO) {
                super.onRemoteNetworkStatus(networkStatusVO);
                if (onEventProcessorListener != null) {
                    onEventProcessorListener.onRemoteNetworkStatus(networkStatusVO);
                }
            }
        };
        if (linkMicManager != null) {
            linkMicManager.addEventHandler(linkMicEventListener);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????? - ?????????">
    public interface OnEventProcessorListener {

        /**
         * ????????????/???????????????
         */
        void onAcceptMyJoinLeave(boolean isByTeacherControl);

        /**
         * ??????????????????????????????
         */
        void onJoinChannelSuccess();

        /**
         * ???????????????????????????
         */
        void onLeaveChannel();

        /**
         * ??????????????????????????????
         */
        void onTeacherMuteMyMedia(boolean isVideoType, boolean isMute);

        /**
         * ??????????????????
         */
        void onResponseJoin(boolean isNeedAnswer);

        /**
         * ????????????
         */
        void onUserLogin(PLVLoginEvent loginEvent);

        /**
         * ??????onSliceStart??????
         */
        void onSliceStart(PLVOnSliceStartEvent onSliceStartEvent);

        /**
         * ??????????????????
         *
         * @param quality ??????????????????
         */
        void onNetworkQuality(int quality);

        /**
         * ????????????????????????
         *
         * @param networkStatusVO
         */
        void onUpstreamNetworkStatus(PLVNetworkStatusVO networkStatusVO);

        /**
         * ??????????????????????????????
         *
         * @param networkStatusVO
         */
        void onRemoteNetworkStatus(PLVNetworkStatusVO networkStatusVO);

        /**
         * ????????????
         */
        void onTeacherInfo(String nick);

        /**
         * ???????????????????????????????????????
         */
        void onResponseJoinForDiscuss();

        /**
         * ????????????
         *
         * @param groupId         ??????Id
         * @param isInClass       ????????????
         * @param leaderId        ??????Id
         * @param switchRoomEvent ??????????????????
         */
        void onJoinDiscuss(String groupId, boolean isInClass, @Nullable String leaderId, PLVSwitchRoomEvent switchRoomEvent);

        /**
         * ????????????
         *
         * @param switchRoomEvent ??????????????????
         */
        void onLeaveDiscuss(PLVSwitchRoomEvent switchRoomEvent);

        /**
         * ????????????????????????
         *
         * @param isJoin true????????????false?????????
         */
        void onTeacherJoinDiscuss(boolean isJoin);

        /**
         * ????????????????????????
         *
         * @param content ????????????
         */
        void onTeacherSendBroadcast(String content);

        /**
         * ??????????????????
         */
        void onLeaderRequestHelp();

        /**
         * ??????????????????
         */
        void onLeaderCancelHelp();

        /**
         * ???????????????????????????
         */
        void onUpdateLinkMicZoom(PLVUpdateMicSiteEvent updateMicSiteEvent);

        /**
         * ????????????????????????????????????
         */
        void onRemoveLinkMicZoom(PLVRemoveMicSiteEvent removeMicSiteEvent);

        /**
         * ???????????????????????????????????????
         *
         * @param updateMicSiteEventMap Key:??????id???Value:??????
         */
        void onChangeLinkMicZoom(@Nullable Map<String, PLVUpdateMicSiteEvent> updateMicSiteEventMap);

    }
    // </editor-fold>
}
