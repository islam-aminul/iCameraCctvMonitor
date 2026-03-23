package com.tcs.ion.iCamera.cctv.service;

import com.google.gson.*;
import com.tcs.ion.iCamera.cctv.model.CctvData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Uses ffprobe to analyse RTSP streams.
 * Probes up to 25-30 streams concurrently via a fixed thread pool.
 *
 * ffprobe path is read from AppSettings; defaults to .\ffprobe.exe
 */
public class FfprobeService {

    private static final Logger log = LoggerFactory.getLogger(FfprobeService.class);
    private static final int PROBE_TIMEOUT_SECONDS = 10;
    private static final int MAX_THREADS = 8; // parallel probes

    private final DataStore store = DataStore.getInstance();
    private final ExecutorService probePool = Executors.newFixedThreadPool(MAX_THREADS, r -> {
        Thread t = new Thread(r, "ffprobe-worker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Probes all CCTV streams currently in the DataStore concurrently.
     */
    public void probeAll() {
        Collection<CctvData> cctvList = store.getAllCctv();
        if (cctvList.isEmpty()) return;

        List<Future<?>> futures = new ArrayList<>();
        for (CctvData cctv : cctvList) {
            if (cctv.getRtspUrl() == null || cctv.getRtspUrl().isEmpty()) continue;
            futures.add(probePool.submit(() -> probeStream(cctv)));
        }
        // Wait for all probes to complete (with overall timeout)
        for (Future<?> f : futures) {
            try { f.get(PROBE_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS); }
            catch (TimeoutException e) { f.cancel(true); }
            catch (Exception e) { log.debug("Probe future error: {}", e.getMessage()); }
        }
    }

    /**
     * Runs ffprobe against a single RTSP stream and populates the CctvData fields.
     */
    void probeStream(CctvData cctv) {
        String ffprobePath = store.getSettings().getFfprobePath();
        String rtsp = cctv.getRtspUrl();

        List<String> cmd = Arrays.asList(
                ffprobePath,
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                "-rtsp_transport", "tcp",   // TCP wrapper – better for 25-30 concurrent streams
                "-timeout", String.valueOf(PROBE_TIMEOUT_SECONDS * 1_000_000), // microseconds
                rtsp
        );

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) output.append(line);
            }

            boolean finished = proc.waitFor(PROBE_TIMEOUT_SECONDS + 2L, TimeUnit.SECONDS);
            if (!finished) { proc.destroyForcibly(); throw new TimeoutException("ffprobe timeout"); }

            if (proc.exitValue() != 0) throw new IOException("ffprobe exit=" + proc.exitValue());

            parseProbeOutput(output.toString(), cctv);
            cctv.setFfprobeSuccess(true);

        } catch (Exception e) {
            log.warn("ffprobe failed for {} ({}): {}", cctv.getCctvName(), rtsp, e.getMessage());
            cctv.setFfprobeSuccess(false);
        }
        // Re-evaluate active status with new ffprobe data
        cctv.evaluateStatus();
        store.updateCctvData(cctv);
    }

    private void parseProbeOutput(String json, CctvData cctv) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray streams = root.getAsJsonArray("streams");
            if (streams == null || streams.size() == 0) return;

            for (JsonElement el : streams) {
                JsonObject stream = el.getAsJsonObject();
                String codecType = getStr(stream, "codec_type");
                if (!"video".equalsIgnoreCase(codecType)) continue;

                // Codec / encoding
                String codecName = getStr(stream, "codec_name");
                if (codecName != null) cctv.setEncoding(codecName.toUpperCase());

                // Profile
                String profile = getStr(stream, "profile");
                if (profile != null) cctv.setStreamProfile(profile);

                // Resolution
                int w = getInt(stream, "width");
                int h = getInt(stream, "height");
                if (w > 0 && h > 0) cctv.setResolution(w + "x" + h);

                // FPS (avg_frame_rate or r_frame_rate)
                String fpsStr = getStr(stream, "avg_frame_rate");
                if (fpsStr == null || fpsStr.equals("0/0")) fpsStr = getStr(stream, "r_frame_rate");
                cctv.setFps(parseFraction(fpsStr));

                // Bitrate from stream tags or format
                String bitrate = getStr(stream, "bit_rate");
                if (bitrate == null || bitrate.isEmpty()) {
                    JsonObject format = root.getAsJsonObject("format");
                    if (format != null) bitrate = getStr(format, "bit_rate");
                }
                if (bitrate != null && !bitrate.isEmpty()) {
                    try { cctv.setBitrateKbps((int)(Long.parseLong(bitrate) / 1000)); } catch (NumberFormatException ignored) {}
                }

                // Keyframe interval
                String gopStr = getStr(stream, "codec_tag_string");
                // Try has_b_frames as proxy or disposition
                String kfStr = null;
                if (stream.has("tags")) {
                    JsonObject tags = stream.getAsJsonObject("tags");
                    kfStr = getStr(tags, "KEY_FRAME_INTERVAL");
                }
                if (kfStr != null) {
                    try { cctv.setKeyFrameInterval(Integer.parseInt(kfStr)); } catch (NumberFormatException ignored) {}
                }

                break; // only process first video stream
            }
        } catch (Exception e) {
            log.warn("ffprobe JSON parse error for {}: {}", cctv.getCctvName(), e.getMessage());
        }
    }

    private String getStr(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsString() : null;
    }

    private int getInt(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        return (el != null && !el.isJsonNull()) ? el.getAsInt() : 0;
    }

    /** Parses "25/1" or "30000/1001" into a double. */
    private double parseFraction(String s) {
        if (s == null || s.isEmpty()) return 0;
        String[] parts = s.split("/");
        if (parts.length == 2) {
            try {
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                return den == 0 ? 0 : num / den;
            } catch (NumberFormatException ignored) {}
        }
        try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        return 0;
    }

    public void shutdown() {
        probePool.shutdown();
    }
}
