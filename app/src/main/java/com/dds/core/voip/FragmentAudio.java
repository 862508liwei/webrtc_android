package com.dds.core.voip;

import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.Chronometer;
import android.widget.Toast;
import android.os.Environment;
import java.io.File;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import com.dds.webrtc.BuildConfig;
import com.dds.webrtc.R;


import com.dds.App;
import com.dds.core.util.BarUtils;
import com.dds.core.util.OSUtils;
import com.dds.skywebrtc.CallSession;
import com.dds.skywebrtc.EnumType.CallState;
import com.dds.skywebrtc.SkyEngineKit;
import com.dds.webrtc.R;


/**
 * Created by dds on 2018/7/26.
 * android_shuai@163.com
 * 语音通话控制界面
 */
public class FragmentAudio extends SingleCallFragment implements View.OnClickListener {
    private static final String TAG = "FragmentAudio";
    private ImageView muteImageView;
    private ImageView speakerImageView;
    private boolean micEnabled = false; // 静音
    private boolean isSpeakerOn = false; // 扬声器

    private ImageButton recordButtonAudio;
    private Chronometer recordingTimerAudio;
    private boolean isRecording = false;
    private String currentRecordingFilePath;

    @Override
    int getLayout() {
        return R.layout.fragment_audio;
    }

    @Override
    public void initView(View view) {
        super.initView(view);
        muteImageView = view.findViewById(R.id.muteImageView);
        speakerImageView = view.findViewById(R.id.speakerImageView);
        minimizeImageView.setVisibility(View.GONE);

        recordButtonAudio = view.findViewById(R.id.recordButtonAudio);
        recordingTimerAudio = view.findViewById(R.id.recordingTimerAudio);
        if (recordButtonAudio != null) {
            recordButtonAudio.setOnClickListener(this);
        }

        outgoingHangupImageView.setOnClickListener(this);
        incomingHangupImageView.setOnClickListener(this);
        minimizeImageView.setOnClickListener(this);
        muteImageView.setOnClickListener(this);
        acceptImageView.setOnClickListener(this);
        speakerImageView.setOnClickListener(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M || OSUtils.isMiui() || OSUtils.isFlyme()) {
            lytParent.post(() -> {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) minimizeImageView.getLayoutParams();
                params.topMargin = BarUtils.getStatusBarHeight();
                minimizeImageView.setLayoutParams(params);
            });
        }
    }

    @Override
    public void init() {
        super.init();
        CallSession currentSession = gEngineKit.getCurrentSession();
        currentState = currentSession.getState();
        // 如果已经接通
        if (currentState == CallState.Connected) {
            descTextView.setVisibility(View.GONE); // 提示语
            outgoingActionContainer.setVisibility(View.VISIBLE);
            durationTextView.setVisibility(View.VISIBLE);
            minimizeImageView.setVisibility(View.VISIBLE);
            startRefreshTime();
        } else {
            // 如果未接通
            if (isOutgoing) {
                descTextView.setText(R.string.av_waiting);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                incomingActionContainer.setVisibility(View.GONE);
            } else {
                descTextView.setText(R.string.av_audio_invite);
                outgoingActionContainer.setVisibility(View.GONE);
                incomingActionContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void didChangeState(CallState state) {
        currentState = state;
        runOnUiThread(() -> {
            if (state == CallState.Connected) {
                incomingActionContainer.setVisibility(View.GONE);
                outgoingActionContainer.setVisibility(View.VISIBLE);
                minimizeImageView.setVisibility(View.VISIBLE);
                descTextView.setVisibility(View.GONE);

                startRefreshTime();
            } else {
                // do nothing now
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        // 接听
        if (id == R.id.acceptImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null)
                Log.d(TAG, "session = " + session + "; session.getState() = " + session.getState());
            if (session != null && session.getState() == CallState.Incoming) {
                session.joinHome(session.getRoomId());
            } else if (session != null) {
                session.sendRefuse();
            }
        }
        // 挂断电话
        if (id == R.id.incomingHangupImageView || id == R.id.outgoingHangupImageView) {
            App.getInstance().setOtherUserId("0");
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null) {
                SkyEngineKit.Instance().endCall();
            }
            //            activity.finish();
            //再onEvent中结束，防止ChatActivity结束了，消息发送不了
        }
        // 静音
        if (id == R.id.muteImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null && session.getState() != CallState.Idle) {
                if (session.toggleMuteAudio(!micEnabled)) {
                    micEnabled = !micEnabled;
                }
                muteImageView.setSelected(micEnabled);
            }
        }
        // 扬声器
        if (id == R.id.speakerImageView) {
            CallSession session = gEngineKit.getCurrentSession();
            if (session != null && session.getState() != CallState.Idle) {
                if (session.toggleSpeaker(!isSpeakerOn)) {
                    isSpeakerOn = !isSpeakerOn;
                }
                speakerImageView.setSelected(isSpeakerOn);
            }
        }
        // 小窗
        if (id == R.id.minimizeImageView) {
            if (callSingleActivity != null) {
                callSingleActivity.showFloatingView();
            }
        }

        // 录制按钮
        if (id == R.id.recordButtonAudio) {
            handleRecordButtonAudioClick();
        }
    }

    private void handleRecordButtonAudioClick() {
        if (!isRecording) {
            // Start recording
            File movieDir = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO) {
                movieDir = getContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC); // Use DIRECTORY_MUSIC for audio
            }
            if (movieDir == null) {
                Toast.makeText(getContext(), "Failed to access storage for recording.", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(movieDir, "skywebrtc_audio_record_" + System.currentTimeMillis() + ".mp3"); // or .aac, .wav
            currentRecordingFilePath = file.getAbsolutePath();

            SkyEngineKit.Instance().startRecording(currentRecordingFilePath); // Assuming SkyEngineKit handles audio-only recording correctly

            recordButtonAudio.setImageResource(android.R.drawable.ic_media_pause); // Update to "stop" icon (using pause as placeholder)
            recordingTimerAudio.setBase(android.os.SystemClock.elapsedRealtime());
            recordingTimerAudio.setVisibility(View.VISIBLE);
            recordingTimerAudio.start();
            isRecording = true;
            Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        } else {
            // Stop recording
            SkyEngineKit.Instance().stopRecording();

            recordButtonAudio.setImageResource(android.R.drawable.ic_btn_speak_now); // Update to "start" icon
            recordingTimerAudio.stop();
            recordingTimerAudio.setVisibility(View.GONE);
            isRecording = false;
            if (currentRecordingFilePath != null) {
                Toast.makeText(getContext(), "Recording saved: " + new File(currentRecordingFilePath).getName(), Toast.LENGTH_LONG).show();
                showSaveNotification(currentRecordingFilePath, false); // false for audio
            }
        }
    }

    private static final String RECORDING_CHANNEL_ID = "recording_channel";
    private int notificationIdCounter = 1000; // Start audio notifications from a different range if needed, or use a global counter

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
        // Use "audio/mp3" for mp3 files, or "audio/*" for general audio
        viewIntent.setDataAndType(fileUri, isVideo ? "video/mp4" : "audio/mp3");
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

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

        notificationManager.notify(notificationIdCounter++, builder.build());
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Ensure recording is stopped if fragment is destroyed while recording
        if (isRecording) {
            SkyEngineKit.Instance().stopRecording();
            isRecording = false;
            if (recordingTimerAudio != null) {
                recordingTimerAudio.stop();
                recordingTimerAudio.setVisibility(View.GONE);
            }
        }
    }

}