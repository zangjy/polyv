package com.easefun.polyv.livecommon.module.modules.document.presenter;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.easefun.polyv.livecommon.module.data.IPLVLiveRoomDataManager;
import com.easefun.polyv.livecommon.module.data.PLVStatefulData;
import com.easefun.polyv.livecommon.module.modules.document.contract.IPLVDocumentContract;
import com.easefun.polyv.livecommon.module.modules.document.model.PLVDocumentRepository;
import com.easefun.polyv.livecommon.module.modules.document.model.PLVPptUploadLocalRepository;
import com.easefun.polyv.livecommon.module.modules.document.model.enums.PLVDocumentMarkToolType;
import com.easefun.polyv.livecommon.module.modules.document.model.enums.PLVDocumentMode;
import com.easefun.polyv.livecommon.module.modules.document.model.vo.PLVPptUploadLocalCacheVO;
import com.easefun.polyv.livescenes.document.PLVSDocumentWebProcessor;
import com.easefun.polyv.livescenes.document.model.PLVSAssistantInfo;
import com.easefun.polyv.livescenes.document.model.PLVSChangePPTInfo;
import com.easefun.polyv.livescenes.document.model.PLVSEditTextInfo;
import com.easefun.polyv.livescenes.document.model.PLVSPPTJsModel;
import com.easefun.polyv.livescenes.document.model.PLVSPPTPaintStatus;
import com.easefun.polyv.livescenes.document.model.PLVSPPTStatus;
import com.easefun.polyv.livescenes.upload.OnPLVSDocumentUploadListener;
import com.plv.foundationsdk.utils.PLVGsonUtil;
import com.plv.livescenes.access.PLVUserAbility;
import com.plv.livescenes.access.PLVUserAbilityManager;
import com.plv.livescenes.access.PLVUserRole;
import com.plv.livescenes.document.PLVDocumentWebProcessor;
import com.plv.livescenes.socket.PLVSocketWrapper;
import com.plv.socket.event.PLVEventConstant;
import com.plv.socket.event.PLVMessageBaseEvent;
import com.plv.socket.event.ppt.PLVOnSliceStartEvent;
import com.plv.socket.eventbus.ppt.PLVOnSliceStartEventBus;
import com.plv.socket.impl.PLVSocketMessageObserver;
import com.plv.socket.user.PLVSocketUserBean;
import com.plv.socket.user.PLVSocketUserConstant;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;

/**
 * ??????????????????????????????Presenter
 * ??????????????????&ppt?????????????????????
 * <p>
 * ??????????????????????????????????????????????????????????????????????????????????????????{@link PLVDocumentNetPresenter}??????
 *
 * @author suhongtao
 */
public class PLVDocumentPresenter implements IPLVDocumentContract.Presenter {

    // <editor-fold defaultstate="collapsed" desc="??????">

    private static PLVDocumentPresenter INSTANCE;

    private PLVDocumentPresenter() {
    }

    public static IPLVDocumentContract.Presenter getInstance() {
        if (INSTANCE == null) {
            synchronized (PLVDocumentPresenter.class) {
                if (INSTANCE == null) {
                    INSTANCE = new PLVDocumentPresenter();
                }
            }
        }
        return INSTANCE;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="??????">

    private static final String TAG = PLVDocumentPresenter.class.getSimpleName();

    // ??????autoId???0
    public static final int AUTO_ID_WHITE_BOARD = 0;

    // ????????? ???????????????????????????
    private boolean isInitialized = false;
    /**
     * MVP - View ?????????
     */
    private final List<WeakReference<IPLVDocumentContract.View>> viewWeakReferenceList = new ArrayList<>();

    /**
     * rx disposables
     */
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    /**
     * MVP - Model
     */
    @Nullable
    private PLVDocumentRepository plvDocumentRepository;
    @Nullable
    private PLVPptUploadLocalRepository plvPptUploadLocalRepository;

    @Nullable
    private PLVUserAbilityManager.OnUserAbilityChangedListener onUserAbilityChangeCallback;
    @Nullable
    private PLVUserAbilityManager.OnUserRoleChangedListener onUserRoleChangedListener;

    /**
     * ????????? ??????????????????
     * ????????????????????????????????????
     */
    private boolean isStreamStarted = false;

    //???????????????
    private boolean isGuest = false;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="???????????????">

    @Override
    public void init(LifecycleOwner lifecycleOwner,
                     IPLVLiveRoomDataManager liveRoomDataManager,
                     PLVSDocumentWebProcessor documentWebProcessor) {
        isGuest = liveRoomDataManager.getConfig().getUser().getViewerType().equals(PLVSocketUserConstant.USERTYPE_GUEST);
        initRepository(liveRoomDataManager, documentWebProcessor);
        initOnUserAbilityChangeListener();
        initOnUserRoleChangeListener();
        initSocketListener();

        observeRefreshPptMessage(lifecycleOwner);
        observePptJsModel(lifecycleOwner);
        observePptStatus(lifecycleOwner);
        observePptPaintStatus(lifecycleOwner);
        observeDocumentZoomValueChanged(lifecycleOwner);

        observeOnSliceStartEvent();

        isInitialized = true;
    }

    /**
     * ????????? MVP - Model
     * ????????????WebView
     *
     * @param liveRoomDataManager
     * @param documentWebProcessor
     */
    private void initRepository(IPLVLiveRoomDataManager liveRoomDataManager, PLVSDocumentWebProcessor documentWebProcessor) {
        plvDocumentRepository = new PLVDocumentRepository(documentWebProcessor);
        plvDocumentRepository.init(liveRoomDataManager);

        PLVSocketUserBean userBean = new PLVSocketUserBean();
        userBean.setUserId(liveRoomDataManager.getConfig().getUser().getViewerId());
        userBean.setNick(liveRoomDataManager.getConfig().getUser().getViewerName());
        userBean.setPic(liveRoomDataManager.getConfig().getUser().getViewerAvatar());

        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.SETUSER, PLVGsonUtil.toJson(userBean));
        updateDocumentPermission();
        if (!isGuest) {
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPT, "{\"autoId\":0,\"isCamClosed\":0}");
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.SETPAINTSTATUS, "{\"status\":\"open\"}");

        plvPptUploadLocalRepository = new PLVPptUploadLocalRepository();
    }

    /**
     * ???????????????????????????????????????
     */
    private void initOnUserAbilityChangeListener() {
        this.onUserAbilityChangeCallback = new PLVUserAbilityManager.OnUserAbilityChangedListener() {
            @Override
            public void onUserAbilitiesChanged(@NonNull List<PLVUserAbility> addedAbilities, @NonNull List<PLVUserAbility> removedAbilities) {
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onUserPermissionChange();
                    }
                }

                final boolean hasPaintPermission = PLVUserAbilityManager.myAbility().hasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_USE_PAINT);
                enableMarkTool(hasPaintPermission);
            }
        };
        PLVUserAbilityManager.myAbility().addUserAbilityChangeListener(new WeakReference<>(onUserAbilityChangeCallback));
    }

    /**
     * ?????????????????????????????????
     */
    private void initOnUserRoleChangeListener() {
        this.onUserRoleChangedListener = new PLVUserAbilityManager.OnUserRoleChangedListener() {
            @Override
            public void onUserRoleAdded(PLVUserRole role) {
                updateDocumentPermission();
            }

            @Override
            public void onUserRoleRemoved(PLVUserRole role) {
                updateDocumentPermission();
            }
        };
        PLVUserAbilityManager.myAbility().addUserRoleChangeListener(new WeakReference<>(onUserRoleChangedListener));
    }

    /**
     * ?????????Socket??????
     * ??????????????????PPT??????socket??????
     */
    private void initSocketListener() {
        PLVSocketWrapper.getInstance().getSocketObserver().addOnMessageListener(new PLVSocketMessageObserver.OnMessageListener() {
            @Override
            public void onMessage(String listenEvent, String event, String message) {
                if (!PLVEventConstant.Ppt.ON_ASSISTANT_CONTROL.equals(event)) {
                    return;
                }
                PLVSAssistantInfo assistantInfo = PLVGsonUtil.fromJson(PLVSAssistantInfo.class, message);
                if (assistantInfo == null) {
                    return;
                }
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onAssistantChangePptPage(assistantInfo.getData().getPageId());
                    }
                }
            }
        });
    }

    /**
     * ??????Model???PPT????????????
     * ???PPT????????????????????????socket??????????????????
     *
     * @param lifecycleOwner
     */
    private void observeRefreshPptMessage(LifecycleOwner lifecycleOwner) {
        plvDocumentRepository.getRefreshPptMessageLiveData().observe(lifecycleOwner, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String message) {
                if (isStreamStarted) {
                    PLVSocketWrapper.getInstance().emit(PLVMessageBaseEvent.LISTEN_EVENT, message);
                }
            }
        });
    }

    /**
     * ??????Model?????????PPT????????????????????????
     * ???view?????????
     *
     * @param lifecycleOwner
     */
    private void observePptJsModel(LifecycleOwner lifecycleOwner) {
        plvDocumentRepository.getPptJsModelLiveData().observe(lifecycleOwner, new Observer<PLVStatefulData<PLVSPPTJsModel>>() {
            @Override
            public void onChanged(@Nullable PLVStatefulData<PLVSPPTJsModel> plvsPptJsModel) {
                if (plvsPptJsModel == null || !plvsPptJsModel.isSuccess()) {
                    return;
                }
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onPptPageList(plvsPptJsModel.getData());
                    }
                }
            }
        });
    }

    /**
     * ??????Model???Webview PPT????????????
     * ???view????????? PPTID?????? ????????????
     *
     * @param lifecycleOwner
     */
    private void observePptStatus(LifecycleOwner lifecycleOwner) {
        plvDocumentRepository.getPptStatusLiveData().observe(lifecycleOwner, new Observer<PLVSPPTStatus>() {
            @Override
            public void onChanged(@Nullable PLVSPPTStatus plvspptStatus) {
                if (plvspptStatus == null) {
                    return;
                }
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onPptPageChange(plvspptStatus.getAutoId(), plvspptStatus.getPageId());
                        view.onPptStatusChange(plvspptStatus);
                    }
                }
            }
        });
    }

    /**
     * ??????Model???Webview ??????????????????
     * ???view?????????
     *
     * @param lifecycleOwner
     */
    private void observePptPaintStatus(LifecycleOwner lifecycleOwner) {
        plvDocumentRepository.getPptPaintStatusLiveData().observe(lifecycleOwner, new Observer<PLVSPPTPaintStatus>() {
            @Override
            public void onChanged(@Nullable PLVSPPTPaintStatus plvspptPaintStatus) {
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onPptPaintStatus(plvspptPaintStatus);
                    }
                }
            }
        });
    }

    /**
     * ??????????????????????????????
     */
    private void observeDocumentZoomValueChanged(LifecycleOwner lifecycleOwner) {
        plvDocumentRepository.getDocumentZoomValueLiveData().observe(lifecycleOwner, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String value) {
                if (value == null) {
                    return;
                }
                for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
                    IPLVDocumentContract.View view = viewWeakReference.get();
                    if (view != null) {
                        view.onZoomValueChanged(value);
                    }
                }
            }
        });
    }

    /**
     * ??????sliceStart????????????????????????????????????webview
     * ?????????????????????????????????
     */
    private void observeOnSliceStartEvent() {
        Disposable disposable = PLVOnSliceStartEventBus.get()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<PLVOnSliceStartEvent>() {
                    @Override
                    public void accept(PLVOnSliceStartEvent plvOnSliceStartEvent) {
                        if (plvDocumentRepository != null) {
                            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.ONSLICESTART, PLVGsonUtil.toJson(plvOnSliceStartEvent));
                        }
                    }
                });
        compositeDisposable.add(disposable);
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Presenter??????">

    @Override
    public void registerView(IPLVDocumentContract.View view) {
        PLVDocumentNetPresenter.getInstance().registerView(view);
        viewWeakReferenceList.add(new WeakReference<>(view));
    }

    @Override
    public void notifyStreamStatus(boolean isStreamStarted) {
        this.isStreamStarted = isStreamStarted;
    }

    @Override
    public void switchShowMode(PLVDocumentMode showMode) {
        for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
            IPLVDocumentContract.View view = viewWeakReference.get();
            if (view != null) {
                view.onSwitchShowMode(showMode);
            }
        }
    }

    @Override
    public void enableMarkTool(boolean enable) {
        if (!checkInitialized()) {
            return;
        }
        final boolean hasPaintPermission = PLVUserAbilityManager.myAbility().hasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_USE_PAINT);
        if (enable && hasPaintPermission) {
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.SETPAINTSTATUS, "{\"status\":\"open\"}");
        } else {
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.SETPAINTSTATUS, "{\"status\":\"close\"}");
        }
    }

    @Override
    public void changeColor(String colorString) {
        if (!checkInitialized()) {
            return;
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGE_COLOR, colorString);
    }

    @Override
    public void changeMarkToolType(@PLVDocumentMarkToolType.Range String markToolType) {
        if (!checkInitialized()) {
            return;
        }
        if (PLVDocumentMarkToolType.CLEAR.equals(markToolType)) {
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.DELETEALLPAINT, "");
        } else if (PLVDocumentMarkToolType.ERASER.equals(markToolType)) {
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.ERASE_STATUS, "");
        } else if (PLVDocumentMarkToolType.BRUSH.equals(markToolType)
                || PLVDocumentMarkToolType.ARROW.equals(markToolType)
                || PLVDocumentMarkToolType.TEXT.equals(markToolType)) {
            String message = "{\"type\":\"" + markToolType + "\"}";
            plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.SETDRAWTYPE, message);
        }
    }

    @Override
    public void changeToWhiteBoard() {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_SWITCH_PPT_WHITEBOARD)) {
            return;
        }
        changeWhiteBoardPage(0);
    }

    @Override
    public void changeWhiteBoardPage(int pageId) {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_TURN_PAGE)) {
            return;
        }
        PLVSChangePPTInfo changePptInfo = new PLVSChangePPTInfo(AUTO_ID_WHITE_BOARD, pageId);
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPT, PLVGsonUtil.toJson(changePptInfo));
    }

    @Override
    public void changePpt(int autoId) {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_OPEN_PPT)) {
            return;
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPT, "{\"autoId\":" + autoId + "}");
        // ??????PPT?????????????????????????????????
        changePptPage(autoId, 0);
    }

    @Override
    public void changePptPage(int autoId, int pageId) {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_TURN_PAGE)) {
            return;
        }
        PLVSChangePPTInfo changePptInfo = new PLVSChangePPTInfo(autoId, pageId);
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPT, PLVGsonUtil.toJson(changePptInfo));
    }

    @Override
    public void changePptToLastStep() {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_TURN_PAGE)) {
            return;
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPTPAGE, "{\"type\":\"gotoPreviousStep\"}");
    }

    @Override
    public void changePptToNextStep() {
        if (!checkInitialized()) {
            return;
        }
        if (PLVUserAbilityManager.myAbility().notHasAbility(PLVUserAbility.STREAMER_DOCUMENT_ALLOW_TURN_PAGE)) {
            return;
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.CHANGEPPTPAGE, "{\"type\":\"gotoNextStep\"}");
    }

    @Override
    public void changeTextContent(String content) {
        if (!checkInitialized()) {
            return;
        }
        PLVSEditTextInfo textInfo = new PLVSEditTextInfo(content);
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.FILLEDITTEXT, PLVGsonUtil.toJson(textInfo));
    }

    @Override
    public void resetZoom() {
        if (!checkInitialized()) {
            return;
        }
        plvDocumentRepository.sendWebMessage(PLVDocumentWebProcessor.TO_ZOOM_RESET, "");
    }

    @Override
    public void requestGetPptCoverList() {
        PLVDocumentNetPresenter.getInstance().requestGetPptCoverList();
    }

    @Override
    public void requestGetPptCoverList(boolean forceRefresh) {
        PLVDocumentNetPresenter.getInstance().requestGetPptCoverList(forceRefresh);
    }

    @Override
    public void requestGetPptPageList(int autoId) {
        if (!checkInitialized()) {
            return;
        }
        plvDocumentRepository.requestGetCachedPptPageList(autoId);
    }

    @Override
    public void onSelectUploadFile(Uri fileUri) {
        PLVDocumentNetPresenter.getInstance().onSelectUploadFile(fileUri);
    }

    @Override
    public void uploadFile(Context context, File uploadFile, final String convertType, final OnPLVSDocumentUploadListener listener) {
        PLVDocumentNetPresenter.getInstance().uploadFile(context, uploadFile, convertType, listener);
    }

    @Override
    public void restartUploadFromCache(Context context, String fileId, OnPLVSDocumentUploadListener listener) {
        PLVDocumentNetPresenter.getInstance().restartUploadFromCache(context, fileId, listener);
    }

    @Override
    public void checkUploadFileStatus() {
        PLVDocumentNetPresenter.getInstance().checkUploadFileStatus();
    }

    @Override
    public void removeUploadCache(int autoId) {
        PLVDocumentNetPresenter.getInstance().removeUploadCache(autoId);
    }

    @Override
    public void removeUploadCache(List<PLVPptUploadLocalCacheVO> localCacheVOS) {
        PLVDocumentNetPresenter.getInstance().removeUploadCache(localCacheVOS);
    }

    @Override
    public void removeUploadCache(String fileId) {
        PLVDocumentNetPresenter.getInstance().removeUploadCache(fileId);
    }

    @Override
    public void deleteDocument(int autoId) {
        PLVDocumentNetPresenter.getInstance().deleteDocument(autoId);
    }

    @Override
    public void deleteDocument(String fileId) {
        PLVDocumentNetPresenter.getInstance().deleteDocument(fileId);
    }

    @Override
    public void requestOpenPptView(int pptId, String pptName) {
        for (WeakReference<IPLVDocumentContract.View> viewWeakReference : viewWeakReferenceList) {
            IPLVDocumentContract.View view = viewWeakReference.get();
            if (view == null) {
                continue;
            }
            if (view.onRequestOpenPptView(pptId, pptName)) {
                // consume
                break;
            }
        }
    }

    @Override
    public void destroy() {
        compositeDisposable.dispose();
        onUserAbilityChangeCallback = null;
        onUserRoleChangedListener = null;
        if (plvDocumentRepository != null) {
            plvDocumentRepository.destroy();
        }
        PLVDocumentNetPresenter.getInstance().destroy();
        isInitialized = false;
        viewWeakReferenceList.clear();
        INSTANCE = null;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="??????????????????">

    /**
     * ?????????????????????????????????
     * ???????????????????????????
     *
     * @return isInitialized
     */
    private boolean checkInitialized() {
        if (!isInitialized
                || plvPptUploadLocalRepository == null
                || plvDocumentRepository == null) {
            Log.w(TAG, "Call PLVLSDocumentPresenter.init() first!");
        }
        return isInitialized;
    }

    private void updateDocumentPermission() {
        if (plvDocumentRepository == null) {
            return;
        }
        final String userType;
        if (PLVUserAbilityManager.myAbility().hasRole(PLVUserRole.STREAMER_TEACHER) || PLVUserAbilityManager.myAbility().hasRole(PLVUserRole.STREAMER_GRANTED_SPEAKER_USER)) {
            userType = "speaker";
        } else if (PLVUserAbilityManager.myAbility().hasRole(PLVUserRole.STREAMER_GRANTED_PAINT_USER)) {
            userType = "paint";
        } else {
            userType = "whatever";
        }
        plvDocumentRepository.sendWebMessage(PLVSDocumentWebProcessor.AUTHORIZATION_PPT_PAINT, "{\"userType\":\"" + userType + "\"}");
    }

    // </editor-fold>
}
