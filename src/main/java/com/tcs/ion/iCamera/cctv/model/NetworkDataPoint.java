package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;

/**
 * A single network speed measurement snapshot (upload to AWS).
 */
public class NetworkDataPoint {

    private Instant timestamp;
    private double uploadSpeedMbps;

    public NetworkDataPoint(double uploadSpeedMbps) {
        this.timestamp = Instant.now();
        this.uploadSpeedMbps = uploadSpeedMbps;
    }

    public Instant getTimestamp() { return timestamp; }
    public double getUploadSpeedMbps() { return uploadSpeedMbps; }

    public String getTimestampDisplay() {
        return timestamp.atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
}
