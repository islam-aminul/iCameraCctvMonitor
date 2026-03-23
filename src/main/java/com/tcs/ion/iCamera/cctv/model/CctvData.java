package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single CCTV camera's status and metadata.
 */
public class CctvData {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long ACTIVE_THRESHOLD_MS = 120_000L; // 2 minutes

    private int cctvId;
    private String cctvName;
    private String rtspUrl;
    private String ipAddress;   // extracted from RTSP URL
    private boolean reachable;

    // Timestamps (epoch millis; -1 = not found)
    private long lastFileModifiedMillis;
    private long lastFileUploadedMillis;

    // ffprobe results
    private String encoding;        // e.g. "H264"
    private String streamProfile;   // e.g. "high", "low", "baseline"
    private double fps;
    private int bitrateKbps;
    private String resolution;      // e.g. "1920x1080"
    private int keyFrameInterval;
    private boolean ffprobeSuccess;

    // Computed fields
    private boolean active;         // true only when ALL criteria met
    private String inactiveReason;

    // Snapshot metadata
    private Instant snapshotTime;
    private boolean stale;

    public CctvData() {
        this.snapshotTime = Instant.now();
    }

    /**
     * Evaluates and sets the active/inactive status based on all criteria.
     * Call this after updating all fields from JMX + ffprobe.
     */
    public void evaluateStatus() {
        if (!reachable) {
            active = false;
            inactiveReason = "RTSP Unreachable";
            return;
        }
        long now = System.currentTimeMillis();
        if (lastFileModifiedMillis < 0 || (now - lastFileModifiedMillis) > ACTIVE_THRESHOLD_MS) {
            active = false;
            inactiveReason = "File generation stale (>" + (ACTIVE_THRESHOLD_MS / 1000) + "s)";
            return;
        }
        if (lastFileUploadedMillis < 0 || (now - lastFileUploadedMillis) > ACTIVE_THRESHOLD_MS) {
            active = false;
            inactiveReason = "File upload stale (>" + (ACTIVE_THRESHOLD_MS / 1000) + "s)";
            return;
        }
        if (!ffprobeSuccess) {
            active = false;
            inactiveReason = "ffprobe failed";
            return;
        }
        if (!"H264".equalsIgnoreCase(encoding) && !"H.264".equalsIgnoreCase(encoding)) {
            active = false;
            inactiveReason = "Encoding not H264 (found: " + encoding + ")";
            return;
        }
        active = true;
        inactiveReason = null;
    }

    public boolean isFileGenerationActive() {
        if (lastFileModifiedMillis < 0) return false;
        return (System.currentTimeMillis() - lastFileModifiedMillis) <= ACTIVE_THRESHOLD_MS;
    }

    public boolean isFileUploadActive() {
        if (lastFileUploadedMillis < 0) return false;
        return (System.currentTimeMillis() - lastFileUploadedMillis) <= ACTIVE_THRESHOLD_MS;
    }

    public String getLastFileModifiedDisplay() {
        if (lastFileModifiedMillis < 0) return "Not found";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastFileModifiedMillis), ZoneId.systemDefault()).format(DTF);
    }

    public String getLastFileUploadedDisplay() {
        if (lastFileUploadedMillis < 0) return "Not found";
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastFileUploadedMillis), ZoneId.systemDefault()).format(DTF);
    }

    /**
     * Extracts IP address from RTSP URL.  e.g. rtsp://192.168.0.1:554/stream -> 192.168.0.1
     */
    public void extractIpFromRtsp() {
        if (rtspUrl == null || rtspUrl.isEmpty()) { ipAddress = "N/A"; return; }
        try {
            String stripped = rtspUrl.replaceFirst("rtsp://", "");
            String hostPort = stripped.contains("/") ? stripped.substring(0, stripped.indexOf('/')) : stripped;
            ipAddress = hostPort.contains(":") ? hostPort.substring(0, hostPort.lastIndexOf(':')) : hostPort;
        } catch (Exception e) {
            ipAddress = "N/A";
        }
    }

    // ---- Getters & Setters ----

    public int getCctvId() { return cctvId; }
    public void setCctvId(int cctvId) { this.cctvId = cctvId; }

    public String getCctvName() { return cctvName; }
    public void setCctvName(String cctvName) { this.cctvName = cctvName; }

    public String getRtspUrl() { return rtspUrl; }
    public void setRtspUrl(String rtspUrl) {
        this.rtspUrl = rtspUrl;
        extractIpFromRtsp();
    }

    public String getIpAddress() { return ipAddress; }

    public boolean isReachable() { return reachable; }
    public void setReachable(boolean reachable) { this.reachable = reachable; }

    public long getLastFileModifiedMillis() { return lastFileModifiedMillis; }
    public void setLastFileModifiedMillis(long lastFileModifiedMillis) { this.lastFileModifiedMillis = lastFileModifiedMillis; }

    public long getLastFileUploadedMillis() { return lastFileUploadedMillis; }
    public void setLastFileUploadedMillis(long lastFileUploadedMillis) { this.lastFileUploadedMillis = lastFileUploadedMillis; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public String getStreamProfile() { return streamProfile; }
    public void setStreamProfile(String streamProfile) { this.streamProfile = streamProfile; }

    public double getFps() { return fps; }
    public void setFps(double fps) { this.fps = fps; }

    public int getBitrateKbps() { return bitrateKbps; }
    public void setBitrateKbps(int bitrateKbps) { this.bitrateKbps = bitrateKbps; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public int getKeyFrameInterval() { return keyFrameInterval; }
    public void setKeyFrameInterval(int keyFrameInterval) { this.keyFrameInterval = keyFrameInterval; }

    public boolean isFfprobeSuccess() { return ffprobeSuccess; }
    public void setFfprobeSuccess(boolean ffprobeSuccess) { this.ffprobeSuccess = ffprobeSuccess; }

    public boolean isActive() { return active; }

    public String getInactiveReason() { return inactiveReason; }

    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant snapshotTime) { this.snapshotTime = snapshotTime; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }
}
