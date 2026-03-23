package com.tcs.ion.iCamera.cctv.service;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Generic HTTP client for REST API calls (MAC sync, cloud alert push, etc.).
 */
public class HttpService {

    private static final Logger log = LoggerFactory.getLogger(HttpService.class);
    private static final int TIMEOUT_MS = 10_000;
    private static final Gson GSON = new Gson();

    private final CloseableHttpClient httpClient;

    public HttpService() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MS)
                .setSocketTimeout(TIMEOUT_MS)
                .setConnectionRequestTimeout(TIMEOUT_MS)
                .build();
        httpClient = HttpClients.custom().setDefaultRequestConfig(config).build();
    }

    /**
     * HTTP GET – returns response body as String, or null on error.
     */
    public String get(String url) {
        try {
            HttpGet request = new HttpGet(url);
            try (CloseableHttpResponse resp = httpClient.execute(request)) {
                int code = resp.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(resp.getEntity());
                log.debug("GET {} -> {} {}", url, code, body);
                return body;
            }
        } catch (IOException e) {
            log.error("GET {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * HTTP POST with JSON body – returns response body as String, or null on error.
     */
    public String postJson(String url, Object body) {
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/json");
            request.setEntity(new StringEntity(GSON.toJson(body), "UTF-8"));
            try (CloseableHttpResponse resp = httpClient.execute(request)) {
                int code = resp.getStatusLine().getStatusCode();
                String respBody = EntityUtils.toString(resp.getEntity());
                log.debug("POST {} -> {} {}", url, code, respBody);
                return respBody;
            }
        } catch (IOException e) {
            log.error("POST {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Triggers MAC address sync by POSTing to the configured sync endpoint.
     * URL to be provided later; placeholder implementation.
     */
    public boolean syncMacAddress(int proxyId, String newMac) {
        // TODO: Replace with actual MAC sync endpoint
        String endpoint = "http://localhost:8081/api/proxy/" + proxyId + "/syncMac";
        Map<String, String> payload = java.util.Collections.singletonMap("macAddress", newMac);
        String response = postJson(endpoint, payload);
        return response != null;
    }

    /**
     * Sends an alert payload to a cloud REST endpoint (if configured).
     */
    public boolean pushAlertToCloud(String alertJson) {
        // TODO: Replace with actual cloud alert endpoint
        String endpoint = System.getProperty("cloud.alert.url", "");
        if (endpoint.isEmpty()) return false;
        String response = postJson(endpoint, alertJson);
        return response != null;
    }

    public void close() {
        try { httpClient.close(); } catch (IOException ignored) {}
    }
}
