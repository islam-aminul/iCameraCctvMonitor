package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.CctvData;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Probes RTSP streams using JavaCV's FFmpegFrameGrabber (libavformat via JavaCPP).
 * No external ffprobe.exe binary required.
 * Up to 8 streams are probed concurrently.
 */
public class FfprobeService {

    private static final Logger log = LoggerFactory.getLogger(FfprobeService.class);

    /** Per-stream timeout in milliseconds. */
    private static final int PROBE_TIMEOUT_MS = 10_000;
    private static final int MAX_THREADS = 8;

    private final DataStore store = DataStore.getInstance();
    private final ExecutorService probePool = Executors.newFixedThreadPool(MAX_THREADS, r -> {
        Thread t = new Thread(r, "ffprobe-worker");
        t.setDaemon(true);
        return t;
    });

    /** Probes all CCTV RTSP streams currently held in the DataStore. */
    public void probeAll() {
        Collection<CctvData> cctvList = store.getAllCctv();
        if (cctvList.isEmpty()) return;

        List<Future<?>> futures = new ArrayList<>();
        for (CctvData cctv : cctvList) {
            if (cctv.getRtspUrl() == null || cctv.getRtspUrl().isEmpty()) continue;
            futures.add(probePool.submit(() -> probeStream(cctv)));
        }
        // Wait for all probes (individual streams have their own timeout)
        for (Future<?> f : futures) {
            try {
                f.get(PROBE_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                f.cancel(true);
            } catch (Exception e) {
                log.debug("Probe future error: {}", e.getMessage());
            }
        }
    }

    /** Opens the RTSP stream via JavaCV and reads video stream metadata. */
    void probeStream(CctvData cctv) {
        String rtsp = cctv.getRtspUrl();
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = new FFmpegFrameGrabber(rtsp);
            // Force TCP transport – more reliable for concurrent probing
            grabber.setOption("rtsp_transport", "tcp");
            // avformat timeout in microseconds
            grabber.setTimeout(PROBE_TIMEOUT_MS * 1_000); // int arithmetic – 10s in microseconds
            // open + probe stream info (avformat_open_input + avformat_find_stream_info)
            grabber.start();

            // ---- Codec / encoding ----
            String codec = grabber.getVideoCodecName();
            if (codec != null && !codec.isEmpty()) {
                cctv.setEncoding(codec.toUpperCase());
            }

            // ---- Resolution ----
            int w = grabber.getImageWidth();
            int h = grabber.getImageHeight();
            if (w > 0 && h > 0) {
                cctv.setResolution(w + "x" + h);
            }

            // ---- Frame rate ----
            double fps = grabber.getFrameRate();
            if (fps > 0) {
                cctv.setFps(fps);
            }

            // ---- Bitrate (bits/s → kbps) ----
            int bitrateBps = grabber.getVideoBitrate();
            if (bitrateBps > 0) {
                cctv.setBitrateKbps(bitrateBps / 1000);
            }

            cctv.setFfprobeSuccess(true);
            log.debug("Probed {} – codec={} res={}x{} fps={:.2f} bitrate={}kbps",
                    cctv.getCctvName(), codec, w, h, fps, bitrateBps / 1000);

        } catch (Exception e) {
            log.warn("Stream probe failed for {} ({}): {}", cctv.getCctvName(), rtsp, e.getMessage());
            cctv.setFfprobeSuccess(false);
        } finally {
            if (grabber != null) {
                try { grabber.stop();    } catch (Exception ignored) {}
                try { grabber.release(); } catch (Exception ignored) {}
            }
        }
        cctv.evaluateStatus();
        store.updateCctvData(cctv);
    }

    public void shutdown() {
        probePool.shutdown();
    }
}
