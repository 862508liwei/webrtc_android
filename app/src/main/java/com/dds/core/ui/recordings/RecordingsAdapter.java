package com.dds.core.ui.recordings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dds.webrtc.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RecordingsAdapter extends RecyclerView.Adapter<RecordingsAdapter.RecordingViewHolder> {

    private List<RecordingInfo> recordingInfoList;
    private OnItemClickListener listener;
    private Context context;

    public interface OnItemClickListener {
        void onItemClick(RecordingInfo recordingInfo);
    }

    public RecordingsAdapter(Context context, List<RecordingInfo> recordingInfoList, OnItemClickListener listener) {
        this.context = context;
        this.recordingInfoList = recordingInfoList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public RecordingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_recording, parent, false);
        return new RecordingViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordingViewHolder holder, int position) {
        RecordingInfo recordingInfo = recordingInfoList.get(position);
        holder.textViewFileName.setText(recordingInfo.getFileName());
        holder.textViewFileDuration.setText(formatDuration(recordingInfo.getDurationMillis()));
        holder.textViewFileDate.setText(formatDate(recordingInfo.getLastModified()));

        if (recordingInfo.isVideo()) {
            holder.imageViewFileType.setImageResource(android.R.drawable.presence_video_online);
        } else {
            holder.imageViewFileType.setImageResource(android.R.drawable.presence_audio_online);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(recordingInfo);
            }
        });
    }

    @Override
    public int getItemCount() {
        return recordingInfoList == null ? 0 : recordingInfoList.size();
    }

    public void setData(List<RecordingInfo> newList) {
        this.recordingInfoList = newList;
        notifyDataSetChanged(); // Consider using DiffUtil for better performance later
    }

    static class RecordingViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewFileType;
        TextView textViewFileName;
        TextView textViewFileDuration;
        TextView textViewFileDate;

        public RecordingViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewFileType = itemView.findViewById(R.id.imageViewFileType);
            textViewFileName = itemView.findViewById(R.id.textViewFileName);
            textViewFileDuration = itemView.findViewById(R.id.textViewFileDuration);
            textViewFileDate = itemView.findViewById(R.id.textViewFileDate);
        }
    }

    public static String formatDuration(long millis) {
        if (millis < 0) {
            millis = 0;
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    public static String formatDate(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
}
