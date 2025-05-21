package com.dds.core.voip;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.Chronometer;
import android.widget.Toast;
import android.os.Environment;
import java.io.File;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import com.dds.webrtc.BuildConfig;


import androidx.annotation.NonNull;

import com.dds.core.util.BarUtils;
import com.dds.core.util.OSUtils;
import com.dds.skywebrtc.CallSession;
import com.dds.skywebrtc.EnumType.CallState;
import com.dds.skywebrtc.SkyEngineKit;
import com.dds.webrtc.R;

import org.webrtc.SurfaceViewRenderer;

/**
 * Created by dds on 2018/7/26.
 * android_shuai@163.com
 * 视频通话控制界面
 */
public class FragmentVideo extends SingleCallFragment implements View.OnClickListener {
    private static final String TAG = "FragmentVideo";
    private ImageView outgoingAudioOnlyImageView;
    private LinearLayout audioLayout;
    private ImageView incomingAudioOnlyImageView;
    private LinearLayout hangupLinearLayout;
    private LinearLayout acceptLinearLayout;
    private ImageView connectedAudioOnlyImageView;
    private ImageView connectedHangupImageView;
    private ImageView switchCameraImageView;
    private FrameLayout fullscreenRenderer;
    private FrameLayout pipRenderer;
    private LinearLayout inviteeInfoContainer;
    private boolean isFromFloatingView = false;
    private SurfaceViewRenderer localSurfaceView;
    private SurfaceViewRenderer remoteSurfaceView;

    private ImageButton recordButtonVideo;
    private Chronometer recordingTimerVideo;
    private boolean isRecording = false;
    private String currentRecordingFilePath;


    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (callSingleActivity != null) {
            isFromFloatingView = callSingleActivity.isFromFloatingView();
        }
    }

    @Override
    int getLayout() {
        return R.layout.fragment_video;
    }

    @Override
    public void initView(View view) {
        super.initView(view);
        fullscreenRenderer = view.findViewById(R.id.fullscreen_video_view);
        pipRenderer = view.findViewById(R.id.pip_video_view);
        inviteeInfoContainer = view.findViewById(R.id.inviteeInfoContainer);
        outgoingAudioOnlyImageView = view.findViewById(R.id.outgoingAudioOnlyImageView);
        audioLayout = view.findViewById(R.id.audioLayout);
        incomingAudioOnlyImageView = view.findViewById(R.id.incomingAudioOnlyImageView);
        hangupLinearLayout = view.findViewById(R.id.hangupLinearLayout);
        acceptLinearLayout = view.findViewById(R.id.acceptLinearLayout);
        connectedAudioOnlyImageView = view.findViewById(R.id.connectedAudioOnlyImageView);
        connectedHangupImageView = view.findViewById(R.id.connectedHangupImageView);
        switchCameraImageView = view.findViewById(R.id.switchCameraImageView);

        recordButtonVideo = view.findViewById(R.id.recordButtonVideo);
        recordingTimerVideo = view.findViewById(R.id.recordingTimerVideo);
        if (recordButtonVideo != null) {
            recordButtonVideo.setOnClickListener(this);
        }


        outgoingHangupImageView.setOnClickListener(this);
        incomingHangupImageView.setOnClickListener(this);
        minimizeImageView.setOnClickListener(this);
        connectedHangupImageView.setOnClickListener(this);
        acceptImageView.setOnClickListener(this);
        switchCameraImageView.setOnClickListener(this);
        pipRenderer.setOnClickListener(this);
        outgoingAudioOnlyImageView.setOnClickListener(this);
        incomingAudioOnlyImageView.setOnClickListener(this);
        connectedAudioOnlyImageView.setOnClickListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M || OSUtils.isMiui() || OSUtils.isFlyme()) {
            lytParent.post(() -> {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) inviteeInfoContainer.getLayoutParams();
                params.topMargin = (int) (BarUtils.getStatusBarHeight() * 1.2);
                inviteeInfoContainer.setLayoutParams(params);
                RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams) minimizeImageView.getLayoutParams();
                params1.topMargin = BarUtils.getStatusBarHeight();
                minimizeImageView.setLayoutParams(params1);
            });

            pipRenderer.post(() -> {
                FrameLayout.LayoutParams params2 = (FrameLayout.LayoutParams) pipRenderer.getLayoutParams();
                params2.topMargin = (int) (BarUtils.getStatusBarHeight() * 1.2);
                pipRenderer.setLayoutParams(params2);
            });
        }
//        if(isOutgoing){ //测试崩溃对方是否会停止
//            lytParent.postDelayed(() -> {
//                int i = 1 / 0;
//            }, 10000);
//        }

    }


    @Override
    public void init() {
        super.init();
        CallSession session = gEngineKit.getCurrentSession();
        if (session != null) {
            currentState = session.getState();
        }
        if (session == null || CallState.Idle == session.getState()) {
            if (callSingleActivity != null) {
                callSingleActivity.finish();
            }
        } else if (CallState.Connected == session.getState()) {
            incomingActionContainer.setVisibility(View.GONE);
            outgoingActionContainer.setVisibility(View.GONE);
            connectedActionContainer.setVisibility(View.VISIBLE);
            inviteeInfoContainer.setVisibility(View.GONE);
            minimizeImageView.setVisibility(View.VISIBLE);
            startRefreshTime();
        } else {
            if (isOutgoing) {
                incomingActionContainer.setVisibility(View.GONE);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                connectedActionContainer.setVisibility(View.GONE);
                descTextView.setText(R.string.av_waiting);
            } else {
                incomingActionContainer.setVisibility(View.VISIBLE);
                outgoingActionContainer.setVisibility(View.GONE);
                connectedActionContainer.setVisibility(View.GONE);
                descTextView.setText(R.string.av_video_invite);
                if (currentState == CallState.Incoming) {
                    View surfaceView = gEngineKit.getCurrentSession().setupLocalVideo(false);
                    Log.d(TAG, "init surfaceView != null is " + (surfaceView != null) + "; isOutgoing = " + isOutgoing + "; currentState = " + currentState);
                    if (surfaceView != null) {
                        localSurfaceView = (SurfaceViewRenderer) surfaceView;
                        localSurfaceView.setZOrderMediaOverlay(false);
                        fullscreenRenderer.addView(localSurfaceView);
                    }
                }
            }
        }
        if (isFromFloatingView) {
            didCreateLocalVideoTrack();
            if (session != null) {
                didReceiveRemoteVideoTrack(session.mTargetId);
            }
        }
    }

    @Override
    public void didChangeState(CallState state) {
        currentState = state;
        Log.d(TAG, "didChangeState, state = " + state);
        runOnUiThread(() -> {
            if (state == CallState.Connected) {

                incomingActionContainer.setVisibility(View.GONE);
                outgoingActionContainer.setVisibility(View.GONE);
                connectedActionContainer.setVisibility(View.VISIBLE);
                inviteeInfoContainer.setVisibility(View.GONE);
                descTextView.setVisibility(View.GONE);
                minimizeImageView.setVisibility(View.VISIBLE);
                // 开启计时器
                startRefreshTime();
            } else {
                // do nothing now
            }
        });
    }

    @Override
    public void didChangeMode(Boolean isAudio) {
        runOnUiThread(() -> callSingleActivity.switchAudio());
    }


    @Override
    public void didCreateLocalVideoTrack() {
        if (localSurfaceView == null) {
            View surfaceView = gEngineKit.getCurrentSession().setupLocalVideo(true);
            if (surfaceView != null) {
                localSurfaceView = (SurfaceViewRenderer) surfaceView;
            } else {
                if (callSingleActivity != null) callSingleActivity.finish();
                return;
            }
        } else {
            localSurfaceView.setZOrderMediaOverlay(true);
        }
        Log.d(TAG,
                "didCreateLocalVideoTrack localSurfaceView != null is " + (localSurfaceView != null) + "; remoteSurfaceView == null = " + (remoteSurfaceView == null)
        );

        if (localSurfaceView.getParent() != null) {
            ((ViewGroup) localSurfaceView.getParent()).removeView(localSurfaceView);
        }
        if (isOutgoing && remoteSurfaceView == null) {
            if (fullscreenRenderer != null && fullscreenRenderer.getChildCount() != 0)
                fullscreenRenderer.removeAllViews();
            fullscreenRenderer.addView(localSurfaceView);
        } else {
            if (pipRenderer.getChildCount() != 0) pipRenderer.removeAllViews();
            pipRenderer.addView(localSurfaceView);
        }
    }


    @Override
    public void didReceiveRemoteVideoTrack(String userId) {
        pipRenderer.setVisibility(View.VISIBLE);
        if (localSurfaceView != null) {
            localSurfaceView.setZOrderMediaOverlay(true);
            if (isOutgoing) {
                if (localSurfaceView.getParent() != null) {
                    ((ViewGroup) localSurfaceView.getParent()).removeView(localSurfaceView);
                }
                pipRenderer.addView(localSurfaceView);
            }
        }


        View surfaceView = gEngineKit.getCurrentSession().setupRemoteVideo(userId, false);
        Log.d(TAG, "didReceiveRemoteVideoTrack,surfaceView = " + surfaceView);
        if (surfaceView != null) {
            fullscreenRenderer.setVisibility(View.VISIBLE);
            remoteSurfaceView = (SurfaceViewRenderer) surfaceView;
            fullscreenRenderer.removeAllViews();
            if (remoteSurfaceView.getParent() != null) {
                ((ViewGroup) remoteSurfaceView.getParent()).removeView(remoteSurfaceView);
            }
            fullscreenRenderer.addView(remoteSurfaceView);
        }
    }

    @Override
    public void didUserLeave(String userId) {

    }

    @Override
    public void didError(String error) {

    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        // 接听
        CallSession session = gEngineKit.getCurrentSession();
        if (id == R.id.acceptImageView) {
            if (session != null && session.getState() == CallState.Incoming) {
                session.joinHome(session.getRoomId());
            } else if (session != null) {
                if (callSingleActivity != null) {
                    session.sendRefuse();
                    callSingleActivity.finish();
                }
            }
        }
        // 挂断电话
        if (id == R.id.incomingHangupImageView || id == R.id.outgoingHangupImageView || id == R.id.connectedHangupImageView) {
            if (session != null) {
                Log.d(TAG, "endCall");
                SkyEngineKit.Instance().endCall();
            }
            if (callSingleActivity != null) callSingleActivity.finish();
        }

        // 切换摄像头
        if (id == R.id.switchCameraImageView) {
            session.switchCamera();
        }
        if (id == R.id.pip_video_view) {
            boolean isFullScreenRemote = fullscreenRenderer.getChildAt(0) == remoteSurfaceView;
            fullscreenRenderer.removeAllViews();
            pipRenderer.removeAllViews();
            if (isFullScreenRemote) {
                remoteSurfaceView.setZOrderMediaOverlay(true);
                pipRenderer.addView(remoteSurfaceView);
                localSurfaceView.setZOrderMediaOverlay(false);
                fullscreenRenderer.addView(localSurfaceView);
            } else {
                localSurfaceView.setZOrderMediaOverlay(true);
                pipRenderer.addView(localSurfaceView);
                remoteSurfaceView.setZOrderMediaOverlay(false);
                fullscreenRenderer.addView(remoteSurfaceView);
            }
        }

        // 切换到语音拨打
        if (id == R.id.outgoingAudioOnlyImageView || id == R.id.incomingAudioOnlyImageView || id == R.id.connectedAudioOnlyImageView) {
            if (session != null) {
                if (callSingleActivity != null) callSingleActivity.isAudioOnly = true;
                session.switchToAudio();
            }
        }

        // 小窗
        if (id == R.id.minimizeImageView) {
            if (callSingleActivity != null) callSingleActivity.showFloatingView();
        }

        // 录制按钮
        if (id == R.id.recordButtonVideo) {
            handleRecordButtonVideoClick();
        }
    }

    private void handleRecordButtonVideoClick() {
        if (!isRecording) {
            // Start recording
            File movieDir = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO) {
                movieDir = getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            }
            if (movieDir == null) {
                // Fallback or error handling
                Toast.makeText(getContext(), "Failed to access storage for recording.", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(movieDir, "skywebrtc_video_record_" + System.currentTimeMillis() + ".mp4");
            currentRecordingFilePath = file.getAbsolutePath();

            SkyEngineKit.Instance().startRecording(currentRecordingFilePath);

            recordButtonVideo.setImageResource(android.R.drawable.presence_video_busy); // Update to "stop" icon
            recordingTimerVideo.setBase(android.os.SystemClock.elapsedRealtime());
            recordingTimerVideo.setVisibility(View.VISIBLE);
            recordingTimerVideo.start();
            isRecording = true;
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        } else {
            // Stop recording
            SkyEngineKit.Instance().stopRecording();

            recordButtonVideo.setImageResource(android.R.drawable.presence_video_online); // Update to "start" icon
            recordingTimerVideo.stop();
            recordingTimerVideo.setVisibility(View.GONE);
            isRecording = false;
            if (currentRecordingFilePath != null) {
                Toast.makeText(getContext(), "Recording saved: " + new File(currentRecordingFilePath).getName(), Toast.LENGTH_LONG).show();
                showSaveNotification(currentRecordingFilePath, true);
            }
        }
    }

    private static final String RECORDING_CHANNEL_ID = "recording_channel";
    private int notificationIdCounter = 0; // Simple counter for unique notification IDs

    private void showSaveNotification(String filePath, boolean isVideo) {
        if (getContext() == null) return;

        Context context = getContext();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Create Notification Channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    RECORDING_CHANNEL_ID,
                    "Recordings",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for saved call recordings");
            notificationManager.createNotificationChannel(channel);
        }

        File file = new File(filePath);
        Uri fileUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);

        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(fileUri, isVideo ? "video/mp4" : "audio/mp4"); // Adjust mime type if necessary
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Important for starting activity from notification

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                (int) System.currentTimeMillis(), // Unique request code
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, RECORDING_CHANNEL_ID)
                .setSmallIcon(R.drawable.av_default_header) // Replace with a more suitable icon
                .setContentTitle("Recording Saved")
                .setContentText("Tap to view your " + (isVideo ? "video" : "audio") + " recording.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // Use a unique ID for each notification to ensure they all show up
        notificationManager.notify(notificationIdCounter++, builder.build());
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (fullscreenRenderer != null) {
            fullscreenRenderer.removeAllViews();
        }
        if (pipRenderer != null) {
            pipRenderer.removeAllViews();
        }
        // Ensure recording is stopped if fragment is destroyed while recording
        if (isRecording) {
            SkyEngineKit.Instance().stopRecording();
            isRecording = false;
            if (recordingTimerVideo != null) {
                recordingTimerVideo.stop();
                recordingTimerVideo.setVisibility(View.GONE);
            }
        }
    }
}