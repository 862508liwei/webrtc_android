package com.dds.core.ui.player;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast; // Added for error messages
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import android.net.Uri; // Required for VideoView URI

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dds.webrtc.R;

public class MediaPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_IS_VIDEO = "extra_is_video";

    private VideoView videoViewPlayer;
    private LinearLayout audioPlayerControls;
    private TextView textViewAudioFileName;
    private ImageView imageViewAlbumArtPlaceholder;
    private ImageButton buttonPlayPauseAudio;

    private MediaPlayer audioPlayer; // For audio playback
    private MediaController mediaController; // For VideoView controls


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        String filePath = getIntent().getStringExtra(EXTRA_FILE_PATH);
        boolean isVideo = getIntent().getBooleanExtra(EXTRA_IS_VIDEO, false);

        if (getSupportActionBar() != null && filePath != null) {
            getSupportActionBar().setTitle(new java.io.File(filePath).getName());
        }


        videoViewPlayer = findViewById(R.id.videoViewPlayer);
        audioPlayerControls = findViewById(R.id.audioPlayerControls);
        textViewAudioFileName = findViewById(R.id.textViewAudioFileName);
        imageViewAlbumArtPlaceholder = findViewById(R.id.imageViewAlbumArtPlaceholder);
        buttonPlayPauseAudio = findViewById(R.id.buttonPlayPauseAudio);

        if (filePath == null) {
            // Handle error: file path is missing
            Toast.makeText(this, "File path missing", Toast.LENGTH_SHORT).show();
            finish(); // Close activity if no file path
            return;
        }

        if (isVideo) {
            videoViewPlayer.setVisibility(View.VISIBLE);
            audioPlayerControls.setVisibility(View.GONE);

            if (mediaController == null) {
                mediaController = new MediaController(this);
                mediaController.setAnchorView(videoViewPlayer);
            }
            videoViewPlayer.setMediaController(mediaController);
            videoViewPlayer.setVideoURI(Uri.parse(filePath)); // Assuming filePath is a content URI or direct path
            videoViewPlayer.requestFocus();

            videoViewPlayer.setOnPreparedListener(mp -> {
                videoViewPlayer.start();
            });

            videoViewPlayer.setOnCompletionListener(mp -> {
                finish();
            });

            videoViewPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(MediaPlayerActivity.this, "播放视频失败", Toast.LENGTH_SHORT).show();
                Log.e("MediaPlayerActivity", "VideoView Error: " + what + ", " + extra);
                finish();
                return true;
            });

        } else { // Audio
            videoViewPlayer.setVisibility(View.GONE);
            audioPlayerControls.setVisibility(View.VISIBLE);
            textViewAudioFileName.setText(new java.io.File(filePath).getName());

            audioPlayer = new MediaPlayer();
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            try {
                audioPlayer.setDataSource(filePath);
                audioPlayer.prepareAsync();
            } catch (java.io.IOException e) {
                Toast.makeText(this, "Error setting data source", Toast.LENGTH_SHORT).show();
                Log.e("MediaPlayerActivity", "MediaPlayer IOException: ", e);
                if (audioPlayer != null) {
                    audioPlayer.release();
                    audioPlayer = null;
                }
                finish();
                return;
            }

            audioPlayer.setOnPreparedListener(mp -> {
                mp.start();
                buttonPlayPauseAudio.setImageResource(android.R.drawable.ic_media_pause);
            });

            audioPlayer.setOnCompletionListener(mp -> {
                buttonPlayPauseAudio.setImageResource(android.R.drawable.ic_media_play);
                // Optionally finish or allow replay
                // finish();
            });

            audioPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(MediaPlayerActivity.this, "播放音频失败", Toast.LENGTH_SHORT).show();
                Log.e("MediaPlayerActivity", "MediaPlayer Error: " + what + ", " + extra);
                if (audioPlayer != null) {
                    audioPlayer.release();
                    audioPlayer = null;
                }
                finish();
                return true;
            });

            buttonPlayPauseAudio.setOnClickListener(v -> {
                if (audioPlayer != null) {
                    if (audioPlayer.isPlaying()) {
                        audioPlayer.pause();
                        buttonPlayPauseAudio.setImageResource(android.R.drawable.ic_media_play);
                    } else {
                        audioPlayer.start();
                        buttonPlayPauseAudio.setImageResource(android.R.drawable.ic_media_pause);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoViewPlayer != null && videoViewPlayer.isPlaying()) {
            videoViewPlayer.pause();
        }
        if (audioPlayer != null && audioPlayer.isPlaying()) {
            audioPlayer.pause();
            // We don't change the button icon here as the user might resume
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // For VideoView, onPause is usually enough. If we were saving state, we'd do it here.
        if (isFinishing()) {
            releaseMediaPlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoViewPlayer != null) {
            videoViewPlayer.stopPlayback(); // This releases resources for VideoView
        }
        releaseMediaPlayer();
    }

    private void releaseMediaPlayer() {
        if (audioPlayer != null) {
            try {
                if (audioPlayer.isPlaying()) {
                    audioPlayer.stop();
                }
                audioPlayer.release();
            } catch (Exception e) {
                Log.e("MediaPlayerActivity", "Error releasing MediaPlayer: ", e);
            } finally {
                audioPlayer = null;
            }
        }
    }
}
