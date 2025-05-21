package com.dds.core.ui.recordings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.TextView; // For emptyViewRecordings
import android.widget.Toast;
import android.os.Environment;
import android.media.MediaMetadataRetriever;

import com.dds.webrtc.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnItemClickListener {

    private RecyclerView recyclerViewRecordings;
    private TextView emptyViewRecordings; // Changed to TextView for direct text setting if needed
    private RecordingsAdapter recordingsAdapter;
    private List<RecordingInfo> recordingInfoList = new ArrayList<>();


    public RecordingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_recordings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerViewRecordings = view.findViewById(R.id.recyclerViewRecordings);
        emptyViewRecordings = view.findViewById(R.id.emptyViewRecordings); // Initialize if you want to control it here

        // Setup RecyclerView
        recyclerViewRecordings.setLayoutManager(new LinearLayoutManager(getContext()));
        recordingsAdapter = new RecordingsAdapter(getContext(), recordingInfoList, this);
        recyclerViewRecordings.setAdapter(recordingsAdapter);

        loadRecordings();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh recordings when the fragment becomes visible, in case new recordings were made
        // or files were deleted externally (though this won't catch all external deletions without more complex listeners)
        // loadRecordings(); // Consider if this is too frequent or should be manual refresh
    }


    private void loadRecordings() {
        recordingInfoList.clear();
        // Potential performance bottleneck - consider background thread
        // For now, as per instruction, on main thread.

        File moviesDir = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO) {
            moviesDir = getContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        }
        File musicDir = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.FROYO) {
            musicDir = getContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        }

        addRecordingsFromDirectory(moviesDir, true); // true for video
        addRecordingsFromDirectory(musicDir, false); // false for audio

        // Sort by lastModified date, newest first
        Collections.sort(recordingInfoList, (o1, o2) -> Long.compare(o2.getLastModified(), o1.getLastModified()));

        recordingsAdapter.setData(recordingInfoList);

        updateEmptyViewVisibility();
    }

    private void addRecordingsFromDirectory(File directory, boolean isVideoDir) {
        if (directory != null && directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && (file.getName().startsWith("skywebrtc_video_record_") || file.getName().startsWith("skywebrtc_audio_record_"))) {
                        long durationMillis = 0;
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(file.getAbsolutePath());
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            if (durationStr != null) {
                                durationMillis = Long.parseLong(durationStr);
                            }
                        } catch (Exception e) {
                            // Log error or handle
                            e.printStackTrace();
                        } finally {
                            try {
                                retriever.release();
                            } catch (Exception e) {
                                // Handle release exception if necessary
                                e.printStackTrace();
                            }
                        }
                        boolean isActuallyVideo = file.getName().contains("_video_"); // More robust check if needed
                        recordingInfoList.add(new RecordingInfo(
                                file.getAbsolutePath(),
                                file.getName(),
                                durationMillis,
                                file.lastModified(),
                                isActuallyVideo
                        ));
                    }
                }
            }
        }
    }


    private void updateEmptyViewVisibility() {
        if (recordingInfoList.isEmpty()) {
            emptyViewRecordings.setVisibility(View.VISIBLE);
            recyclerViewRecordings.setVisibility(View.GONE);
        } else {
            emptyViewRecordings.setVisibility(View.GONE);
            recyclerViewRecordings.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onItemClick(RecordingInfo recordingInfo) {
        Toast.makeText(getContext(), "Clicked: " + recordingInfo.getFileName(), Toast.LENGTH_SHORT).show();
        // File opening logic will be added in a future task
    }
}
