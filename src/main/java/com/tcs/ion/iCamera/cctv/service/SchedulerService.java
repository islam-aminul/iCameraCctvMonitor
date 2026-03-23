package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central scheduler.  Uses a single ScheduledExecutorService so poll cycles never stack up.
 * If one cycle overlaps with the next, the new cycle is skipped for that source.
 */
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "iCamera-Scheduler");
                t.setDaemon(true);
                return t;
            });

    private final AtomicBoolean jmxPollRunning     = new AtomicBoolean(false);
    private final AtomicBoolean oshiPollRunning     = new AtomicBoolean(false);
    private final AtomicBoolean ffprobePollRunning  = new AtomicBoolean(false);
    private final AtomicBoolean alertPollRunning    = new AtomicBoolean(false);

    private final JmxService          jmxService;
    private final OshiService         oshiService;
    private final FfprobeService      ffprobeService;
    private final AlertService        alertService;
    private final WindowsServiceReader wsReader;
    private final DataStore           store = DataStore.getInstance();

    private ScheduledFuture<?> jmxFuture, oshiFuture, ffprobeFuture, alertFuture, staleFuture;

    public SchedulerService(JmxService jmxService,
                            OshiService oshiService,
                            FfprobeService ffprobeService,
                            AlertService alertService,
                            WindowsServiceReader wsReader) {
        this.jmxService      = jmxService;
        this.oshiService     = oshiService;
        this.ffprobeService  = ffprobeService;
        this.alertService    = alertService;
        this.wsReader        = wsReader;
    }

    public void start() {
        AppSettings cfg = store.getSettings();
        long pollSec = cfg.getPollIntervalSeconds();

        // JMX + Windows Service polling
        jmxFuture = executor.scheduleAtFixedRate(() -> {
            if (jmxPollRunning.compareAndSet(false, true)) {
                try {
                    boolean ok = jmxService.poll();
                    if (!ok) log.warn("JMX poll failed – will retry next cycle");
                    // Also refresh Windows service status
                    wsReader.refresh();
                } catch (Exception e) {
                    log.error("JMX scheduler error", e);
                } finally {
                    jmxPollRunning.set(false);
                }
            } else {
                log.debug("JMX poll skipped – previous cycle still running");
            }
        }, 0, pollSec, TimeUnit.SECONDS);

        // OSHI hardware metrics (every 15 seconds)
        oshiFuture = executor.scheduleAtFixedRate(() -> {
            if (oshiPollRunning.compareAndSet(false, true)) {
                try {
                    oshiService.poll();
                } catch (Exception e) {
                    log.error("OSHI scheduler error", e);
                } finally {
                    oshiPollRunning.set(false);
                }
            }
        }, 2, 15, TimeUnit.SECONDS);

        // ffprobe stream analytics (same interval as JMX)
        ffprobeFuture = executor.scheduleAtFixedRate(() -> {
            if (ffprobePollRunning.compareAndSet(false, true)) {
                try {
                    ffprobeService.probeAll();
                } catch (Exception e) {
                    log.error("ffprobe scheduler error", e);
                } finally {
                    ffprobePollRunning.set(false);
                }
            } else {
                log.debug("ffprobe poll skipped – previous cycle still running");
            }
        }, 5, pollSec, TimeUnit.SECONDS);

        // Alert evaluation (every 10 seconds)
        alertFuture = executor.scheduleAtFixedRate(() -> {
            if (alertPollRunning.compareAndSet(false, true)) {
                try {
                    alertService.evaluate();
                } catch (Exception e) {
                    log.error("Alert scheduler error", e);
                } finally {
                    alertPollRunning.set(false);
                }
            }
        }, 8, 10, TimeUnit.SECONDS);

        // Stale-data marker (every pollInterval)
        staleFuture = executor.scheduleAtFixedRate(store::markStaleIfExpired,
                pollSec, pollSec, TimeUnit.SECONDS);

        log.info("SchedulerService started (pollInterval={}s)", pollSec);
    }

    public void stop() {
        cancelIfNotNull(jmxFuture, oshiFuture, ffprobeFuture, alertFuture, staleFuture);
        executor.shutdown();
        try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        log.info("SchedulerService stopped");
    }

    private void cancelIfNotNull(ScheduledFuture<?>... futures) {
        for (ScheduledFuture<?> f : futures) {
            if (f != null) f.cancel(false);
        }
    }

    /** Restart with updated poll interval. */
    public void restart() {
        stop();
        start();
    }
}
