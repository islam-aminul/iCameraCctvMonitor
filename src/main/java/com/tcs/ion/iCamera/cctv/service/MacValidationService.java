package com.tcs.ion.iCamera.cctv.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.MacValidationResult;
import com.tcs.ion.iCamera.cctv.util.MacValidationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs cloud-side MAC validation for the monitored iCamera Proxy.
 *
 * <h3>Steps performed on each {@link #validate(long, long)} call:</h3>
 * <ol>
 *   <li>Reads endpoint configuration from {@code mac-validation.properties}
 *       (at the executable location) via {@link MacValidationConfig}.</li>
 *   <li>Detects the local network interface actually used for outbound traffic to
 *       the configured host using a UDP probe (no data is sent).</li>
 *   <li>Reads the hardware (MAC) address of that interface.</li>
 *   <li>POSTs to the remote MAC validation API
 *       ({@code POST https://<host>/iCAMERAStreamingFW/GetProxyMacValidationDataServlet})
 *       with mandatory header {@code X-Requested-With: XMLHttpRequest} and
 *       form body {@code org_id=<long>&proxy_id=<long>&mac_address=<string>}.</li>
 *   <li>Parses the JSON response, handling both {@code mac-details} and
 *       {@code Mac-details} key variants.</li>
 *   <li>Evaluates and returns a {@link MacValidationResult} covering all
 *       documented scenarios.</li>
 * </ol>
 *
 * <h3>API contract (as documented):</h3>
 * <p>Request: {@code Content-Type: application/x-www-form-urlencoded}<br>
 * Success response:
 * <pre>{@code
 * {"status":"OK","data":{"existing proxy details":{...},"mac-details":[...]}}
 * }</pre>
 * Error response:
 * <pre>{@code
 * {"status":"error","error":"org id and proxy id are mandatory"}
 * }</pre>
 */
public class MacValidationService {

    private static final Logger log = LoggerFactory.getLogger(MacValidationService.class);

    /** UDP port used only for interface-route detection (no packet is actually sent). */
    private static final int PROBE_PORT = 443;

    private final HttpService httpService;

    public MacValidationService() {
        this.httpService = new HttpService();
    }

    /** Package-visible constructor for unit testing with a mock HttpService. */
    MacValidationService(HttpService httpService) {
        this.httpService = httpService;
    }

    // ---- Public API --------------------------------------------------------

    /**
     * Runs a full MAC validation cycle.
     *
     * @param orgId   Organisation ID (mandatory – must be positive).
     * @param proxyId Proxy ID (mandatory – must be positive).
     * @return a {@link MacValidationResult} describing the outcome.  Never null.
     *         On any failure the scenario will be {@link MacValidationResult.Scenario#VALIDATION_FAILED}.
     */
    public MacValidationResult validate(long orgId, long proxyId) {
        MacValidationConfig cfg;
        try {
            cfg = MacValidationConfig.load();
        } catch (Exception e) {
            return failed(null, null, null,
                    "Failed to load mac-validation.properties: " + e.getMessage());
        }

        String host = cfg.getCloudDcHost();

        // Step 1 – detect outbound interface & read MAC
        InterfaceInfo iface;
        try {
            iface = detectOutboundInterface(host);
            log.info("Outbound interface for {}: {} ({})", host, iface.name, iface.macAddress);
        } catch (Exception e) {
            log.error("Interface detection failed for host {}: {}", host, e.getMessage());
            return failed(host, null, null,
                    "Interface detection failed: " + e.getMessage());
        }

        // Step 2 – call remote API
        Map<String, String> formParams = new LinkedHashMap<>();
        formParams.put("org_id",      String.valueOf(orgId));
        formParams.put("proxy_id",    String.valueOf(proxyId));
        formParams.put("mac_address", iface.macAddress);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Requested-With", "XMLHttpRequest");

        String responseBody;
        try {
            responseBody = httpService.postForm(cfg.getMacValidationApiUrl(), formParams, headers);
        } catch (Exception e) {
            log.error("API call to {} failed: {}", cfg.getMacValidationApiUrl(), e.getMessage());
            return failed(host, iface.name, iface.macAddress,
                    "API call failed: " + e.getMessage());
        }

        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.error("Empty response from MAC validation API at {}", cfg.getMacValidationApiUrl());
            return failed(host, iface.name, iface.macAddress,
                    "Empty or null response from API");
        }

        // Step 3 – parse response
        ParsedResponse parsed;
        try {
            parsed = parseResponse(responseBody);
        } catch (Exception e) {
            log.error("Response parsing failed: {}", e.getMessage());
            return failed(host, iface.name, iface.macAddress,
                    "Response parsing failed: " + e.getMessage());
        }

        if (!parsed.ok) {
            log.warn("API returned error: {}", parsed.errorMessage);
            return failed(host, iface.name, iface.macAddress,
                    "API error: " + parsed.errorMessage);
        }

        // Step 4 – evaluate scenario
        return evaluateScenario(host, iface.name, iface.macAddress,
                parsed.cloudMac, parsed.conflictingProxies);
    }

    // ---- Interface detection -----------------------------------------------

    /**
     * Uses a no-op UDP socket connection to discover which local IP (and therefore
     * which network interface) the OS would use to reach {@code host}.  The
     * hardware address of that interface is then read and formatted.
     */
    private InterfaceInfo detectOutboundInterface(String host) throws Exception {
        InetAddress localAddr;
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(host), PROBE_PORT);
            localAddr = socket.getLocalAddress();
        }

        if (localAddr == null || localAddr.isAnyLocalAddress()) {
            throw new Exception(
                    "Could not determine a specific local address for host '" + host + "'");
        }

        NetworkInterface ni = NetworkInterface.getByInetAddress(localAddr);
        if (ni == null) {
            throw new Exception(
                    "No NetworkInterface found for local address " + localAddr.getHostAddress());
        }

        byte[] hwAddr = ni.getHardwareAddress();
        if (hwAddr == null || hwAddr.length == 0) {
            throw new Exception(
                    "Interface '" + ni.getName() + "' has no hardware (MAC) address");
        }

        return new InterfaceInfo(ni.getName(), formatMac(hwAddr));
    }

    /** Formats a raw byte array as upper-case colon-separated hex (e.g. {@code AA:BB:CC:DD:EE:FF}). */
    private static String formatMac(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    // ---- Response parsing --------------------------------------------------

    private ParsedResponse parseResponse(String json) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalArgumentException("Response is not valid JSON: " + e.getMessage());
        }

        String status = stringOrNull(root, "status");

        if ("error".equalsIgnoreCase(status)) {
            String errMsg = stringOrNull(root, "error");
            return ParsedResponse.error(errMsg != null ? errMsg : "Unknown API error");
        }

        if (!"OK".equalsIgnoreCase(status)) {
            throw new IllegalArgumentException("Unexpected status value: '" + status + "'");
        }

        JsonObject data = root.getAsJsonObject("data");
        if (data == null) {
            throw new IllegalArgumentException("Response missing 'data' object");
        }

        // "existing proxy details" – key contains spaces
        JsonObject existingProxy = null;
        if (data.has("existing proxy details") && !data.get("existing proxy details").isJsonNull()) {
            existingProxy = data.getAsJsonObject("existing proxy details");
        }

        // mac-details / Mac-details (handle documented key variation)
        JsonArray macDetailsArr = null;
        if (data.has("mac-details") && !data.get("mac-details").isJsonNull()) {
            macDetailsArr = data.getAsJsonArray("mac-details");
        } else if (data.has("Mac-details") && !data.get("Mac-details").isJsonNull()) {
            macDetailsArr = data.getAsJsonArray("Mac-details");
        }

        // Cloud MAC – try both "mac_address" (underscore) and "mac address" (space) variants
        String cloudMac = null;
        if (existingProxy != null) {
            cloudMac = stringOrNull(existingProxy, "mac_address");
            if (cloudMac == null) {
                cloudMac = stringOrNull(existingProxy, "mac address");
            }
        }

        // Conflicting proxies – extract display name / ID from each record
        List<String> conflicts = new ArrayList<>();
        if (macDetailsArr != null) {
            for (JsonElement elem : macDetailsArr) {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();

                String name = stringOrNull(obj, "proxy_name");
                if (name == null) name = stringOrNull(obj, "proxy name");
                if (name == null) {
                    String id = stringOrNull(obj, "proxy_id");
                    if (id == null) id = stringOrNull(obj, "proxy id");
                    name = (id != null) ? "Proxy-" + id : elem.toString();
                }
                conflicts.add(name);
            }
        }

        return ParsedResponse.ok(cloudMac, conflicts);
    }

    /** Returns the string value of {@code key} from {@code obj}, or null when absent/null/blank. */
    private static String stringOrNull(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        String v = obj.get(key).getAsString().trim();
        return v.isEmpty() ? null : v;
    }

    // ---- Scenario evaluation -----------------------------------------------

    /**
     * Evaluates all six documented scenarios and builds the corresponding
     * {@link MacValidationResult}.
     *
     * <table border="1">
     *   <tr><th>cloudMac present?</th><th>MAC matches?</th><th>Conflicts?</th><th>Scenario</th></tr>
     *   <tr><td>yes</td><td>yes</td><td>no</td><td>MAC_MATCH_NO_CONFLICT</td></tr>
     *   <tr><td>yes</td><td>no</td><td>no</td><td>MAC_MISMATCH_NO_CONFLICT</td></tr>
     *   <tr><td>yes</td><td>no</td><td>yes</td><td>MAC_MISMATCH_WITH_CONFLICT</td></tr>
     *   <tr><td>no</td><td>–</td><td>yes</td><td>NO_MAPPING_MAC_REGISTERED_ELSEWHERE</td></tr>
     *   <tr><td>no</td><td>–</td><td>no</td><td>NO_MAPPING_MAC_FREE</td></tr>
     * </table>
     */
    private MacValidationResult evaluateScenario(
            String host,
            String ifaceName,
            String localMac,
            String cloudMac,
            List<String> conflictingProxies) {

        boolean cloudMacPresent = cloudMac != null && !cloudMac.isEmpty();
        boolean hasConflicts    = conflictingProxies != null && !conflictingProxies.isEmpty();
        boolean macMatches      = cloudMacPresent && localMac.equalsIgnoreCase(cloudMac);

        MacValidationResult.Scenario scenario;
        AlertData.Severity severity;
        String message;

        if (cloudMacPresent && macMatches && !hasConflicts) {
            scenario = MacValidationResult.Scenario.MAC_MATCH_NO_CONFLICT;
            severity = AlertData.Severity.INFO;
            message  = "MAC address matches cloud registration. No conflict detected.";

        } else if (cloudMacPresent && !macMatches && !hasConflicts) {
            scenario = MacValidationResult.Scenario.MAC_MISMATCH_NO_CONFLICT;
            severity = AlertData.Severity.WARNING;
            message  = "Local MAC (" + localMac + ") differs from cloud-registered MAC ("
                    + cloudMac + "). No other proxy uses the local MAC.";

        } else if (cloudMacPresent && !macMatches && hasConflicts) {
            scenario = MacValidationResult.Scenario.MAC_MISMATCH_WITH_CONFLICT;
            severity = AlertData.Severity.CRITICAL;
            message  = "Local MAC (" + localMac + ") differs from cloud-registered MAC ("
                    + cloudMac + ") and is already mapped to "
                    + conflictingProxies.size() + " other proxy/proxies.";

        } else if (!cloudMacPresent && hasConflicts) {
            scenario = MacValidationResult.Scenario.NO_MAPPING_MAC_REGISTERED_ELSEWHERE;
            severity = AlertData.Severity.WARNING;
            message  = "No cloud MAC mapping found for this proxy. The local MAC ("
                    + localMac + ") is already registered to "
                    + conflictingProxies.size() + " other proxy/proxies.";

        } else {
            // !cloudMacPresent && !hasConflicts
            scenario = MacValidationResult.Scenario.NO_MAPPING_MAC_FREE;
            severity = AlertData.Severity.INFO;
            message  = "No cloud MAC mapping found for this proxy. The local MAC ("
                    + localMac + ") is not registered anywhere remotely.";
        }

        log.info("MAC validation scenario={} severity={} msg='{}'", scenario, severity, message);

        return MacValidationResult.builder()
                .destinationHost(host)
                .outboundInterface(ifaceName)
                .localMac(localMac)
                .cloudMac(cloudMac)
                .conflictingProxies(conflictingProxies)
                .scenario(scenario)
                .alertSeverity(severity)
                .alertMessage(message)
                .build();
    }

    // ---- Helpers -----------------------------------------------------------

    /** Builds a VALIDATION_FAILED result with CRITICAL severity. */
    private static MacValidationResult failed(
            String host, String ifaceName, String localMac, String detail) {
        log.error("MAC validation failed: {}", detail);
        return MacValidationResult.builder()
                .destinationHost(host)
                .outboundInterface(ifaceName)
                .localMac(localMac)
                .scenario(MacValidationResult.Scenario.VALIDATION_FAILED)
                .alertSeverity(AlertData.Severity.CRITICAL)
                .alertMessage("MAC validation failed.")
                .errorDetail(detail)
                .build();
    }

    // ---- Inner types -------------------------------------------------------

    /** Carries the interface name and formatted MAC address from interface detection. */
    private static final class InterfaceInfo {
        final String name;
        final String macAddress;
        InterfaceInfo(String name, String macAddress) {
            this.name       = name;
            this.macAddress = macAddress;
        }
    }

    /** Raw result of parsing the API response JSON. */
    private static final class ParsedResponse {
        final boolean      ok;
        final String       cloudMac;
        final List<String> conflictingProxies;
        final String       errorMessage;

        private ParsedResponse(boolean ok, String cloudMac,
                               List<String> conflictingProxies, String errorMessage) {
            this.ok                 = ok;
            this.cloudMac           = cloudMac;
            this.conflictingProxies = conflictingProxies;
            this.errorMessage       = errorMessage;
        }

        static ParsedResponse ok(String cloudMac, List<String> conflicts) {
            return new ParsedResponse(true, cloudMac, conflicts, null);
        }

        static ParsedResponse error(String message) {
            return new ParsedResponse(false, null, null, message);
        }
    }
}
