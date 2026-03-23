package com.tcs.ion.iCamera.cctv.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcs.ion.iCamera.cctv.model.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.*;

/**
 * Embedded Jetty REST server for programmatic access to iCamera monitor data.
 * Starts on port 8080 (configurable via AppSettings).
 *
 * Endpoints:
 *   GET  /api/status       – combined status JSON
 *   GET  /api/proxy        – proxy + system metrics
 *   GET  /api/cctv         – all CCTV data
 *   GET  /api/alerts       – unresolved alerts
 *   GET  /api/network      – network speed history
 *   POST /api/alerts/{id}/resolve  – resolve an alert
 */
public class JettyServer {

    private static final Logger log = LoggerFactory.getLogger(JettyServer.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Server server;
    private final DataStore store = DataStore.getInstance();

    public void start() throws Exception {
        int port = store.getSettings().getJettyPort();
        server = new Server(port);

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setContextPath("/");

        ctx.addServlet(new ServletHolder(new StatusServlet()), "/api/status");
        ctx.addServlet(new ServletHolder(new ProxyServlet()), "/api/proxy");
        ctx.addServlet(new ServletHolder(new CctvServlet()), "/api/cctv");
        ctx.addServlet(new ServletHolder(new AlertsServlet()), "/api/alerts/*");
        ctx.addServlet(new ServletHolder(new NetworkServlet()), "/api/network");
        ctx.addServlet(new ServletHolder(new CorsFilter()), "/*"); // CORS for local development

        server.setHandler(ctx);
        server.start();
        log.info("Jetty REST server started on port {}", port);
    }

    public void stop() throws Exception {
        if (server != null) { server.stop(); log.info("Jetty stopped"); }
    }

    // ---- Servlets ----

    private class StatusServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("jmxConnected", store.isJmxConnected());
            status.put("jmxUrl", store.getActiveJmxUrl());
            status.put("lastPoll", store.getLastPollTime() != null ? store.getLastPollTime().toString() : null);
            status.put("totalCctv", store.getTotalCctvCount());
            status.put("activeCctv", store.getActiveCctvCount());
            status.put("activeAlerts", store.getUnresolvedAlerts().size());
            ProxyData pd = store.getProxyData();
            if (pd != null) {
                status.put("proxyId", pd.getProxyId());
                status.put("proxyName", pd.getProxyName());
                status.put("proxyStatus", pd.getStatus());
                status.put("tcCode", pd.getTcCode());
            }
            writeJson(resp, status);
        }
    }

    private class ProxyServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("proxy", store.getProxyData());
            result.put("systemMetrics", store.getSystemMetrics());
            writeJson(resp, result);
        }
    }

    private class CctvServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            writeJson(resp, store.getAllCctv());
        }
    }

    private class AlertsServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            writeJson(resp, store.getUnresolvedAlerts());
        }
        @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            String path = req.getPathInfo(); // /{id}/resolve
            if (path != null && path.endsWith("/resolve")) {
                String id = path.replace("/", "").replace("resolve", "").trim();
                store.resolveAlert(id);
                writeJson(resp, Collections.singletonMap("result", "resolved"));
            }
        }
    }

    private class NetworkServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
                throws IOException {
            writeJson(resp, store.getNetworkHistory());
        }
    }

    private class CorsFilter extends HttpServlet implements Filter {
        @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setHeader("Access-Control-Allow-Origin", "*");
            httpResp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            httpResp.setHeader("Access-Control-Allow-Headers", "Content-Type");
            chain.doFilter(request, response);
        }
        @Override public void init(FilterConfig filterConfig) {}
        @Override public void destroy() {}
    }

    private void writeJson(HttpServletResponse resp, Object obj) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(GSON.toJson(obj));
    }
}
