package com.ss.video.rtc.demo.quickstart;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.RTCVideo;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.VideoCanvas;
import com.ss.bytertc.engine.VideoEncoderConfig;
import com.ss.bytertc.engine.data.AudioRoute;
import com.ss.bytertc.engine.data.CameraId;
import com.ss.bytertc.engine.data.RemoteStreamKey;
import com.ss.bytertc.engine.data.ScreenMediaType;
import com.ss.bytertc.engine.data.StreamIndex;
import com.ss.bytertc.engine.data.VideoFrameInfo;
import com.ss.bytertc.engine.data.VideoSourceType;
import com.ss.bytertc.engine.handler.IRTCVideoEventHandler;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.MediaDeviceState;
import com.ss.bytertc.engine.type.MediaStreamType;
import com.ss.bytertc.engine.type.StreamRemoveReason;
import com.ss.bytertc.engine.type.VideoDeviceType;
import com.ss.bytertc.engine.video.IVideoSink;
import com.ss.rtc.demo.quickstart.R;

import org.webrtc.RXScreenCaptureService;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * VolcEngineRTC 视频通话的主页面
 * 本示例不限制房间内最大用户数；同时最多渲染四个用户的视频数据（自己和三个远端用户视频数据）；
 *
 * 包含如下简单功能：
 * - 创建引擎
 * - 设置视频发布参数
 * - 渲染自己的视频数据
 * - 创建房间
 * - 加入音视频通话房间
 * - 打开/关闭麦克风
 * - 打开/关闭摄像头
 * - 渲染远端用户的视频数据
 * - 离开房间
 * - 销毁引擎
 *
 * 实现一个基本的音视频通话的流程如下：
 * 1.创建 IRTCVideo 实例。
 *   RTCVideo.createRTCVideo(Context context, String appId, IRTCVideoEventHandler handler,
 *     Object eglContext, JSONObject parameters)
 * 2.视频发布端设置期望发布的最大分辨率视频流参数，包括分辨率、帧率、码率、缩放模式、网络不佳时的回退策略等。
 *   RTCVideo.setVideoEncoderConfig(VideoEncoderConfig maxSolution)
 * 3.开启本地视频采集。 RTCVideo.startVideoCapture()
 * 4.设置本地视频渲染时，使用的视图，并设置渲染模式。
 *   RTCVideo.setLocalVideoCanvas(StreamIndex streamIndex, VideoCanvas videoCanvas)
 * 5.创建房间。RTCVideo.createRTCRoom(String roomId)
 * 6.加入音视频通话房间。
 *   RTCRoom.joinRoom(String token, UserInfo userInfo, RTCRoomConfig roomConfig)
 * 7.SDK 接收并解码远端视频流首帧后，设置用户的视频渲染视图。
 *   RTCVideo.setRemoteVideoCanvas(String userId, StreamIndex streamIndex, VideoCanvas videoCanvas)
 * 8.在用户离开房间之后移出用户的视频渲染视图。
 * 9.离开音视频通话房间。RTCRoom.leaveRoom()
 * 10.调用 RTCRoom.destroy() 销毁房间对象。
 * 11.调用 RTCVideo.destroyRTCVideo() 销毁引擎。
 *
 * 详细的API文档参见{https://www.volcengine.com/docs/6348/70080}
 */
public class RTCRoomActivity extends AppCompatActivity implements OnChatHideListener{
    private String code;
    //private TreeMap<String, String> room = new TreeMap<>();

    private ImageView mSpeakerIv;
    private ImageView mAudioIv;
    private ImageView mVideoIv;

    private TextView mInputSendTv;
    private EditText mInputEt;

    private boolean mIsSpeakerPhone = true;
    private boolean mIsMuteAudio = false;
    private boolean mIsMuteVideo = false;
    private CameraId mCameraID = CameraId.CAMERA_ID_FRONT;

    private FrameLayout mSelfContainer;
    private final FrameLayout[] mRemoteContainerArray = new FrameLayout[7];
    //private List<FrameLayout> mRemoteList = new ArrayList<>();
    private final TextView[] mUserIdTvArray = new TextView[7];
    private final String[] mShowUidArray = new String[7];

    private RTCVideo mRTCVideo;
    private RTCRoom mRTCRoom;
    private RecyclerView mRecyclerView; //聊天区RV
    //private RecyclerView mVideoRV; //视频区RV
    private ChatAdapter mChatAdapter = new ChatAdapter();  //聊天适配器
    //private VideoAdapter mVideoAdapter = new VideoAdapter();

    public long LastChatActivityTime = 0; //上一次聊天区活动时间
    public final ReentrantLock ChatActivityTimeLock = new ReentrantLock(); //聊天区活动时间锁

    private String mRoomId;
    private VideoView videoPlay;
    private MediaController mediaController;
    private static int REQUEST_VIDEO_CODE = 1;
    public static final int REQUEST_CODE_OF_SCREEN_SHARING = 101;

    public void requestForScreenSharing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.e("ShareScreen","当前系统版本过低，无法支持屏幕共享");
            return;
        }
        MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_OF_SCREEN_SHARING);
        } else {
            Log.e("ShareScreen","当前系统版本过低，无法支持屏幕共享");
        }
    }

    private void startScreenShare(Intent data) {
        startRXScreenCaptureService(data);
        //编码参数
        VideoEncoderConfig config = new VideoEncoderConfig();
        config.width = 720;
        config.height = 1280;
        config.frameRate = 15;
        config.maxBitrate = 1600;
        mRTCVideo.setScreenVideoEncoderConfig(config);
        // 开启屏幕视频数据采集
        mRTCVideo.startScreenCapture(ScreenMediaType.SCREEN_MEDIA_TYPE_VIDEO_AND_AUDIO, data);
        Log.i("progress","ready");
    }

    private void startRXScreenCaptureService(@NonNull Intent data) {
        Context context = getApplicationContext();//Utilities.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent intent = new Intent();
            intent.putExtra(RXScreenCaptureService.KEY_LARGE_ICON, R.drawable.launcher_quick_start);
            intent.putExtra(RXScreenCaptureService.KEY_SMALL_ICON, R.drawable.launcher_quick_start);
            intent.putExtra(RXScreenCaptureService.KEY_LAUNCH_ACTIVITY, RTCRoomActivity.this.getClass().getCanonicalName());//mHostActivity.getClass().getCanonicalName()
            intent.putExtra(RXScreenCaptureService.KEY_CONTENT_TEXT, "正在录制/投射您的屏幕");
            intent.putExtra(RXScreenCaptureService.KEY_RESULT_DATA, data);
            //前台服务
            context.startForegroundService(RXScreenCaptureService.getServiceIntent(context, RXScreenCaptureService.COMMAND_LAUNCH, intent));
            Log.i("progress","前台已就位");
        }
    }

    ChatHideAsyncTask mTask= new ChatHideAsyncTask(this);

    @Override
    public void setChatVisibility(int visibility) {
        mRecyclerView.setVisibility(visibility);
    }

    @Override
    public void setChatAlpha(float alpha) {
        mRecyclerView.setAlpha(alpha);
    }


    private static class ChatHideAsyncTask extends AsyncTask<Object, Void, Void> {

        private WeakReference<RTCRoomActivity> activityReference;
        OnChatHideListener listener;

        ChatHideAsyncTask(RTCRoomActivity context) {
            activityReference = new WeakReference<>(context);
            listener = context;
        }

        private double NormalizedTunableSigmoidFunction(double x, double k) {
            return (x - k * x) / (k - 2 * k * Math.abs(x) + 1);
        }

        @Override
        protected Void doInBackground(Object[] objects) {
            RTCRoomActivity activity = activityReference.get();
            RecyclerView mRecyclerView = activity.findViewById(R.id.main_chat_rv);
            if (activity.isFinishing()) return null;
            while (true) {
                if(activity.ChatActivityTimeLock.tryLock()){
                    try {
                        if (System.currentTimeMillis() - activity.LastChatActivityTime > 5000 && mRecyclerView.getVisibility() == View.VISIBLE) {
                            double alpha = (10000 - System.currentTimeMillis() + activity.LastChatActivityTime) / 5000f;
                            if (alpha < 0) alpha = 0;
                            if (alpha > 1) alpha = 1;
                            alpha = NormalizedTunableSigmoidFunction(alpha, -0.75);
                            listener.setChatAlpha((float) alpha);
                        }
                        if (System.currentTimeMillis() - activity.LastChatActivityTime > 10000) {
                            listener.setChatVisibility(View.INVISIBLE);
                        }
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
                    finally {
                        activity.ChatActivityTimeLock.unlock();
                    }
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }

                if (activity.isFinishing()){
                    break;
                }
                if (isCancelled()){
                    break;
                }
            }
            return null;
        }
    }

    private RTCRoomEventHandlerAdapter mIRtcRoomEventHandler = new RTCRoomEventHandlerAdapter() {

        /**
         * 远端主播角色用户加入房间回调。
         */
        @Override
        public void onUserJoined(UserInfo userInfo, int elapsed) {
            super.onUserJoined(userInfo, elapsed);
            Log.d("IRTCRoomEventHandler", "onUserJoined: " + userInfo.getUid());
            //mRTCRoomStats.users += 1;
        }

        /**
         * 远端用户离开房间回调。
         */
        @Override
        public void onUserLeave(String uid, int reason) {
            super.onUserLeave(uid, reason);
            Log.d("IRTCRoomEventHandler", "onUserLeave: " + uid);
            runOnUiThread(() -> removeRemoteView(uid));
            //mRTCRoomStats.users -= 1;
        }

        @Override
        public void onUserPublishScreen(String uid, MediaStreamType type) {
            if (type != MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO) {
                FrameLayout fl = findViewById(R.id.ShareScreenFL);
                runOnUiThread(() -> removeRemoteView(uid));
                runOnUiThread(() -> setRemoteView_with_share(new RemoteStreamKey(mRoomId, uid, StreamIndex.STREAM_INDEX_SCREEN), fl));
                //runOnUiThread(() -> setRemoteView(mRoomId, uid));//可能有问题
                Log.i("progress","有人在共享屏幕");
            }
        }

        @Override
        public void onUserUnpublishScreen(String uid, MediaStreamType type, StreamRemoveReason reason) {
            if (type != MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO) {
                runOnUiThread(() -> removeRemoteView(uid));
                runOnUiThread(() -> setRemoteView_with_share(new RemoteStreamKey(mRoomId, uid, StreamIndex.STREAM_INDEX_MAIN)));
                FrameLayout fl = findViewById(R.id.ShareScreenFL);
                fl.removeAllViews();
                Log.i("progress","有人取消了共享屏幕");
            }
        }

        /**
         * 房间内新增远端摄像头/麦克风采集音视频流的回调。
         */
        //@Override
        //public void onUserPublishStream(String uid, MediaStreamType type) {
        //    //Log.d(TAG, "onUserPublishStream: " + uid);
        //    if (type != MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO) {
        //        runOnUiThread(() -> setRemoteView(new RemoteStreamKey(mRoomId, uid, StreamIndex.STREAM_INDEX_MAIN)));
        //    }
        //}

        //@Override
        //public void onUserUnpublishStream(String uid, MediaStreamType type, StreamRemoveReason reason) {
        //    //Log.d(TAG, "onUserUnPublishStream: " + uid);
        //    if (type != MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO) {
        //        runOnUiThread(() -> removeRemoteView(uid));
        //    }
        //}

        @Override
        public void onRoomMessageSendResult(long msgid, int error) {
            super.onRoomMessageSendResult(msgid, error);
            String tip;
            if (error != 0) {
                tip = String.format(Locale.US, "房间消息发送失败(%d)", error);
            } else {
                tip = "消息发送成功";
            }
            //runOnUiThread(() -> Toast.makeText(RTCRoomActivity.this, tip, Toast.LENGTH_SHORT).show());
        }


        @Override
        public void onRoomMessageReceived(String uid, String message) {
            super.onRoomMessageReceived(uid, message);
            showMessage(uid, message);
            mRecyclerView.scrollToPosition(mChatAdapter.getItemCount()-1);
            ChatActivityTimeLock.lock();
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAlpha(1);
            LastChatActivityTime = System.currentTimeMillis();
            ChatActivityTimeLock.unlock();
        }


    };

    private IRTCVideoEventHandler mIRtcVideoEventHandler = new IRTCVideoEventHandler() {

        /**
         * SDK收到第一帧远端视频解码数据后，用户收到此回调。
         */
        @Override
        public void onFirstRemoteVideoFrameDecoded(RemoteStreamKey remoteStreamKey, VideoFrameInfo frameInfo) {
            super.onFirstRemoteVideoFrameDecoded(remoteStreamKey, frameInfo);
            Log.d("IRTCVideoEventHandler", "onFirstRemoteVideoFrame: " + remoteStreamKey.toString());
            runOnUiThread(() -> setRemoteView(remoteStreamKey.getRoomId(), remoteStreamKey.getUserId()));
            Log.i("progress","远端视频正在解码");
        }

        /**
         * 警告回调，详细可以看 {https://www.volcengine.com/docs/6348/70082#warncode}
         */
        @Override
        public void onWarning(int warn) {
            super.onWarning(warn);
            Log.d("IRTCVideoEventHandler", "onWarning: " + warn);
        }

        /**
         * 错误回调，详细可以看 {https://www.volcengine.com/docs/6348/70082#errorcode}
         */
        @Override
        public void onError(int err) {
            super.onError(err);
            Log.d("IRTCVideoEventHandler", "onError: " + err);
            showAlertDialog(String.format(Locale.US, "error: %d", err));
        }

        @Override
        public void onVideoDeviceStateChanged(String deviceId, VideoDeviceType deviceType, int deviceState, int deviceError) {
            if (deviceType == VideoDeviceType.VIDEO_DEVICE_TYPE_SCREEN_CAPTURE_DEVICE) {
                if (deviceState == MediaDeviceState.MEDIA_DEVICE_STATE_STARTED) {
                    mRTCRoom.publishScreen(MediaStreamType.RTC_MEDIA_STREAM_TYPE_BOTH);
                    mRTCVideo.setVideoSourceType(StreamIndex.STREAM_INDEX_SCREEN, VideoSourceType.VIDEO_SOURCE_TYPE_INTERNAL);
                    Log.i("progress","ScreenPublished");
                } else if (deviceState == MediaDeviceState.MEDIA_DEVICE_STATE_STOPPED
                        || deviceState == MediaDeviceState.MEDIA_DEVICE_STATE_RUNTIMEERROR) {
                    mRTCRoom.unpublishScreen(MediaStreamType.RTC_MEDIA_STREAM_TYPE_BOTH);
                    mRTCRoom.publishStream(MediaStreamType.RTC_MEDIA_STREAM_TYPE_VIDEO);
                    Log.i("progress","ScreenPublishCanceled");
                }
            }
        }

    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Intent intent = getIntent();
        mRoomId = intent.getStringExtra(Constants.ROOM_ID_EXTRA);
        String roomId = intent.getStringExtra(Constants.ROOM_ID_EXTRA);
        String userId = intent.getStringExtra(Constants.USER_ID_EXTRA);
        String token = intent.getStringExtra(Constants.TOKEN);

        initUI(roomId, userId);
        initEngineAndJoinRoom(roomId, userId, token);
        sendMessage(userId);

        mRecyclerView = findViewById(R.id.main_chat_rv);

        mRecyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                ChatActivityTimeLock.lock();
                mRecyclerView.setVisibility(View.VISIBLE);
                mRecyclerView.setAlpha(1);
                LastChatActivityTime = System.currentTimeMillis();
                ChatActivityTimeLock.unlock();
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                ChatActivityTimeLock.lock();
                mRecyclerView.setVisibility(View.VISIBLE);
                mRecyclerView.setAlpha(1);
                LastChatActivityTime = System.currentTimeMillis();
                ChatActivityTimeLock.unlock();
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

            }
        });

        mTask.execute();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRTCRoom.leaveRoom();
        mRTCRoom.destroy();
        mTask.cancel(true);
    }

    //光标和软键盘的问题
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mInputEt = findViewById(R.id.input_et);
        if (null != this.getCurrentFocus()) {
            InputMethodManager mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            assert mInputMethodManager != null;
            mInputEt.setCursorVisible(false);
            return mInputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
        }
        return super.onTouchEvent(event);
    }

    //处理发消息
    private void sendMessage(String userId){
        mRecyclerView = findViewById(R.id.main_chat_rv);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(mChatAdapter);

        mInputSendTv = findViewById(R.id.input_send);
        mInputEt = findViewById(R.id.input_et);

        mInputEt.setOnClickListener((v) -> {
            mInputEt.setCursorVisible(true);

            ChatActivityTimeLock.lock();
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAlpha(1);
            LastChatActivityTime = System.currentTimeMillis();
            ChatActivityTimeLock.unlock();
        });

        mInputSendTv.setOnClickListener( (view -> {
            Log.d("ChatActivityTimeLock", String.valueOf(ChatActivityTimeLock.getHoldCount()));
            ChatActivityTimeLock.lock();
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.setAlpha(1);
            LastChatActivityTime = System.currentTimeMillis();
            ChatActivityTimeLock.unlock();
            String inputMessage = mInputEt.getText().toString();
            if (inputMessage.equals("")) Toast.makeText(this,"Don't you want to say something?", Toast.LENGTH_SHORT).show();
            else {
                mChatAdapter.addChatMsg(userId + ": " + inputMessage);
                mRecyclerView.scrollToPosition(mChatAdapter.getItemCount()-1);
                mInputEt.setText("");
                mRTCRoom.sendRoomMessage(userId + ": " + inputMessage);
            }
        }));

    }

    private void initUI(String roomId, String userId) {
        mSelfContainer = findViewById(R.id.self_video_container);
        videoPlay = findViewById(R.id.videoPlay);
        //mVideoRV = findViewById(R.id.remote_rv);
        //inearLayoutManager layoutManager = new LinearLayoutManager(this);
        //layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        //mVideoRV.setLayoutManager(layoutManager);
        //mVideoRV.setAdapter(mVideoAdapter);

        //for (int i = 0; i < 7; i++) {
        //    mRemoteList.add(mRemoteContainerArray[i]);
        //}
        //mVideoAdapter.notifyItems(mRemoteList);

        mRemoteContainerArray[0] = findViewById(R.id.remote_video_0_container);
        mRemoteContainerArray[1] = findViewById(R.id.remote_video_1_container);
        mRemoteContainerArray[2] = findViewById(R.id.remote_video_2_container);
        mRemoteContainerArray[3] = findViewById(R.id.remote_video_3_container);
        mRemoteContainerArray[4] = findViewById(R.id.remote_video_4_container);
        mRemoteContainerArray[5] = findViewById(R.id.remote_video_5_container);
        mRemoteContainerArray[6] = findViewById(R.id.remote_video_6_container);

        mUserIdTvArray[0] = findViewById(R.id.remote_video_0_user_id_tv);
        mUserIdTvArray[1] = findViewById(R.id.remote_video_1_user_id_tv);
        mUserIdTvArray[2] = findViewById(R.id.remote_video_2_user_id_tv);
        mUserIdTvArray[3] = findViewById(R.id.remote_video_3_user_id_tv);
        mUserIdTvArray[4] = findViewById(R.id.remote_video_4_user_id_tv);
        mUserIdTvArray[5] = findViewById(R.id.remote_video_5_user_id_tv);
        mUserIdTvArray[6] = findViewById(R.id.remote_video_6_user_id_tv);

        findViewById(R.id.switch_camera).setOnClickListener((v) -> onSwitchCameraClick());
        //findViewById(R.id.selectVideo).setOnClickListener((v) -> selectVideo());
        findViewById(R.id.ScreenshareOffIV).setOnClickListener((v) -> endShareScreen());
        findViewById(R.id.ScreenshareOnIV).setOnClickListener((v) -> shareScreen());
        mSpeakerIv = findViewById(R.id.switch_audio_router);
        mAudioIv = findViewById(R.id.switch_local_audio);
        mVideoIv = findViewById(R.id.switch_local_video);
        findViewById(R.id.hang_up).setOnClickListener((v) -> onBackPressed());
        mSpeakerIv.setOnClickListener((v) -> updateSpeakerStatus());
        mAudioIv.setOnClickListener((v) -> updateLocalAudioStatus());
        mVideoIv.setOnClickListener((v) -> updateLocalVideoStatus());
        TextView roomIDTV = findViewById(R.id.room_id_text);
        TextView userIDTV = findViewById(R.id.self_video_user_id_tv);
        roomIDTV.setText(String.format("RoomID:%s", roomId));
        userIDTV.setText(String.format("%s", userId));
    }

    private void stopScreenShare() {
        // 停止屏幕数据采集
        mRTCVideo.stopScreenCapture();
        mRTCVideo.startVideoCapture();
        Log.i("progress","stop_step_one");
    }

    private void selectVideo(){
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_VIDEO_CODE);
    }

    private void shareScreen(){
        requestForScreenSharing();
    }

    private void endShareScreen(){
        stopScreenShare();
        FrameLayout fl = findViewById(R.id.ShareScreenFL);
        fl.removeAllViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_VIDEO_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            ContentResolver cr = this.getContentResolver();
            Cursor cursor = cr.query(uri, null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    String videoPath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));

                    ActivityCompat.requestPermissions(RTCRoomActivity.this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            1);

                    videoPlay.setVisibility(View.VISIBLE);
                    videoPlay.setVideoPath(videoPath);
                    mediaController = new MediaController(this);
                    videoPlay.setMediaController(mediaController);
                    videoPlay.requestFocus();
                }
                cursor.close();
            }
        }
        else if (requestCode == REQUEST_CODE_OF_SCREEN_SHARING && resultCode == Activity.RESULT_OK) {
            mRTCVideo.stopVideoCapture();
            startScreenShare(data);
            mRTCRoom.unpublishStream(MediaStreamType.RTC_MEDIA_STREAM_TYPE_VIDEO);
            Log.i("progress","mRTCRoom.unpublishStream");
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void initEngineAndJoinRoom(String roomId, String userId, String token) {
        // 创建引擎
        mRTCVideo = RTCVideo.createRTCVideo(getApplicationContext(), Constants.APPID, mIRtcVideoEventHandler, null, null);
        // 设置视频发布参数
        VideoEncoderConfig videoEncoderConfig = new VideoEncoderConfig(360, 640, 15, 800);
        mRTCVideo.setVideoEncoderConfig(videoEncoderConfig);
        setLocalRenderView(userId);
        // 开启本地视频采集
        mRTCVideo.startVideoCapture();
        // 开启本地音频采集
        mRTCVideo.startAudioCapture();
        // 加入房间
        mRTCRoom = mRTCVideo.createRTCRoom(roomId);
        mRTCRoom.setRTCRoomEventHandler(mIRtcRoomEventHandler);
        RTCRoomConfig roomConfig = new RTCRoomConfig(ChannelProfile.CHANNEL_PROFILE_COMMUNICATION,
                true, true, true);
        int joinRoomRes = mRTCRoom.joinRoom(token,
                UserInfo.create(userId, ""), roomConfig);
        Log.i("TAG", "initEngineAndJoinRoom: " + joinRoomRes);
    }

    private void setLocalRenderView(String uid) {
        VideoCanvas videoCanvas = new VideoCanvas();
        TextureView renderView = new TextureView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mSelfContainer.removeAllViews();
        mSelfContainer.addView(renderView, params);
        videoCanvas.renderView = renderView;
        videoCanvas.uid = uid;
        videoCanvas.isScreen = false;
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
        // 设置本地视频渲染视图
        mRTCVideo.setLocalVideoCanvas(StreamIndex.STREAM_INDEX_MAIN, videoCanvas);
    }

    private void setRemoteRenderView(String roomId, String uid, FrameLayout container) {
        TextureView renderView = new TextureView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        container.removeAllViews();
        container.addView(renderView, params);

        VideoCanvas videoCanvas = new VideoCanvas();
        videoCanvas.renderView = renderView;
        videoCanvas.roomId = roomId;
        videoCanvas.uid = uid;
        videoCanvas.isScreen = false;
        videoCanvas.renderMode = VideoCanvas.RENDER_MODE_HIDDEN;
        // 设置远端用户视频渲染视图
        mRTCVideo.setRemoteVideoCanvas(uid, StreamIndex.STREAM_INDEX_MAIN, videoCanvas);
    }

    private final RemoteStreamKey[] mShowRemoteStreamArray = new RemoteStreamKey[7];
    private void setRemoteView_with_share(RemoteStreamKey remoteStreamKey) {
        String uid = remoteStreamKey.getUserId();
        if (TextUtils.isEmpty(uid)) return;
        int emptyInx = -1;
        for (int i = 0; i < mShowUidArray.length; i++) {
            if (TextUtils.isEmpty(mShowUidArray[i]) && emptyInx == -1 || TextUtils.equals(mShowUidArray[i], uid)) {
                emptyInx = i;
                break;
            } else if (TextUtils.equals(uid, mShowUidArray[i])) {
                return;
            }
        }
        if (emptyInx < 0) {
            return;
        }
        mShowUidArray[emptyInx] = uid;
        mUserIdTvArray[emptyInx].setText(uid);
        mShowRemoteStreamArray[emptyInx] = remoteStreamKey;
        boolean sharingScreen = remoteStreamKey.getStreamIndex() == StreamIndex.STREAM_INDEX_SCREEN;
        // mUserIdTvArray[renderIndex].setText(sharingScreen ? String.format("%s屏幕分享", uid) : String.format("%s", uid));
        setScreenRemoteRenderView(remoteStreamKey, mRemoteContainerArray[emptyInx]);
    }

    private void setRemoteView_with_share(RemoteStreamKey remoteStreamKey, FrameLayout fl) {
        String uid = remoteStreamKey.getUserId();
        if (TextUtils.isEmpty(uid)) return;
        int emptyInx = -1;
        for (int i = 0; i < mShowUidArray.length; i++) {
            if (TextUtils.isEmpty(mShowUidArray[i]) && emptyInx == -1 || TextUtils.equals(mShowUidArray[i], uid)) {
                emptyInx = i;
                break;
            } else if (TextUtils.equals(uid, mShowUidArray[i])) {
                return;
            }
        }
        if (emptyInx < 0) {
            return;
        }
        mShowUidArray[emptyInx] = uid;
        mUserIdTvArray[emptyInx].setText(uid);
        mShowRemoteStreamArray[emptyInx] = remoteStreamKey;
        boolean sharingScreen = remoteStreamKey.getStreamIndex() == StreamIndex.STREAM_INDEX_SCREEN;
        // mUserIdTvArray[renderIndex].setText(sharingScreen ? String.format("%s屏幕分享", uid) : String.format("%s", uid));
        setScreenRemoteRenderView(remoteStreamKey, mRemoteContainerArray[emptyInx]);
        setScreenRemoteRenderView(remoteStreamKey, fl);
}

    private void setScreenRemoteRenderView(RemoteStreamKey remoteStreamKey, FrameLayout container){
        IVideoSink videoSink = new CustomRenderView(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        container.removeAllViews();
        container.addView((View) videoSink, params);
        mRTCVideo.setRemoteVideoSink(remoteStreamKey, videoSink, IVideoSink.PixelFormat.I420);

    }

    private void setRemoteView(String roomId, String uid) {
        int emptyInx = -1;
        for (int i = 0; i < mShowUidArray.length; i++) {
            if (TextUtils.isEmpty(mShowUidArray[i]) && emptyInx == -1) {
                emptyInx = i;
            } else if (TextUtils.equals(uid, mShowUidArray[i])) {
                return;
            }
        }
        if (emptyInx < 0) {
            return;
        }
        mShowUidArray[emptyInx] = uid;
        mUserIdTvArray[emptyInx].setText(uid);
        setRemoteRenderView(roomId, uid, mRemoteContainerArray[emptyInx]);
    }

    private void removeRemoteView(String uid) {
        for (int i = 0; i < mShowUidArray.length; i++) {
            if (TextUtils.equals(uid, mShowUidArray[i])) {
                mShowUidArray[i] = null;
                mUserIdTvArray[i].setText(null);
                mRemoteContainerArray[i].removeAllViews();
            }
        }
    }

    private void onSwitchCameraClick() {
        // 切换前置/后置摄像头（默认使用前置摄像头）
        if (mCameraID.equals(CameraId.CAMERA_ID_FRONT)) {
            mCameraID = CameraId.CAMERA_ID_BACK;
        } else {
            mCameraID = CameraId.CAMERA_ID_FRONT;
        }
        mRTCVideo.switchCamera(mCameraID);
    }

    private void updateSpeakerStatus() {
        mIsSpeakerPhone = !mIsSpeakerPhone;
        // 设置使用哪种方式播放音频数据
        mRTCVideo.setAudioRoute(mIsSpeakerPhone ? AudioRoute.AUDIO_ROUTE_SPEAKERPHONE
                : AudioRoute.AUDIO_ROUTE_EARPIECE);
        mSpeakerIv.setImageResource(mIsSpeakerPhone ? R.drawable.speaker_on : R.drawable.speaker_off);
    }

    private void updateLocalAudioStatus() {
        mIsMuteAudio = !mIsMuteAudio;
        // 开启/关闭本地音频发送
        if (mIsMuteAudio) {
            mRTCRoom.unpublishStream(MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO);
        } else {
            mRTCRoom.publishStream(MediaStreamType.RTC_MEDIA_STREAM_TYPE_AUDIO);
        }
        mAudioIv.setImageResource(mIsMuteAudio ? R.drawable.mute_audio : R.drawable.normal_audio);
    }

    private void updateLocalVideoStatus() {
        mIsMuteVideo = !mIsMuteVideo;
        if (mIsMuteVideo) {
            // 关闭视频采集
            mRTCVideo.stopVideoCapture();
        } else {
            // 开启视频采集
            mRTCVideo.startVideoCapture();
        }
        mVideoIv.setImageResource(mIsMuteVideo ? R.drawable.mute_video : R.drawable.normal_video);
    }

    private void showAlertDialog(String message) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setPositiveButton("知道了", (dialog, which) -> dialog.dismiss());
            builder.create().show();
        });
    }

    @Override
    public void finish() {
        super.finish();
        // 离开房间
        if (mRTCRoom != null) {
            mRTCRoom.leaveRoom();
            mRTCRoom.destroy();
        }
        mRTCRoom = null;
        // 销毁引擎
        RTCVideo.destroyRTCVideo();
        mIRtcVideoEventHandler = null;
        mIRtcRoomEventHandler = null;
        mRTCVideo = null;
    }

    private void showMessage(String uid, String message) {
        runOnUiThread(() -> {
            mChatAdapter.addChatMsg(message);
            //mRecyclerView.scrollToPosition(mChatAdapter.getItemCount()-1);
        });
    }
}