package com.dds.core.ui.recordings;

public class RecordingInfo {
    private String filePath;
    private String fileName;
    private long durationMillis;
    private long lastModified;
    private boolean isVideo;

    public RecordingInfo(String filePath, String fileName, long durationMillis, long lastModified, boolean isVideo) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.durationMillis = durationMillis;
        this.lastModified = lastModified;
        this.isVideo = isVideo;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public long getLastModified() {
        return lastModified;
    }

    public boolean isVideo() {
        return isVideo;
    }
}
