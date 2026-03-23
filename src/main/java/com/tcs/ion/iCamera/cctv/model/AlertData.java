package com.tcs.ion.iCamera.cctv.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Represents a single alert event.  Can be serialised to JSON for REST API transport.
 */
public class AlertData {

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneId.of("UTC"));

    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Category { PROXY, CCTV, SYSTEM, NETWORK, HSQLDB, MAC }

    private String id;
    private Severity severity;
    private Category category;
    private String source;       // e.g. "ProxyID-1703", "CCTV-798"
    private String parameter;    // e.g. "CPU_USAGE", "RTSP_UNREACHABLE"
    private String message;
    private double currentValue; // numeric value that triggered, if applicable
    private double threshold;
    private Instant timestamp;
    private boolean acknowledged;
    private boolean resolved;

    public AlertData(Severity severity, Category category, String source, String parameter, String message) {
        this.id = UUID.randomUUID().toString();
        this.severity = severity;
        this.category = category;
        this.source = source;
        this.parameter = parameter;
        this.message = message;
        this.timestamp = Instant.now();
        this.acknowledged = false;
        this.resolved = false;
    }

    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this.toMap());
    }

    private java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("severity", severity.name());
        map.put("category", category.name());
        map.put("source", source);
        map.put("parameter", parameter);
        map.put("message", message);
        map.put("currentValue", currentValue);
        map.put("threshold", threshold);
        map.put("timestamp", DTF.format(timestamp));
        map.put("timestampLocal", timestamp.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        map.put("acknowledged", acknowledged);
        map.put("resolved", resolved);
        return map;
    }

    public String getTimestampDisplay() {
        return timestamp.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // ---- Getters & Setters ----

    public String getId() { return id; }
    public Severity getSeverity() { return severity; }
    public Category getCategory() { return category; }
    public String getSource() { return source; }
    public String getParameter() { return parameter; }
    public String getMessage() { return message; }
    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
}
