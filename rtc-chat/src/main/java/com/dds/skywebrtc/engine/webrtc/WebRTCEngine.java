package com.dds.skywebrtc.engine.webrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.dds.skywebrtc.EnumType;
import com.dds.skywebrtc.engine.EngineCallback;
import com.dds.skywebrtc.engine.IEngine;
import com.dds.skywebrtc.log.SkyLog;
import com.dds.skywebrtc.render.ProxyVideoSink;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.media.MediaRecorder;
import android.view.Surface;
import java.io.File;


public class WebRTCEngine implements IEngine, Peer.IPeerEvent {
    private final static String TAG = SkyLog.createTag(WebRTCEngine.class.getSimpleName());
    private MediaRecorder mediaRecorder;
    private Surface mediaRecorderSurface;
    private String currentRecordingFilePath;
    private boolean isRecording = false;
    private PeerConnectionFactory _factory;
    private EglBase mRootEglBase;

    // stream
//    private MediaStream _localStream;

    // video
    private VideoTrack _localVideoTrack;
    private VideoSource videoSource;
    private VideoCapturer captureAndroid;
    private SurfaceTextureHelper surfaceTextureHelper;
    // audio
    private AudioSource audioSource;
    private AudioTrack _localAudioTrack;
    private SurfaceViewRenderer localRenderer;

    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int FPS = 30;

    // 对话实例列表
    private final ConcurrentHashMap<String, Peer> peers = new ConcurrentHashMap<>();
    // 服务器实例列表
    private final List<PeerConnection.IceServer> iceServers = new ArrayList<>();

    private EngineCallback mCallback;

    public boolean mIsAudioOnly;
    private final Context mContext;
    private final AudioManager audioManager;
    private boolean isSpeakerOn = true;
    private ProxyVideoSink mediaRecorderVideoSink;


    public WebRTCEngine(boolean mIsAudioOnly, Context mContext) {
        this.mIsAudioOnly = mIsAudioOnly;
        this.mContext = mContext;
        audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        // 初始化ice地址
        initIceServer();
    }


    // -----------------------------------对外方法------------------------------------------
    @Override
    public void init(EngineCallback callback) {
        mCallback = callback;

        if (mRootEglBase == null) {
            mRootEglBase = EglBase.create();
        }
        if (_factory == null) {
            _factory = createConnectionFactory();
        }
        createLocalStream();
    }

    @Override
    public void joinRoom(List<String> userIds) {
        Log.d(TAG, "joinRoom: " + userIds.toString());
        for (String id : userIds) {
            // create Peer
            Peer peer = new Peer(_factory, iceServers, id, this);
            peer.setOffer(false);
            // add localStream
//            peer.addLocalStream(_localStream);
            List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
            if (_localVideoTrack != null) {
                peer.addVideoTrack(_localVideoTrack, mediaStreamLabels);
            }
            if (_localAudioTrack != null) {
                peer.addAudioTrack(_localAudioTrack, mediaStreamLabels);
            }
            // 添加列表
            peers.put(id, peer);
        }
        if (mCallback != null) {
            mCallback.joinRoomSucc();
        }

        if (isHeadphonesPlugged()) {
            toggleHeadset(true);
        } else {
            if (mIsAudioOnly)
                toggleSpeaker(false);
            else {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            }
        }
    }

    @Override
    public void userIn(String userId) {
        Log.d(TAG, "userIn: " + userId);
        // create Peer
        Peer peer = new Peer(_factory, iceServers, userId, this);
        peer.setOffer(true);
        // add localStream
        List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
        if (_localVideoTrack != null) {
            peer.addVideoTrack(_localVideoTrack, mediaStreamLabels);
        }
        if (_localAudioTrack != null) {
            peer.addAudioTrack(_localAudioTrack, mediaStreamLabels);
        }
        // 添加列表
        peers.put(userId, peer);
        // createOffer
        peer.createOffer();
    }

    @Override
    public void userReject(String userId, int type) {
        //拒绝接听userId应该是没有添加进peers里去不需要remove
//       Peer peer = peers.get(userId);
//        if (peer != null) {
//            peer.close();
//            peers.remove(userId);
//        }
//        if (peers.size() == 0) {

        if (mCallback != null) {
            mCallback.reject(type);
        }
//        }
    }

    @Override
    public void disconnected(String userId, EnumType.CallEndReason reason) {
        if (mCallback != null) {
            mCallback.disconnected(reason);
        }
    }

    @Override
    public void receiveOffer(String userId, String description) {
        Peer peer = peers.get(userId);
        if (peer != null) {
            SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER, description);
            peer.setOffer(false);
            peer.setRemoteDescription(sdp);
            peer.createAnswer();
        }


    }

    @Override
    public void receiveAnswer(String userId, String sdp) {
        Log.d(TAG, "receiveAnswer--" + userId);
        Peer peer = peers.get(userId);
        if (peer != null) {
            SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
            peer.setRemoteDescription(sessionDescription);
        }


    }

    @Override
    public void receiveIceCandidate(String userId, String id, int label, String candidate) {
        Log.d(TAG, "receiveIceCandidate--" + userId);
        Peer peer = peers.get(userId);
        if (peer != null) {
            IceCandidate iceCandidate = new IceCandidate(id, label, candidate);
            peer.addRemoteIceCandidate(iceCandidate);

        }
    }

    @Override
    public void leaveRoom(String userId) {
        Peer peer = peers.get(userId);
        if (peer != null) {
            peer.close();
            peers.remove(userId);
        }
        Log.d(TAG, "leaveRoom peers.size() = " + peers.size() + "; mCallback = " + mCallback);
        if (peers.size() <= 1) {
            if (mCallback != null) {
                mCallback.exitRoom();
            }
            if (peers.size() == 1) {
                for (Map.Entry<String, Peer> set : peers.entrySet()) {
                    set.getValue().close();
                }
                peers.clear();
            }
        }


    }

    @Override
    public View setupLocalPreview(boolean isOverlay) {
        if (mRootEglBase == null) {
            return null;
        }
        localRenderer = new SurfaceViewRenderer(mContext);
        localRenderer.init(mRootEglBase.getEglBaseContext(), null);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRenderer.setMirror(true);
        localRenderer.setZOrderMediaOverlay(isOverlay);

        ProxyVideoSink localSink = new ProxyVideoSink();
        localSink.setTarget(localRenderer);
        if (_localVideoTrack != null) {
            _localVideoTrack.addSink(localSink);
        }
        return localRenderer;
    }

    @Override
    public void stopPreview() {
        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }
        // 释放摄像头
        if (captureAndroid != null) {
            try {
                captureAndroid.stopCapture();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            captureAndroid.dispose();
            captureAndroid = null;
        }
        // 释放画布
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
            surfaceTextureHelper = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (_localVideoTrack != null && mediaRecorderVideoSink != null) {
            _localVideoTrack.removeSink(mediaRecorderVideoSink);
            mediaRecorderVideoSink = null;
        }
        if (isRecording) {
            stopRecording();
        }

        if (localRenderer != null) {
            localRenderer.release();
            localRenderer = null;
        }


    }

    @Override
    public void startStream() {

    }

    @Override
    public void stopStream() {

    }

    @Override
    public View setupRemoteVideo(String userId, boolean isOverlay) {
        if (TextUtils.isEmpty(userId)) {
            Log.e(TAG, "setupRemoteVideo userId is null ");
            return null;
        }
        Peer peer = peers.get(userId);
        if (peer == null) return null;

        if (peer.renderer == null) {
            peer.createRender(mRootEglBase, mContext, isOverlay);
        }

        return peer.renderer;

    }

    @Override
    public void stopRemoteVideo() {

    }

    private boolean isSwitch = false; // 是否正在切换摄像头

    @Override
    public void switchCamera() {
        if (isSwitch) return;
        isSwitch = true;
        if (captureAndroid == null) return;
        if (captureAndroid instanceof CameraVideoCapturer) {
            CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) captureAndroid;
            try {
                cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean isFrontCamera) {
                        isSwitch = false;
                    }

                    @Override
                    public void onCameraSwitchError(String errorDescription) {
                        isSwitch = false;
                    }
                });
            } catch (Exception e) {
                isSwitch = false;
            }
        } else {
            Log.d(TAG, "Will not switch camera, video caputurer is not a camera");
        }
    }

    @Override
    public boolean muteAudio(boolean enable) {
        if (_localAudioTrack != null) {
            _localAudioTrack.setEnabled(!enable);
            return true;
        }
        return false;
    }

    @Override
    public boolean toggleSpeaker(boolean enable) {
        if (audioManager != null) {
            isSpeakerOn = enable;
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            if (enable) {
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL),
                        AudioManager.FX_KEY_CLICK);
                audioManager.setSpeakerphoneOn(true);
            } else {
                //5.0以上
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //设置mode
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                } else {
                    //设置mode
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                }
                //设置音量，解决有些机型切换后没声音或者声音突然变大的问题
                audioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL),
                        AudioManager.FX_KEY_CLICK
                );
                audioManager.setSpeakerphoneOn(false);
            }
            return true;
        }
        return false;

    }

    @Override
    public boolean toggleHeadset(boolean isHeadset) {
        if (audioManager != null) {
            if (isHeadset) {
                //5.0以上
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    //设置mode
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                } else {
                    //设置mode
                    audioManager.setMode(AudioManager.MODE_IN_CALL);
                }
                audioManager.setSpeakerphoneOn(false);
            } else {
                if (mIsAudioOnly) {
                    toggleSpeaker(isSpeakerOn);
                }
            }
        }
        return false;
    }

    private boolean isHeadphonesPlugged() {
        if (audioManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo deviceInfo : audioDevices) {
                if (deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                    return true;
                }
            }
            return false;
        } else {
            return audioManager.isWiredHeadsetOn();
        }
    }

    @Override
    public void release() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        // 清空peer
        if (peers != null) {
            for (Peer peer : peers.values()) {
                peer.close();
            }
            peers.clear();
        }


        // 停止预览
        stopPreview();

        if (isRecording) {
            stopRecording(); // Ensure recording is stopped and resources are released
        }
        if (_localVideoTrack != null && mediaRecorderVideoSink != null) {
            _localVideoTrack.removeSink(mediaRecorderVideoSink);
            mediaRecorderVideoSink = null;
        }


        if (_factory != null) {
            _factory.dispose();
            _factory = null;
        }

        if (mRootEglBase != null) {
            mRootEglBase.release();
            mRootEglBase = null;
        }


    }

    // -----------------------------其他方法--------------------------------

    private void initIceServer() {
        // 初始化一些stun和turn的地址
        PeerConnection.IceServer var1 = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer();
        iceServers.add(var1);

        PeerConnection.IceServer var11 = PeerConnection.IceServer.builder("stun:42.192.40.58:3478")
                .createIceServer();
        PeerConnection.IceServer var12 = PeerConnection.IceServer.builder("turn:42.192.40.58:3478")
                .setUsername("ddssingsong")
                .setPassword("123456")
                .createIceServer();
        iceServers.add(var11);
        iceServers.add(var12);
    }

    /**
     * 构造PeerConnectionFactory
     *
     * @return PeerConnectionFactory
     */
    public PeerConnectionFactory createConnectionFactory() {
        // 1. 初始化的方法，必须在开始之前调用
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory
                .InitializationOptions
                .builder(mContext)
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);
        // 2. 设置编解码方式：默认方法
        final VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(),
                true,
                true);
        final VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        // 3. 构造Factory
        AudioDeviceModule audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        return PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();
    }

    /**
     * 创建本地流
     */
    public void createLocalStream() {

        // 音频
        audioSource = _factory.createAudioSource(createAudioConstraints());
        _localAudioTrack = _factory.createAudioTrack(AUDIO_TRACK_ID, audioSource);

        // 视频
        if (!mIsAudioOnly) {
            captureAndroid = createVideoCapture();
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            videoSource = _factory.createVideoSource(captureAndroid.isScreencast());
            captureAndroid.initialize(surfaceTextureHelper, mContext, videoSource.getCapturerObserver());
            captureAndroid.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);
            _localVideoTrack = _factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        }

    }

    public void startRecording(String filePath) {
        if (isRecording) {
            Log.w(TAG, "Recording is already in progress.");
            return;
        }

        if (_localVideoTrack == null && !mIsAudioOnly) { // Only critical if video is expected
            Log.e(TAG, "Local video track is not available for video recording.");
            return;
        }

        Log.d(TAG, "startRecording called. Attempting to record to filePath: " + filePath);
        File outputFile = new File(filePath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs();
            if (dirCreated) {
                Log.i(TAG, "Recording directory created: " + parentDir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create recording directory: " + parentDir.getAbsolutePath());
                // Consider returning or throwing an exception as setOutputFile will likely fail
                return;
            }
        } else if (parentDir == null) {
             Log.e(TAG, "Parent directory is null for filePath: " + filePath);
             // Consider returning or throwing an exception
             return;
        }
        currentRecordingFilePath = filePath; // Set early for releaseMediaRecorderOnError

        try {
            mediaRecorder = new MediaRecorder();
            Log.i(TAG, "MediaRecorder new instance created.");

            // 设置音频源
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION);
            Log.i(TAG, "Audio source set to VOICE_COMMUNICATION.");

            // 设置视频源 (如果不是仅音频)
            if (!mIsAudioOnly) {
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                Log.i(TAG, "Video source set to SURFACE.");
            }

            // 设置输出格式
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            Log.i(TAG, "Output format set to MPEG_4.");

            // 设置输出文件
            mediaRecorder.setOutputFile(filePath); // Use the validated filePath
            Log.i(TAG, "Output file set to: " + filePath);

            // 设置音频编码器
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            Log.i(TAG, "Audio encoder set to AAC.");

            // 设置视频编码器、尺寸、帧率等 (如果不是仅音频)
            if (!mIsAudioOnly) {
                mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                Log.i(TAG, "Video encoder set to H264.");
                mediaRecorder.setVideoSize(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT);
                Log.i(TAG, "Video size set to " + VIDEO_RESOLUTION_WIDTH + "x" + VIDEO_RESOLUTION_HEIGHT);
                mediaRecorder.setVideoFrameRate(FPS);
                Log.i(TAG, "Video frame rate set to " + FPS);
                mediaRecorder.setVideoEncodingBitRate(2000 * 1000); // 例如 2Mbps
                Log.i(TAG, "Video encoding bit rate set to 2000kbps.");
                // mediaRecorder.setOrientationHint(0); // 可选，根据需要设置
            }

            Log.i(TAG, "MediaRecorder configuration complete. Calling prepare().");
            mediaRecorder.prepare();
            Log.i(TAG, "MediaRecorder prepare() successful.");

            // 连接 WebRTC 视频轨道到 MediaRecorder Surface (如果不是仅音频)
            if (!mIsAudioOnly && _localVideoTrack != null) {
                Log.i(TAG, "Attempting to connect WebRTC video track to MediaRecorder surface.");
                mediaRecorderSurface = mediaRecorder.getSurface();
                if (mediaRecorderSurface == null) {
                    Log.e(TAG, "MediaRecorder.getSurface() returned null. Cannot record video.");
                    releaseMediaRecorderOnError(); // Use helper to release
                    return;
                }
                mediaRecorderVideoSink = new ProxyVideoSink();
                mediaRecorderVideoSink.setTarget(mediaRecorderSurface);
                _localVideoTrack.addSink(mediaRecorderVideoSink);
                Log.i(TAG, "VideoTrack added to MediaRecorder sink. Surface: " + mediaRecorderSurface);
            } else if (!mIsAudioOnly) {
                Log.w(TAG, "_localVideoTrack is null, cannot record video stream.");
                // This might be an acceptable state if only audio is desired despite mIsAudioOnly being false
                // Or it could be an error state. For now, we proceed to record audio only.
            }

            mediaRecorder.start();
            Log.i(TAG, "MediaRecorder start() successful. Recording started.");
            isRecording = true;
            // ... (通知UI等)

        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaRecorder IllegalStateException in startRecording: " + e.getMessage(), e);
            releaseMediaRecorderOnError();
        } catch (java.io.IOException e) {
            Log.e(TAG, "MediaRecorder IOException in prepare(): " + e.getMessage(), e);
            releaseMediaRecorderOnError();
        } catch (Exception e) { // Catch any other runtime exceptions
            Log.e(TAG, "MediaRecorder Generic Exception in startRecording: " + e.getMessage(), e);
            releaseMediaRecorderOnError();
        }
    }

    public void stopRecording() {
        Log.i(TAG, "stopRecording called.");
        if (!isRecording || mediaRecorder == null) {
            Log.w(TAG, "Not recording or mediaRecorder is null. Ignoring stopRecording call.");
            return;
        }

        try {
            // 移除视频轨道 sink (如果存在)
            if (!mIsAudioOnly && _localVideoTrack != null && mediaRecorderVideoSink != null) {
                _localVideoTrack.removeSink(mediaRecorderVideoSink);
                mediaRecorderVideoSink.setTarget(null); // 清理 target
                mediaRecorderVideoSink = null;
                Log.i(TAG, "VideoTrack removed from MediaRecorder sink.");
            }
            if (mediaRecorderSurface != null) {
                mediaRecorderSurface.release(); // 释放 Surface
                mediaRecorderSurface = null;
                Log.i(TAG, "MediaRecorder surface released.");
            }

            mediaRecorder.stop();
            Log.i(TAG, "MediaRecorder stop() successful.");
            mediaRecorder.reset();
            Log.i(TAG, "MediaRecorder reset() successful.");
            mediaRecorder.release();
            Log.i(TAG, "MediaRecorder release() successful.");
            mediaRecorder = null; // Important: set to null after release
            isRecording = false;
            Log.i(TAG, "Recording stopped and MediaRecorder released. File saved at: " + currentRecordingFilePath);
            // ... (通知UI文件已保存等)

        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaRecorder IllegalStateException in stopRecording: " + e.getMessage(), e);
            // 即使出错，也尝试清理资源
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ex) {
                    Log.e(TAG, "Exception during reset/release in stopRecording error handling: " + ex.getMessage(), ex);
                }
                mediaRecorder = null;
            }
            isRecording = false; // 重置状态
        } catch (Exception e) { // Catch any other runtime exceptions
            Log.e(TAG, "MediaRecorder Generic Exception in stopRecording: " + e.getMessage(), e);
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ex) {
                     Log.e(TAG, "Exception during reset/release in stopRecording error handling: " + ex.getMessage(), ex);
                }
                mediaRecorder = null;
            }
            isRecording = false;
        }
        currentRecordingFilePath = null; // 清理路径
    }


    private void releaseMediaRecorderOnError() {
        Log.w(TAG, "Releasing MediaRecorder due to an error.");
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Exception during MediaRecorder.reset/release in error handling: " + e.getMessage(), e);
            }
            mediaRecorder = null;
        }
        // 确保 video sink 也被清理
        if (!mIsAudioOnly && _localVideoTrack != null && mediaRecorderVideoSink != null) {
             _localVideoTrack.removeSink(mediaRecorderVideoSink);
             mediaRecorderVideoSink.setTarget(null);
             mediaRecorderVideoSink = null;
             Log.i(TAG, "VideoTrack removed from MediaRecorder sink during error handling.");
        }
        if (mediaRecorderSurface != null) {
            mediaRecorderSurface.release();
            mediaRecorderSurface = null;
            Log.i(TAG, "MediaRecorder surface released during error handling.");
        }
        isRecording = false;
        currentRecordingFilePath = null;
    }


    /**
     * 创建媒体方式
     *
     * @return VideoCapturer
     */
    private VideoCapturer createVideoCapture() {
        VideoCapturer videoCapturer;
        if (Camera2Enumerator.isSupported(mContext)) {
            videoCapturer = createCameraCapture(new Camera2Enumerator(mContext));
        } else {
            videoCapturer = createCameraCapture(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    /**
     * 创建相机媒体流
     */
    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    //**************************************各种约束******************************************/
    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";

    // 配置音频参数
    private MediaConstraints createAudioConstraints() {
        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "false"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));
        return audioConstraints;
    }

    //------------------------------------回调---------------------------------------------
    @Override
    public void onSendIceCandidate(String userId, IceCandidate candidate) {
        if (mCallback != null) {
            mCallback.onSendIceCandidate(userId, candidate);
        }

    }

    @Override
    public void onSendOffer(String userId, SessionDescription description) {
        if (mCallback != null) {
            mCallback.onSendOffer(userId, description);
        }
    }

    @Override
    public void onSendAnswer(String userId, SessionDescription description) {
        if (mCallback != null) {
            mCallback.onSendAnswer(userId, description);
        }
    }

    @Override
    public void onRemoteStream(String userId, MediaStream stream) {
        if (mCallback != null) {
            mCallback.onRemoteStream(userId);
        }
    }

    @Override
    public void onRemoveStream(String userId, MediaStream stream) {
        leaveRoom(userId);
    }

    @Override
    public void onDisconnected(String userId) {
        if (mCallback != null) {
            Log.d(TAG, "onDisconnected mCallback != null");
            mCallback.onDisconnected(userId);
        } else {
            Log.d(TAG, "onDisconnected mCallback == null");
        }
    }

}
