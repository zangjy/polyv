package com.easefun.polyv.livecommon.module.modules.multirolelinkmic.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;

import com.easefun.polyv.livecommon.module.data.IPLVLiveRoomDataManager;
import com.easefun.polyv.livecommon.module.modules.linkmic.model.PLVLinkMicDataMapper;
import com.easefun.polyv.livecommon.module.modules.linkmic.model.PLVLinkMicItemDataBean;
import com.plv.foundationsdk.log.PLVCommonLog;
import com.plv.foundationsdk.rx.PLVRxTimer;
import com.plv.foundationsdk.utils.PLVGsonUtil;
import com.plv.linkmic.PLVLinkMicConstant;
import com.plv.linkmic.model.PLVJoinInfoEvent;
import com.plv.linkmic.model.PLVLinkMicJoinStatus;
import com.plv.linkmic.model.PLVMicphoneStatus;
import com.plv.linkmic.repository.PLVLinkMicDataRepository;
import com.plv.linkmic.repository.PLVLinkMicHttpRequestException;
import com.plv.livescenes.linkmic.IPLVLinkMicManager;
import com.plv.livescenes.linkmic.listener.PLVLinkMicEventListener;
import com.plv.livescenes.socket.PLVSocketWrapper;
import com.plv.livescenes.streamer.linkmic.PLVLinkMicEventSender;
import com.plv.socket.event.PLVEventConstant;
import com.plv.socket.event.PLVEventHelper;
import com.plv.socket.event.linkmic.PLVJoinAnswerSEvent;
import com.plv.socket.event.linkmic.PLVJoinLeaveSEvent;
import com.plv.socket.event.linkmic.PLVOpenMicrophoneEvent;
import com.plv.socket.event.ppt.PLVFinishClassEvent;
import com.plv.socket.event.ppt.PLVOnSliceIDEvent;
import com.plv.socket.impl.PLVSocketMessageObserver;
import com.plv.socket.user.PLVClassStatusBean;
import com.plv.socket.user.PLVSocketUserBean;
import com.plv.socket.user.PLVSocketUserConstant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.socket.client.Socket;

import static com.easefun.polyv.livecommon.module.modules.multirolelinkmic.model.PLVMultiRoleLinkMicConstant.DELAY_TO_GET_LINK_MIC_LIST;
import static com.easefun.polyv.livecommon.module.modules.multirolelinkmic.model.PLVMultiRoleLinkMicConstant.INTERVAL_TO_GET_LINK_MIC_LIST;

/**
 * ????????????
 */
public class PLVMultiRoleLinkMicList {
    // <editor-fold defaultstate="collapsed" desc="??????">
    private static final String TAG = "PLVMultiRoleLinkMicList";
    private IPLVLiveRoomDataManager liveRoomDataManager;
    //???????????????
    @Nullable
    private IPLVLinkMicManager linkMicManager;
    //??????????????????
    private List<PLVLinkMicItemDataBean> linkMicList = new LinkedList<>();
    //??????rtc???????????????
    private Map<String, PLVLinkMicItemDataBean> rtcJoinMap = new HashMap<>();
    //???????????????/?????????????????????
    private Map<String, Boolean> teacherScreenStreamMap = new HashMap<>();

    //????????????item
    @Nullable
    private PLVLinkMicItemDataBean myLinkMicItemBean;
    //???onSliceId?????????????????????
    private PLVClassStatusBean myClassStatusBeanOnSliceId;
    private String myLinkMicId;
    private boolean isTeacherType;
    //???????????????Id
    private String groupLeaderId;

    //disposable
    private Disposable linkMicListTimerDisposable;
    private Disposable linkMicListOnceDisposable;
    //listener
    private List<OnLinkMicListListener> onLinkMicListListeners = new ArrayList<>();
    private PLVSocketMessageObserver.OnMessageListener onMessageListener;
    private PLVLinkMicEventListener linkMicEventListener;
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="?????????">
    public PLVMultiRoleLinkMicList(IPLVLiveRoomDataManager liveRoomDataManager) {
        this.liveRoomDataManager = liveRoomDataManager;
        String userType = liveRoomDataManager.getConfig().getUser().getViewerType();
        this.isTeacherType = PLVSocketUserConstant.USERTYPE_TEACHER.equals(userType);
        observeSocketEvent();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="??????API">
    public List<PLVLinkMicItemDataBean> getData() {
        return linkMicList;
    }

    public Map<String, PLVLinkMicItemDataBean> getRtcJoinMap() {
        return rtcJoinMap;
    }

    public void notifyLeaderChanged(String groupLeaderId) {
        this.groupLeaderId = groupLeaderId;
        sortLinkMicList(groupLeaderId);
    }

    public void requestData() {
        requestLinkMicListApi();
    }

    public PLVLinkMicItemDataBean getItem(int linkMicListPos) {
        if (linkMicListPos < 0 || linkMicListPos >= linkMicList.size()) {
            return null;
        }
        return linkMicList.get(linkMicListPos);
    }

    public void disposeRequestData() {
        dispose(linkMicListTimerDisposable);
    }

    public void observeRTCEvent(IPLVLinkMicManager linkMicManager) {
        this.linkMicManager = linkMicManager;
        observeRTCEventInner();
    }

    public void addMyItemToLinkMicList(boolean curEnableLocalVideo, boolean curEnableLocalAudio) {
        addMyItemToLinkMicListInner(curEnableLocalVideo, curEnableLocalAudio);
    }

    public void removeMyItemToLinkMicList() {
        removeMyItemToLinkMicListInner();
    }

    public boolean updateLinkMicItemInfoWithRtcJoinList(PLVLinkMicItemDataBean linkMicItemDataBean, final String linkMicUid) {
        return updateLinkMicItemInfoWithRtcJoinListInner(linkMicItemDataBean, linkMicUid);
    }

    public Pair<Integer, PLVLinkMicItemDataBean> getLinkMicItemWithLinkMicId(String linkMicId) {
        return getLinkMicItemWithLinkMicIdInner(linkMicId);
    }

    public void addOnLinkMicListListener(OnLinkMicListListener listListener) {
        if (listListener != null && !onLinkMicListListeners.contains(listListener)) {
            onLinkMicListListeners.add(listListener);
        }
    }

    public void setMyLinkMicId(String myLinkMicId) {
        this.myLinkMicId = myLinkMicId;
        createMyLinkMicItem(myLinkMicId);
    }

    @Nullable
    public PLVLinkMicItemDataBean getMyLinkMicItemBean() {
        return myLinkMicItemBean;
    }

    public void destroy() {
        linkMicList.clear();
        rtcJoinMap.clear();
        cleanTeacherScreenStream();
        onLinkMicListListeners.clear();
        dispose(linkMicListTimerDisposable);
        dispose(linkMicListOnceDisposable);
        PLVSocketWrapper.getInstance().getSocketObserver().removeOnMessageListener(onMessageListener);
        if (linkMicManager != null) {
            linkMicManager.removeEventHandler(linkMicEventListener);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????????API??????">
    private void requestLinkMicListApi() {
        dispose(linkMicListTimerDisposable);
        linkMicListTimerDisposable = PLVRxTimer.timer(DELAY_TO_GET_LINK_MIC_LIST, INTERVAL_TO_GET_LINK_MIC_LIST, new Consumer<Long>() {
            @Override
            public void accept(Long aLong) throws Exception {
                acceptGetLinkMicListStatus();
            }
        });
    }

    private void requestLinkMicListApiOnce() {
        dispose(linkMicListOnceDisposable);
        linkMicListOnceDisposable = PLVRxTimer.delay(DELAY_TO_GET_LINK_MIC_LIST, new Consumer<Long>() {
            @Override
            public void accept(Long aLong) throws Exception {
                acceptGetLinkMicListStatus();
            }
        });
    }

    private void acceptGetLinkMicListStatus() {
        callbackToListener(new ListenerRunnable() {
            @Override
            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                linkMicListListener.onGetLinkMicListStatus(liveRoomDataManager.getSessionId(), new PLVLinkMicDataRepository.IPLVLinkMicDataRepoListener<PLVLinkMicJoinStatus>() {
                    @Override
                    public void onSuccess(PLVLinkMicJoinStatus data) {
                        PLVCommonLog.d(TAG, "requestLinkMicListFromServer.onSuccess->\n" + data.toString());
                        updateLinkMicListWithJoinStatus(data);
                    }

                    @Override
                    public void onFail(PLVLinkMicHttpRequestException throwable) {
                        super.onFail(throwable);
                        PLVCommonLog.exception(throwable);
                    }
                });
            }
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="?????????????????????item">
    private void addMyItemToLinkMicListInner(boolean curEnableLocalVideo, boolean curEnableLocalAudio) {
        Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(myLinkMicId);
        if (linkMicItem == null && myLinkMicItemBean != null) {
            myLinkMicItemBean.setMuteVideo(!curEnableLocalVideo);
            myLinkMicItemBean.setMuteAudio(!curEnableLocalAudio);
            myLinkMicItemBean.setStatus(PLVLinkMicItemDataBean.STATUS_RTC_JOIN);
            int addIndex = isTeacherType ? 0 : linkMicList.size();
            if (isLeaderId(myLinkMicId)) {
                addIndex = 0;
                for (int i = 0; i < linkMicList.size(); i++) {
                    if (linkMicList.get(i).isTeacher()) {
                        addIndex = i + 1;
                    }
                }
            }
            linkMicList.add(addIndex, myLinkMicItemBean);
            if (myClassStatusBeanOnSliceId != null) {
                myLinkMicItemBean.setHasPaint(myClassStatusBeanOnSliceId.hasPaint());
                myLinkMicItemBean.setCupNum(myClassStatusBeanOnSliceId.getCup());
            }
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    String userId = liveRoomDataManager.getConfig().getUser().getViewerId();
                    linkMicListListener.syncLinkMicItem(myLinkMicItemBean, userId);
                }
            });
            final int finalAddIndex = addIndex;
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    //??????????????????
                    linkMicListListener.onLinkMicItemInsert(myLinkMicItemBean, finalAddIndex);
                }
            });
            //???????????????????????????????????????
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    linkMicListListener.onLinkMicItemInfoChanged();
                }
            });
        }
    }

    private void removeMyItemToLinkMicListInner() {
        final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(myLinkMicId);
        if (linkMicItem != null) {
            linkMicList.remove(linkMicItem.second);
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    //??????????????????
                    linkMicListListener.onLinkMicItemRemove(linkMicItem.second, linkMicItem.first);
                }
            });
        }
    }

    private void createMyLinkMicItem(String myLinkMicId) {
        if (myLinkMicItemBean == null && myLinkMicId != null) {
            myLinkMicItemBean = new PLVLinkMicItemDataBean();
            myLinkMicItemBean.setStatus(PLVLinkMicItemDataBean.STATUS_IDLE);
            myLinkMicItemBean.setLinkMicId(myLinkMicId);
            myLinkMicItemBean.setActor(liveRoomDataManager.getConfig().getUser().getActor());
            myLinkMicItemBean.setNick(liveRoomDataManager.getConfig().getUser().getViewerName());
            myLinkMicItemBean.setUserType(liveRoomDataManager.getConfig().getUser().getViewerType());
            myLinkMicItemBean.setPic(liveRoomDataManager.getConfig().getUser().getViewerAvatar());
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="??????????????????">
    private void updateLinkMicListWithJoinStatus(PLVLinkMicJoinStatus data) {
        final List<PLVJoinInfoEvent> joinList = data.getJoinList();
        final List<PLVLinkMicJoinStatus.WaitListBean> waitList = data.getWaitList();

        //????????????????????????????????????????????????????????????????????????????????????????????????voice????????????=1??????????????????????????????
        Iterator<PLVJoinInfoEvent> joinInfoEventIterator = joinList.iterator();
        while (joinInfoEventIterator.hasNext()) {
            PLVJoinInfoEvent plvJoinInfoEvent = joinInfoEventIterator.next();
            if (PLVSocketUserConstant.USERTYPE_GUEST.equals(plvJoinInfoEvent.getUserType()) && !plvJoinInfoEvent.getClassStatus().isVoice()) {
                //?????????????????????joinList?????????????????????waitList???
                joinInfoEventIterator.remove();
                waitList.add(PLVLinkMicDataMapper.map2WaitListBean(plvJoinInfoEvent));
                PLVCommonLog.d(TAG, String.format(Locale.US, "guest user [%s] lies in joinList but not join at all, so we move him to waitList manually.", plvJoinInfoEvent.toString()));
            }
        }

        final boolean[] hasChangedLinkMicList = new boolean[1];
        //????????????????????????????????????????????????
        callbackToListener(new ListenerRunnable() {
            @Override
            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                List<String> linkMicUidList = linkMicListListener.onUpdateLinkMicItemStatus(joinList, waitList);
                if (linkMicUidList != null && linkMicList.size() > 0) {
                    hasChangedLinkMicList[0] = true;
                    for (String linkMicUid : linkMicUidList) {
                        rtcJoinMap.remove(linkMicUid);
                    }
                }
            }
        });
        //?????????????????????????????????
        for (PLVJoinInfoEvent joinInfoEvent : joinList) {
            final PLVLinkMicItemDataBean linkMicItemDataBean = PLVLinkMicDataMapper.map2LinkMicItemData(joinInfoEvent);
            final PLVSocketUserBean socketUserBean = PLVLinkMicDataMapper.map2SocketUserBean(joinInfoEvent);
            final boolean isGroupLeader = joinInfoEvent.getClassStatus() != null && joinInfoEvent.getClassStatus().isGroupLeader();
            //?????????????????????????????????????????????
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    boolean result = linkMicListListener.onUpdateLinkMicItemInfo(socketUserBean, linkMicItemDataBean, true, isGroupLeader);
                    if (result) {
                        hasChangedLinkMicList[0] = true;
                    }
                }
            });
        }
        //???????????????????????????????????????????????????????????????
        removeLinkMicItemNoExistServer(joinList);
        //??????????????????????????????????????????
        sortLinkMicList(joinList);
        //?????????????????????????????????
        for (PLVLinkMicJoinStatus.WaitListBean waitListBean : waitList) {
            final PLVLinkMicItemDataBean linkMicItemDataBean = PLVLinkMicDataMapper.map2LinkMicItemData(waitListBean);
            final PLVSocketUserBean socketUserBean = PLVLinkMicDataMapper.map2SocketUserBean(waitListBean);
            //?????????????????????????????????????????????
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    boolean result = linkMicListListener.onUpdateLinkMicItemInfo(socketUserBean, linkMicItemDataBean, false, false);
                    if (result) {
                        hasChangedLinkMicList[0] = true;
                    }
                }
            });
        }
        //????????????????????????
        if (hasChangedLinkMicList[0]) {
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    linkMicListListener.onLinkMicItemInfoChanged();
                }
            });
        }
    }

    private void removeLinkMicItemNoExistServer(List<PLVJoinInfoEvent> joinList) {
        Iterator<PLVLinkMicItemDataBean> linkMicItemDataBeanIterator = linkMicList.iterator();
        int i = 0;
        while (linkMicItemDataBeanIterator.hasNext()) {
            final PLVLinkMicItemDataBean linkMicItemDataBean = linkMicItemDataBeanIterator.next();
            String linkMicId = linkMicItemDataBean.getLinkMicId();
            boolean isExistServerList = false;
            for (PLVJoinInfoEvent joinInfoEvent : joinList) {
                if (linkMicId != null && linkMicId.equals(joinInfoEvent.getUserId())) {
                    isExistServerList = true;
                    break;
                }
            }
            if (!isExistServerList && !isMyLinkMicId(linkMicId)) {
                //????????????linkMicList???data???remove?????????????????????onLinkMicItemRemove????????????????????????
                linkMicItemDataBeanIterator.remove();
                final int finalI = i;
                callbackToListener(new ListenerRunnable() {
                    @Override
                    public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                        linkMicListListener.onLinkMicItemRemove(linkMicItemDataBean, finalI);
                    }
                });
                i--;
            }
            i++;
        }
        for (Map.Entry<String, Boolean> teacherScreenStreamEntry : teacherScreenStreamMap.entrySet()) {
            boolean value = teacherScreenStreamEntry.getValue();
            if (!value) {
                continue;
            }
            String key = teacherScreenStreamEntry.getKey();
            boolean isExistServerList = false;
            for (PLVJoinInfoEvent joinInfoEvent : joinList) {
                if (key != null && key.equals(joinInfoEvent.getUserId())) {
                    isExistServerList = true;
                    break;
                }
            }
            if (!isExistServerList && !isMyLinkMicId(key)) {
                callOnTeacherScreenStream(key, false);
            }
        }
    }

    private void cleanTeacherScreenStream() {
        for (Map.Entry<String, Boolean> teacherScreenStreamEntry : teacherScreenStreamMap.entrySet()) {
            boolean value = teacherScreenStreamEntry.getValue();
            String key = teacherScreenStreamEntry.getKey();
            if (!value || isMyLinkMicId(key)) {
                continue;
            }
            callOnTeacherScreenStream(key, false);
        }
        teacherScreenStreamMap.clear();
    }

    private void sortLinkMicList(String groupLeaderId) {
        if (groupLeaderId == null) {
            return;
        }
        int position = -1;
        int teacherPosition = -1;
        int leaderPosition = -1;
        for (PLVLinkMicItemDataBean linkMicItemDataBean : linkMicList) {
            position++;
            if (linkMicItemDataBean.isTeacher()) {
                teacherPosition = position;
            }
            if (linkMicItemDataBean.getLinkMicId() != null
                    && linkMicItemDataBean.getLinkMicId().equals(groupLeaderId)) {
                leaderPosition = position;
            }
        }
        if (leaderPosition != -1 && leaderPosition != teacherPosition && leaderPosition - teacherPosition != 1) {
            PLVLinkMicItemDataBean linkMicItemDataBean = linkMicList.remove(leaderPosition);
            if (leaderPosition > teacherPosition) {
                linkMicList.add(teacherPosition + 1, linkMicItemDataBean);
            } else {
                linkMicList.add(teacherPosition, linkMicItemDataBean);
            }
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    linkMicListListener.onLinkMicListChanged(linkMicList);
                }
            });
        }
    }

    private void sortLinkMicList(List<PLVJoinInfoEvent> joinList) {
        boolean isNeedSort = false;
        PLVLinkMicItemDataBean[] sortLinkMicArr = new PLVLinkMicItemDataBean[linkMicList.size()];
        List<PLVJoinInfoEvent> copyJoinList = new ArrayList<>(joinList);
        Iterator<PLVJoinInfoEvent> joinListIterator = copyJoinList.iterator();
        int joinListPosition = -1;
        int groupLeaderPosition = -1;
        while (joinListIterator.hasNext()) {
            joinListPosition++;
            PLVJoinInfoEvent joinInfoEvent = joinListIterator.next();
            String linkMicId = joinInfoEvent.getUserId();
            if (linkMicId != null && linkMicId.equals(groupLeaderId)) {
                groupLeaderPosition = joinListPosition;
            }
            boolean isExistLocalList = false;
            for (PLVLinkMicItemDataBean linkMicItem : linkMicList) {
                if (linkMicId != null && linkMicId.equals(linkMicItem.getLinkMicId())) {
                    isExistLocalList = true;
                    break;
                }
            }
            if (!isExistLocalList || PLVSocketUserConstant.USERTYPE_TEACHER.equals(joinInfoEvent.getUserType())) {
                joinListIterator.remove();
                joinListPosition--;
            }
        }
        if (groupLeaderPosition != -1) {
            PLVJoinInfoEvent joinInfoEvent = copyJoinList.remove(groupLeaderPosition);
            copyJoinList.add(0, joinInfoEvent);
        }
        List<PLVLinkMicItemDataBean> sortLinkMicList = new ArrayList<>();
        int linkMicItemSortIndex = -1;
        for (PLVLinkMicItemDataBean linkMicItem : linkMicList) {
            if (linkMicItem.isTeacher() || linkMicItem.getLinkMicId() == null) {
                sortLinkMicList.add(0, linkMicItem);
                continue;
            }
            linkMicItemSortIndex++;
            String linkMicItemId = linkMicItem.getLinkMicId();
            int sortIndex = linkMicItemSortIndex;
            int joinInfoIndex = -1;
            for (PLVJoinInfoEvent joinInfoEvent : copyJoinList) {
                joinInfoIndex++;
                String joinInfoId = joinInfoEvent.getUserId();
                if (linkMicItemId.equals(joinInfoId)) {
                    if (linkMicItemSortIndex != joinInfoIndex) {
                        sortIndex = joinInfoIndex;
                        isNeedSort = true;
                    }
                }
            }
            for (int i = sortIndex; i < sortLinkMicArr.length; i++) {
                if (sortLinkMicArr[i] == null) {
                    sortLinkMicArr[i] = linkMicItem;
                    break;
                }
            }
        }
        if (isNeedSort) {
            sortLinkMicList.addAll(Arrays.asList(sortLinkMicArr).subList(0, sortLinkMicArr.length - sortLinkMicList.size()));
            linkMicList.clear();
            linkMicList.addAll(sortLinkMicList);
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    linkMicListListener.onLinkMicListChanged(linkMicList);
                }
            });
        }
    }

    private boolean updateLinkMicItemInfoWithRtcJoinListInner(final PLVLinkMicItemDataBean linkMicItemDataBean, final String linkMicUid) {
        if (linkMicItemDataBean == null) {
            return false;
        }
        boolean hasChangedLinkMicListItem = false;
        for (Map.Entry<String, PLVLinkMicItemDataBean> linkMicItemDataBeanEntry : rtcJoinMap.entrySet()) {
            String uid = linkMicItemDataBeanEntry.getKey();
            PLVLinkMicItemDataBean linkMicItemBean = linkMicItemDataBeanEntry.getValue();
            if (linkMicUid != null && linkMicUid.equals(uid)) {
                if (!linkMicItemDataBean.isRtcJoinStatus()) {
                    linkMicItemDataBean.setStatus(PLVLinkMicItemDataBean.STATUS_RTC_JOIN);
                    updateLinkMicItemMediaStatus(linkMicItemBean, linkMicItemDataBean);
                    hasChangedLinkMicListItem = true;
                }
                final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(linkMicUid);
                if (linkMicItem == null) {
                    int addIndex = linkMicItemDataBean.isTeacher() ? 0 : linkMicList.size();
                    if (isLeaderId(linkMicItemDataBean.getLinkMicId())) {
                        addIndex = 0;
                        for (int i = 0; i < linkMicList.size(); i++) {
                            if (linkMicList.get(i).isTeacher()) {
                                addIndex = i + 1;
                            }
                        }
                    }
                    linkMicList.add(addIndex, linkMicItemDataBean);
                    if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_MIX == linkMicItemBean.getStreamType()) {
                        if (isTeacherLinkMicId(linkMicUid) || isLeaderId(linkMicUid)) {
                            linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_CAMERA);
                            callOnTeacherScreenStream(linkMicUid, true);
                        } else {
                            linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN);
                        }
                    } else if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN == linkMicItemBean.getStreamType()
                            && (isTeacherLinkMicId(linkMicUid) || isLeaderId(linkMicUid))) {
                        callOnTeacherScreenStream(linkMicUid, true);
                        return false;
                    } else {
                        linkMicItemDataBean.setStreamType(linkMicItemBean.getStreamType());
                    }
                    updateLinkMicItemMediaStatus(linkMicItemBean, linkMicItemDataBean);
                    final int finalAddIndex = addIndex;
                    callbackToListener(new ListenerRunnable() {
                        @Override
                        public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                            linkMicListListener.onLinkMicItemInsert(linkMicItemDataBean, finalAddIndex);
                        }
                    });
                }
                break;
            }
        }
        return hasChangedLinkMicListItem;
    }

    private void updateLinkMicItemMediaStatus(PLVLinkMicItemDataBean rtcJoinLinkMicItem, PLVLinkMicItemDataBean linkMicItemDataBean) {
        if (rtcJoinLinkMicItem == null || linkMicItemDataBean == null) {
            return;
        }
        PLVLinkMicItemDataBean.MuteMedia videoMuteMedia;
        PLVLinkMicItemDataBean.MuteMedia audioMuteMedia;
        if ((videoMuteMedia = rtcJoinLinkMicItem.getMuteVideoInRtcJoinList(linkMicItemDataBean.getStreamType())) != null) {
            //???????????????????????????????????????????????????????????????
            linkMicItemDataBean.setMuteVideo(videoMuteMedia.isMute());
        } else {
            if (!linkMicItemDataBean.isGuest()) {//?????????????????????????????????????????????
                //???????????????????????????????????????????????????muteVideo??????
                linkMicItemDataBean.setMuteVideo(!PLVLinkMicEventSender.getInstance().isVideoLinkMicType());
            } else {
                linkMicItemDataBean.setMuteVideo(false);
            }
        }
        if ((audioMuteMedia = rtcJoinLinkMicItem.getMuteAudioInRtcJoinList(linkMicItemDataBean.getStreamType())) != null) {
            //???????????????????????????????????????????????????????????????
            linkMicItemDataBean.setMuteAudio(audioMuteMedia.isMute());
        } else {
            //???????????????muteAudio?????????false
            linkMicItemDataBean.setMuteAudio(false);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????????">
    private Pair<Integer, PLVLinkMicItemDataBean> getLinkMicItemWithLinkMicIdInner(String linkMicId) {
        for (int i = 0; i < linkMicList.size(); i++) {
            PLVLinkMicItemDataBean linkMicItemDataBean = linkMicList.get(i);
            String linkMicIdForIndex = linkMicItemDataBean.getLinkMicId();
            if (linkMicId != null && linkMicId.equals(linkMicIdForIndex)) {
                return new Pair<>(i, linkMicItemDataBean);
            }
        }
        return null;
    }

    private boolean isTeacherLinkMicId(final String linkMicUid) {
        final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(linkMicUid);
        if (linkMicItem != null) {
            PLVLinkMicItemDataBean linkMicItemDataBean = linkMicItem.second;
            return linkMicItemDataBean.isTeacher();
        } else {
            final boolean[] isTeacherLinkMicId = {false};
            callbackToListener(new ListenerRunnable() {
                @Override
                public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                    PLVLinkMicItemDataBean linkMicItemDataBean = linkMicListListener.onGetSavedLinkMicItem(linkMicUid);
                    if (linkMicItemDataBean != null && !isTeacherLinkMicId[0]) {
                        isTeacherLinkMicId[0] = linkMicItemDataBean.isTeacher();
                    }
                }
            });
            return isTeacherLinkMicId[0];
        }
    }

    private boolean isLeaderId(String linkMicId) {
        return linkMicId != null && linkMicId.equals(groupLeaderId);
    }

    private boolean isMyLinkMicId(String linkMicId) {
        return linkMicId != null && linkMicId.equals(myLinkMicId);
    }

    private void callOnTeacherScreenStream(final String linkMicId, final boolean isOpen) {
        if (teacherScreenStreamMap.containsKey(linkMicId)) {
            boolean oldState = teacherScreenStreamMap.get(linkMicId);
            if (oldState == isOpen) {
                return;
            }
        }
        teacherScreenStreamMap.put(linkMicId, isOpen);
        callbackToListener(new ListenerRunnable() {
            @Override
            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                PLVLinkMicItemDataBean linkMicItemDataBean = new PLVLinkMicItemDataBean();
                linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN);
                linkMicItemDataBean.setLinkMicId(linkMicId);
                linkMicListListener.onTeacherScreenStream(linkMicItemDataBean, isOpen);
            }
        });
    }

    private void dispose(Disposable disposable) {
        if (disposable != null) {
            disposable.dispose();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="???????????? - ??????socket??????">
    private void observeSocketEvent() {
        onMessageListener = new PLVSocketMessageObserver.OnMessageListener() {
            @Override
            public void onMessage(String listenEvent, String event, String message) {
                switch (event) {
                    //sliceId??????
                    case PLVOnSliceIDEvent.EVENT:
                        PLVOnSliceIDEvent onSliceIDEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVOnSliceIDEvent.class);
                        acceptOnSliceIDEvent(onSliceIDEvent);
                        break;
                    //????????????????????????
                    case PLVEventConstant.LinkMic.JOIN_LEAVE_EVENT:
                        PLVJoinLeaveSEvent joinLeaveSEvent = PLVGsonUtil.fromJson(PLVJoinLeaveSEvent.class, message);
                        acceptJoinLeaveSEvent(joinLeaveSEvent);
                        break;
                    //??????/????????????????????? ??????/??????????????????
                    case PLVEventConstant.LinkMic.JOIN_ANSWER_EVENT:
                        PLVJoinAnswerSEvent joinAnswerSEvent = PLVGsonUtil.fromJson(PLVJoinAnswerSEvent.class, message);
                        acceptJoinAnswerSEvent(joinAnswerSEvent);
                        break;
                    //???????????????/?????????????????????????????????????????????
                    case PLVEventConstant.LinkMic.EVENT_OPEN_MICROPHONE:
                        PLVMicphoneStatus micPhoneStatus = PLVGsonUtil.fromJson(PLVMicphoneStatus.class, message);
                        acceptMicphoneStatusEvent(micPhoneStatus);
                        break;
                    //????????????
                    case PLVEventConstant.Class.FINISH_CLASS:
                        PLVFinishClassEvent finishClassEvent = PLVEventHelper.toEventModel(listenEvent, event, message, PLVFinishClassEvent.class);
                        acceptFinishClassEvent(finishClassEvent);
                        break;
                }
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
                Socket.EVENT_MESSAGE);
    }

    private void acceptOnSliceIDEvent(PLVOnSliceIDEvent onSliceIDEvent) {
        if (onSliceIDEvent != null && onSliceIDEvent.getData() != null) {
            PLVClassStatusBean classStatusBean = onSliceIDEvent.getClassStatus();
            if (classStatusBean != null) {
                myClassStatusBeanOnSliceId = classStatusBean;
                @Nullable final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(myLinkMicId);
                if (linkMicItem == null) {
                    return;
                }
                boolean oldLinkMicItemHasPaint = linkMicItem.second.isHasPaint();
                int oldLinkMicItemCupNum = linkMicItem.second.getCupNum();
                final int linkMicItemPos = linkMicItem.first;
                if (classStatusBean.hasPaint() != oldLinkMicItemHasPaint) {
                    linkMicItem.second.setHasPaint(classStatusBean.hasPaint());
                    callbackToListener(new ListenerRunnable() {
                        @Override
                        public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                            //??????????????????????????????????????????????????????????????????????????????
                            linkMicListListener.onUserHasPaint(true, linkMicItem.second.isHasPaint(), linkMicItemPos, -1);
                        }
                    });
                }
                if (classStatusBean.getCup() != oldLinkMicItemCupNum) {
                    linkMicItem.second.setCupNum(classStatusBean.getCup());
                    callbackToListener(new ListenerRunnable() {
                        @Override
                        public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                            linkMicListListener.onUserGetCup(linkMicItem.second.getNick(), false, linkMicItemPos, -1);
                        }
                    });
                }
            }
            if (classStatusBean == null || !classStatusBean.isVoice()) {
                if (!isTeacherType) {
                    acceptUserJoinLeave(myLinkMicId);
                }
            }
        }
    }

    private void acceptJoinLeaveSEvent(PLVJoinLeaveSEvent joinLeaveSEvent) {
        if (joinLeaveSEvent != null && joinLeaveSEvent.getUser() != null) {
            acceptUserJoinLeave(joinLeaveSEvent.getUser().getUserId());
        }
    }

    private void acceptJoinAnswerSEvent(PLVJoinAnswerSEvent joinAnswerSEvent) {
        if (joinAnswerSEvent != null) {
            final String linkMicUid = joinAnswerSEvent.getUserId();
            if (joinAnswerSEvent.isRefuse() || !joinAnswerSEvent.isResult()) {
                callbackToListener(new ListenerRunnable() {
                    @Override
                    public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                        linkMicListListener.onLinkMicItemIdleChanged(linkMicUid);
                    }
                });
                acceptUserJoinLeave(linkMicUid);
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
                    acceptUserJoinLeave(userId);
                }
            }
        }
    }

    private void acceptFinishClassEvent(PLVFinishClassEvent finishClassEvent) {
        if (!isTeacherType) {
            acceptUserJoinLeave(myLinkMicId);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="???????????? - ??????rtc??????">
    private void observeRTCEventInner() {
        linkMicEventListener = new PLVLinkMicEventListener() {
            @Override
            public void onUserOffline(String uid) {
                super.onUserOffline(uid);
                PLVCommonLog.d(TAG, "onUserOffline: " + uid);
            }

            @Override
            public void onUserJoined(String uid) {
                super.onUserJoined(uid);
                PLVCommonLog.d(TAG, "onUserJoined: " + uid);
            }

            @Override
            public void onUserMuteVideo(String uid, boolean mute, int streamType) {
                super.onUserMuteVideo(uid, mute);
                PLVCommonLog.d(TAG, "onUserMuteVideo: " + uid + "*" + mute + "*" + streamType);
                for (Map.Entry<String, PLVLinkMicItemDataBean> linkMicItemDataBeanEntry : rtcJoinMap.entrySet()) {
                    if (uid != null && uid.equals(linkMicItemDataBeanEntry.getKey())) {
                        linkMicItemDataBeanEntry.getValue().setMuteVideoInRtcJoinList(new PLVLinkMicItemDataBean.MuteMedia(mute, streamType));
                    }
                }
            }

            @Override
            public void onUserMuteAudio(final String uid, final boolean mute, int streamType) {
                super.onUserMuteAudio(uid, mute);
                PLVCommonLog.d(TAG, "onUserMuteAudio: " + uid + "*" + mute + "*" + streamType);
                for (Map.Entry<String, PLVLinkMicItemDataBean> linkMicItemDataBeanEntry : rtcJoinMap.entrySet()) {
                    if (uid != null && uid.equals(linkMicItemDataBeanEntry.getKey())) {
                        linkMicItemDataBeanEntry.getValue().setMuteAudioInRtcJoinList(new PLVLinkMicItemDataBean.MuteMedia(mute, streamType));
                    }
                }
            }

            @Override
            public void onRemoteStreamOpen(String uid, @PLVLinkMicConstant.RenderStreamTypeAnnotation int streamType) {
                super.onRemoteStreamOpen(uid, streamType);
                PLVCommonLog.d(TAG, "onRemoteStreamOpen: " + uid + "*" + streamType);
                acceptUserJoinChannel(uid, streamType);
            }

            @Override
            public void onRemoteStreamClose(String uid, @PLVLinkMicConstant.RenderStreamTypeAnnotation int streamType) {
                super.onRemoteStreamClose(uid, streamType);
                PLVCommonLog.d(TAG, "onRemoteStreamClose: " + uid + "*" + streamType);
                acceptUserJoinLeave(uid, streamType);
            }
        };
        if (linkMicManager != null) {
            linkMicManager.addEventHandler(linkMicEventListener);
        }
    }

    private void acceptUserJoinLeave(String linkMicUid) {
        acceptUserJoinLeave(linkMicUid, PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_MIX);
    }

    private void acceptUserJoinLeave(final String linkMicUid, final int streamType) {
        Runnable userJoinLeaveTask = new Runnable() {
            @Override
            public void run() {
                final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(linkMicUid);
                if (linkMicItem != null
                        && (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_MIX == streamType
                        || linkMicItem.second.getStreamType() == streamType)) {
                    linkMicList.remove(linkMicItem.second);
                    callbackToListener(new ListenerRunnable() {
                        @Override
                        public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                            linkMicListListener.onLinkMicItemRemove(linkMicItem.second, linkMicItem.first);
                        }
                    });
                }
            }
        };
        if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_MIX == streamType) {
            rtcJoinMap.remove(linkMicUid);
            userJoinLeaveTask.run();
            if (teacherScreenStreamMap.containsKey(linkMicUid)) {
                callOnTeacherScreenStream(linkMicUid, false);
            }
        } else {
            PLVLinkMicItemDataBean linkMicItemDataBean = rtcJoinMap.get(linkMicUid);
            if (linkMicItemDataBean != null && linkMicItemDataBean.includeStreamType(streamType)) {
                if (linkMicItemDataBean.equalStreamType(streamType)) {
                    rtcJoinMap.remove(linkMicUid);
                } else {
                    if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN == streamType) {
                        linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_CAMERA);
                    } else {
                        linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN);
                    }
                }
                if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN == streamType
                        && teacherScreenStreamMap.containsKey(linkMicUid)) {
                    callOnTeacherScreenStream(linkMicUid, false);
                } else {
                    userJoinLeaveTask.run();
                }
            }
        }
    }

    private void acceptUserJoinChannel(final String linkMicUid, final int streamType) {
        requestLinkMicListApiOnce();
        PLVLinkMicItemDataBean linkMicItemDataBean = rtcJoinMap.get(linkMicUid);
        if (linkMicItemDataBean == null) {
            linkMicItemDataBean = new PLVLinkMicItemDataBean();
            linkMicItemDataBean.setLinkMicId(linkMicUid);
            linkMicItemDataBean.setStreamType(streamType);
            rtcJoinMap.put(linkMicUid, linkMicItemDataBean);
        } else {
            if (!linkMicItemDataBean.equalStreamType(streamType)) {
                linkMicItemDataBean.setStreamType(PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_MIX);
            }
        }
        final Pair<Integer, PLVLinkMicItemDataBean> linkMicItem = getLinkMicItemWithLinkMicId(linkMicUid);
        if (linkMicItem != null) {
            final PLVLinkMicItemDataBean linkMicItemBean = linkMicItem.second;
            final int position = linkMicItem.first;
            if (linkMicItemBean.equalStreamType(streamType)) {
                callbackToListener(new ListenerRunnable() {
                    @Override
                    public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                        linkMicListListener.onLinkMicUserExisted(linkMicItemBean, position);
                    }
                });
            } else {
                if (PLVLinkMicConstant.RenderStreamType.STREAM_TYPE_SCREEN == streamType) {
                    if (isTeacherLinkMicId(linkMicUid) || isLeaderId(linkMicUid)) {
                        callOnTeacherScreenStream(linkMicUid, true);
                    } else {
                        callbackToListener(new ListenerRunnable() {
                            @Override
                            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                                linkMicList.remove(position);
                                linkMicListListener.onLinkMicItemRemove(linkMicItemBean, position);
                                linkMicItemBean.setStreamType(streamType);
                                linkMicList.add(position, linkMicItemBean);
                                linkMicListListener.onLinkMicItemInsert(linkMicItemBean, position);
                            }
                        });
                    }
                }
            }
        }
        callbackToListener(new ListenerRunnable() {
            @Override
            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                PLVLinkMicItemDataBean linkMicItemDataBean = linkMicListListener.onGetSavedLinkMicItem(linkMicUid);
                if (linkMicItemDataBean != null) {
                    boolean result = updateLinkMicItemInfoWithRtcJoinListInner(linkMicItemDataBean, linkMicUid);
                    if (result) {
                        callbackToListener(new ListenerRunnable() {
                            @Override
                            public void run(@NonNull OnLinkMicListListener linkMicListListener) {
                                linkMicListListener.onLinkMicItemInfoChanged();
                            }
                        });
                    }
                }
            }
        });
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????? - view??????">
    private void callbackToListener(ListenerRunnable runnable) {
        if (onLinkMicListListeners != null) {
            for (OnLinkMicListListener listListener : onLinkMicListListeners) {
                if (listListener != null && runnable != null) {
                    runnable.run(listListener);
                }
            }
        }
    }

    private interface ListenerRunnable {
        void run(@NonNull OnLinkMicListListener linkMicListListener);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="????????? - ?????????">
    public interface OnLinkMicListListener {
        /**
         * ??????????????????
         *
         * @param dataBeanList ???????????????????????????????????????????????????
         */
        void onLinkMicListChanged(List<PLVLinkMicItemDataBean> dataBeanList);

        /**
         * ??????????????????item
         */
        void onLinkMicItemRemove(PLVLinkMicItemDataBean linkMicItemDataBean, int position);

        /**
         * ?????????????????????????????????
         *
         * @param position ????????????????????????
         */
        void onLinkMicUserExisted(PLVLinkMicItemDataBean linkMicItemDataBean, int position);

        /**
         * ??????????????????????????????
         *
         * @param isOpen true????????????false?????????
         */
        void onTeacherScreenStream(PLVLinkMicItemDataBean linkMicItemDataBean, boolean isOpen);

        /**
         * ??????joinList???waitList??????????????????item??????
         */
        List<String> onUpdateLinkMicItemStatus(List<PLVJoinInfoEvent> joinList, List<PLVLinkMicJoinStatus.WaitListBean> waitList);

        /**
         * ??????????????????item??????
         */
        boolean onUpdateLinkMicItemInfo(@NonNull PLVSocketUserBean socketUserBean, @NonNull PLVLinkMicItemDataBean linkMicItemDataBean, boolean isJoinList, boolean isGroupLeader);

        /**
         * ????????????????????????item
         */
        PLVLinkMicItemDataBean onGetSavedLinkMicItem(String linkMicId);

        /**
         * ????????????item
         */
        void syncLinkMicItem(PLVLinkMicItemDataBean linkMicItemDataBean, String userId);

        /**
         * ??????item??????????????????
         */
        void onLinkMicItemInfoChanged();

        /**
         * ????????????item???idle??????
         */
        void onLinkMicItemIdleChanged(String linkMicId);

        /**
         * ???????????????????????????
         *
         * @param sessionId ??????id
         * @param callback  ??????
         */
        void onGetLinkMicListStatus(String sessionId, PLVLinkMicDataRepository.IPLVLinkMicDataRepoListener<PLVLinkMicJoinStatus> callback);

        /**
         * ??????????????????item
         *
         * @param position ????????????????????????
         */
        void onLinkMicItemInsert(PLVLinkMicItemDataBean linkMicItemDataBean, int position);

        /**
         * ?????????????????????
         *
         * @param isByEvent      ???????????????????????????????????????
         * @param linkMicListPos ??????????????????????????????????????????????????????????????????-1
         * @param memberListPos  ??????????????????????????????????????????????????????????????????-1
         */
        void onUserGetCup(String userNick, boolean isByEvent, int linkMicListPos, int memberListPos);

        /**
         * ???????????????????????????
         *
         * @param isMyself       ???????????????
         * @param isHasPaint     true???????????????false??????????????????
         * @param linkMicListPos ??????????????????????????????????????????????????????????????????-1
         * @param memberListPos  ??????????????????????????????????????????????????????????????????-1
         */
        void onUserHasPaint(boolean isMyself, boolean isHasPaint, int linkMicListPos, int memberListPos);
    }

    public static abstract class AbsOnLinkMicListListener implements OnLinkMicListListener {
        @Override
        public void onLinkMicListChanged(List<PLVLinkMicItemDataBean> dataBeanList) {

        }

        @Override
        public void onLinkMicItemRemove(PLVLinkMicItemDataBean linkMicItemDataBean, int position) {

        }

        @Override
        public void onLinkMicUserExisted(PLVLinkMicItemDataBean linkMicItemDataBean, int position) {

        }

        @Override
        public void onTeacherScreenStream(PLVLinkMicItemDataBean linkMicItemDataBean, boolean isOpen) {

        }

        @Override
        public List<String> onUpdateLinkMicItemStatus(List<PLVJoinInfoEvent> joinList, List<PLVLinkMicJoinStatus.WaitListBean> waitList) {
            return null;
        }

        @Override
        public boolean onUpdateLinkMicItemInfo(@NonNull PLVSocketUserBean socketUserBean, @NonNull PLVLinkMicItemDataBean linkMicItemDataBean, boolean isJoinList, boolean isGroupLeader) {
            return false;
        }

        @Override
        public PLVLinkMicItemDataBean onGetSavedLinkMicItem(String linkMicId) {
            return null;
        }

        @Override
        public void syncLinkMicItem(PLVLinkMicItemDataBean linkMicItemDataBean, String userId) {

        }

        @Override
        public void onLinkMicItemInfoChanged() {

        }

        @Override
        public void onLinkMicItemIdleChanged(String linkMicId) {

        }

        @Override
        public void onGetLinkMicListStatus(String sessionId, PLVLinkMicDataRepository.IPLVLinkMicDataRepoListener<PLVLinkMicJoinStatus> callback) {

        }

        @Override
        public void onLinkMicItemInsert(PLVLinkMicItemDataBean linkMicItemDataBean, int position) {

        }

        @Override
        public void onUserGetCup(String userNick, boolean isByEvent, int linkMicListPos, int memberListPos) {

        }

        @Override
        public void onUserHasPaint(boolean isMyself, boolean isHasPaint, int linkMicListPos, int memberListPos) {

        }
    }
    // </editor-fold>
}
