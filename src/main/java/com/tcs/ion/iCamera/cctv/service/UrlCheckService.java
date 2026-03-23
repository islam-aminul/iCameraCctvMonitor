package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.UrlCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks HTTPS connectivity and SSL certificate validity for the three monitored
 * TCS ION endpoints.  Results are stored in DataStore and evaluated by AlertService.
 *
 * Strategy:
 *   1. TCP socket to port 443 → determines reachability (CRITICAL alert if down)
 *   2. HttpsURLConnection with strict SSL → validates certificate chain and expiry
 *      (WARNING alert if SSL handshake fails or cert expires within 30 days)
 */
public class UrlCheckService {

    private static final Logger log = LoggerFactory.getLogger(UrlCheckService.class);
    private static final int TIMEOUT_MS = 8_000;

    static final List<String> MONITORED_HOSTS = List.of(
            "g01.tcsion.com",
            "cctv4.tcsion.com",
            "cctv8.tcsion.com"
    );

    private final DataStore store = DataStore.getInstance();

    /** Run checks for all monitored hosts and persist results. */
    public void checkAll() {
        List<UrlCheckResult> results = new ArrayList<>();
        for (String host : MONITORED_HOSTS) {
            results.add(check(host));
        }
        store.updateUrlCheckResults(results);
    }

    // ---- private ----

    private UrlCheckResult check(String host) {
        log.debug("URL check: {}", host);

        // ── Step 1: TCP reachability on port 443 ──────────────────────────────
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 443), TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("Host {} unreachable on port 443: {}", host, e.getMessage());
            return new UrlCheckResult(host, false, 0, false, null, null, 0,
                    "Connection refused / timed out", Instant.now());
        }

        // ── Step 2: HTTPS + SSL certificate check ─────────────────────────────
        try {
            URL url = new URL("https://" + host);
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.connect();

            int httpStatus = conn.getResponseCode();
            Certificate[] certs = conn.getServerCertificates();
            conn.disconnect();

            if (certs == null || certs.length == 0) {
                return new UrlCheckResult(host, true, httpStatus, false,
                        null, null, 0, "No certificates returned", Instant.now());
            }

            X509Certificate leaf = (X509Certificate) certs[0];
            leaf.checkValidity();   // throws if expired / not yet valid

            LocalDate expiry   = leaf.getNotAfter().toInstant()
                                     .atZone(ZoneId.systemDefault()).toLocalDate();
            int       daysLeft = (int) ChronoUnit.DAYS.between(LocalDate.now(), expiry);
            String    issuer   = extractCN(leaf.getIssuerX500Principal().getName());

            log.debug("SSL OK for {}: issuer={}, expiry={}, daysLeft={}", host, issuer, expiry, daysLeft);
            return new UrlCheckResult(host, true, httpStatus, true, issuer,
                                      expiry, daysLeft, null, Instant.now());

        } catch (SSLHandshakeException e) {
            log.warn("SSL handshake failed for {}: {}", host, e.getMessage());
            return new UrlCheckResult(host, true, 0, false, null, null, 0,
                    "SSL handshake failed: " + trimMsg(e.getMessage()), Instant.now());

        } catch (CertificateExpiredException e) {
            return new UrlCheckResult(host, true, 0, false, null, null, 0,
                    "Certificate expired", Instant.now());

        } catch (CertificateNotYetValidException e) {
            return new UrlCheckResult(host, true, 0, false, null, null, 0,
                    "Certificate not yet valid", Instant.now());

        } catch (Exception e) {
            log.warn("URL check error for {}: {}", host, e.getMessage());
            return new UrlCheckResult(host, true, 0, false, null, null, 0,
                    trimMsg(e.getMessage()), Instant.now());
        }
    }

    /** Extract the CN value from an X.500 distinguished name string. */
    private String extractCN(String dn) {
        for (String part : dn.split(",")) {
            String p = part.trim();
            if (p.startsWith("CN=")) return p.substring(3);
        }
        return dn;
    }

    private String trimMsg(String msg) {
        if (msg == null) return "Unknown error";
        return msg.length() > 80 ? msg.substring(0, 80) + "\u2026" : msg;
    }
}
