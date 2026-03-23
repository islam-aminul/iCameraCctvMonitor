package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.VmsInfo;
import com.tcs.ion.iCamera.cctv.model.VmsInfo.VmsVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Detects Video Management Software (VMS) running on the local Windows machine.
 *
 * Detection strategy (two passes):
 *   1. Live process scan via OSHI – matches known VMS executable names
 *   2. Windows Service scan via SC.exe – catches VMS installed as services
 *      even when no GUI is open (e.g. Milestone Recording Server)
 *
 * Supported vendors: Milestone, Hikvision, Dahua, Avigilon, Genetec,
 *   Axis, Bosch, Hanwha, Pelco, Exacq, NUUO, Digifort.
 */
public class VmsDetectionService {

    private static final Logger log = LoggerFactory.getLogger(VmsDetectionService.class);

    private final DataStore store = DataStore.getInstance();
    private final OperatingSystem os = new SystemInfo().getOperatingSystem();

    // ---- VMS catalogue ----
    // Map: lower-case exe name → vendor
    private static final Map<String, VmsVendor> PROCESS_MAP = new LinkedHashMap<>();
    // Map: lower-case Windows service display-name fragment → vendor
    private static final Map<String, VmsVendor> SERVICE_MAP = new LinkedHashMap<>();

    static {
        // ----- Milestone XProtect -----
        PROCESS_MAP.put("recordingserver.exe",         VmsVendor.MILESTONE);
        PROCESS_MAP.put("managementserver.exe",         VmsVendor.MILESTONE);
        PROCESS_MAP.put("eventserver.exe",              VmsVendor.MILESTONE);
        PROCESS_MAP.put("xprotect.exe",                 VmsVendor.MILESTONE);
        PROCESS_MAP.put("milestonemanagementclient.exe",VmsVendor.MILESTONE);
        PROCESS_MAP.put("smartclient.exe",              VmsVendor.MILESTONE);
        SERVICE_MAP.put("milestone xprotect",           VmsVendor.MILESTONE);
        SERVICE_MAP.put("xprotect recording server",    VmsVendor.MILESTONE);
        SERVICE_MAP.put("xprotect management server",   VmsVendor.MILESTONE);

        // ----- Hikvision -----
        PROCESS_MAP.put("ivms-4200.exe",                VmsVendor.HIKVISION);
        PROCESS_MAP.put("ivms-4500.exe",                VmsVendor.HIKVISION);
        PROCESS_MAP.put("hikcentral.exe",               VmsVendor.HIKVISION);
        PROCESS_MAP.put("hikcentral-professional.exe",  VmsVendor.HIKVISION);
        PROCESS_MAP.put("hcnetsdk.exe",                 VmsVendor.HIKVISION);
        PROCESS_MAP.put("hikvisionvms.exe",             VmsVendor.HIKVISION);
        SERVICE_MAP.put("hikvision",                    VmsVendor.HIKVISION);
        SERVICE_MAP.put("hikcentral",                   VmsVendor.HIKVISION);

        // ----- Dahua -----
        PROCESS_MAP.put("smartpss.exe",                 VmsVendor.DAHUA);
        PROCESS_MAP.put("dss.exe",                      VmsVendor.DAHUA);
        PROCESS_MAP.put("dssexpress.exe",               VmsVendor.DAHUA);
        PROCESS_MAP.put("pss.exe",                      VmsVendor.DAHUA);
        PROCESS_MAP.put("dahuapss.exe",                 VmsVendor.DAHUA);
        SERVICE_MAP.put("dahua",                        VmsVendor.DAHUA);
        SERVICE_MAP.put("dss express",                  VmsVendor.DAHUA);
        SERVICE_MAP.put("smartpss",                     VmsVendor.DAHUA);

        // ----- Avigilon -----
        PROCESS_MAP.put("avigilon.exe",                 VmsVendor.AVIGILON);
        PROCESS_MAP.put("acc.exe",                      VmsVendor.AVIGILON);
        PROCESS_MAP.put("controlcenter.exe",            VmsVendor.AVIGILON);
        SERVICE_MAP.put("avigilon",                     VmsVendor.AVIGILON);
        SERVICE_MAP.put("acc server",                   VmsVendor.AVIGILON);

        // ----- Genetec -----
        PROCESS_MAP.put("securitycenter.exe",           VmsVendor.GENETEC);
        PROCESS_MAP.put("genetec.exe",                  VmsVendor.GENETEC);
        PROCESS_MAP.put("genetec.server.exe",           VmsVendor.GENETEC);
        SERVICE_MAP.put("genetec",                      VmsVendor.GENETEC);
        SERVICE_MAP.put("security center",              VmsVendor.GENETEC);

        // ----- Axis -----
        PROCESS_MAP.put("axiscamerastation.exe",        VmsVendor.AXIS);
        PROCESS_MAP.put("acs.exe",                      VmsVendor.AXIS);
        SERVICE_MAP.put("axis camera station",          VmsVendor.AXIS);

        // ----- Bosch -----
        PROCESS_MAP.put("bvmsvideoclient.exe",          VmsVendor.BOSCH);
        PROCESS_MAP.put("bvms.exe",                     VmsVendor.BOSCH);
        PROCESS_MAP.put("divar.exe",                    VmsVendor.BOSCH);
        SERVICE_MAP.put("bosch bvms",                   VmsVendor.BOSCH);
        SERVICE_MAP.put("bvms server",                  VmsVendor.BOSCH);

        // ----- Hanwha (Samsung) -----
        PROCESS_MAP.put("ssm.exe",                      VmsVendor.HANWHA);
        PROCESS_MAP.put("wisenetsmartsearch.exe",        VmsVendor.HANWHA);
        PROCESS_MAP.put("ssmserver.exe",                 VmsVendor.HANWHA);
        SERVICE_MAP.put("hanwha",                        VmsVendor.HANWHA);
        SERVICE_MAP.put("wisenet ssm",                   VmsVendor.HANWHA);
        SERVICE_MAP.put("samsung ssm",                   VmsVendor.HANWHA);

        // ----- Pelco -----
        PROCESS_MAP.put("videoxpert.exe",               VmsVendor.PELCO);
        PROCESS_MAP.put("pelcodigitalsentryserver.exe", VmsVendor.PELCO);
        PROCESS_MAP.put("endura.exe",                   VmsVendor.PELCO);
        SERVICE_MAP.put("pelco",                        VmsVendor.PELCO);
        SERVICE_MAP.put("videoxpert",                   VmsVendor.PELCO);

        // ----- Exacq -----
        PROCESS_MAP.put("exacqvision.exe",              VmsVendor.EXACQ);
        PROCESS_MAP.put("edvrclient.exe",               VmsVendor.EXACQ);
        SERVICE_MAP.put("exacq",                        VmsVendor.EXACQ);
        SERVICE_MAP.put("exacqvision server",           VmsVendor.EXACQ);

        // ----- NUUO -----
        PROCESS_MAP.put("nuuocrystal.exe",              VmsVendor.NUUO);
        PROCESS_MAP.put("nuuo.exe",                     VmsVendor.NUUO);
        SERVICE_MAP.put("nuuo",                         VmsVendor.NUUO);

        // ----- Digifort -----
        PROCESS_MAP.put("digifort.exe",                 VmsVendor.DIGIFORT);
        PROCESS_MAP.put("digifortserver.exe",           VmsVendor.DIGIFORT);
        SERVICE_MAP.put("digifort",                     VmsVendor.DIGIFORT);
    }

    /**
     * Scans running processes and Windows services, populates DataStore with results.
     */
    public void scan() {
        List<VmsInfo> detected = new ArrayList<>();

        // Pass 1 – live process scan via OSHI
        detected.addAll(scanProcesses());

        // Pass 2 – Windows service scan (catches background / headless VMS servers)
        detected.addAll(scanWindowsServices(detected));

        store.updateVmsData(detected);
        log.info("VMS scan complete – {} VMS instance(s) detected", detected.size());

        // Raise alerts for any running VMS
        for (VmsInfo vms : detected) {
            if (vms.isRunning()) {
                store.addAlertIfNew(
                    "VMS_RUNNING_" + vms.getVendor().name(),
                    new AlertData(AlertData.Severity.WARNING, AlertData.Category.SYSTEM,
                        "VMS-" + vms.getVendor().name(), "VMS_DETECTED",
                        "VMS software detected and running: " + vms.getVendorDisplayName()
                        + " (PID " + vms.getPid() + ", process: " + vms.getProcessName() + ")")
                );
            }
        }
    }

    // ---- Process scan ----

    private List<VmsInfo> scanProcesses() {
        List<VmsInfo> found = new ArrayList<>();
        List<OSProcess> processes = os.getProcesses();

        for (OSProcess proc : processes) {
            String exeName = extractExeName(proc.getName()).toLowerCase();
            VmsVendor vendor = PROCESS_MAP.get(exeName);
            if (vendor == null) {
                // Also check if full path contains a vendor directory name
                vendor = guessVendorFromPath(proc.getPath());
            }
            if (vendor != null) {
                VmsInfo vms = new VmsInfo();
                vms.setVendor(vendor);
                vms.setProcessName(proc.getName());
                vms.setPid(proc.getProcessID());
                vms.setCpuPercent(proc.getProcessCpuLoadCumulative() * 100.0);
                vms.setMemoryMb(proc.getResidentSetSize() / (1024 * 1024));
                vms.setInstallPath(proc.getPath());
                vms.setUser(proc.getUser());
                vms.setStatus("RUNNING");
                vms.setRunning(true);
                found.add(vms);
                log.debug("VMS process detected: {} ({})", vendor.getDisplayName(), proc.getName());
            }
        }
        return found;
    }

    // ---- Windows Service scan ----

    private List<VmsInfo> scanWindowsServices(List<VmsInfo> alreadyFound) {
        List<VmsInfo> found = new ArrayList<>();
        Set<VmsVendor> runningVendors = new HashSet<>();
        for (VmsInfo v : alreadyFound) runningVendors.add(v.getVendor());

        try {
            // "sc query type= all state= all" lists all services
            ProcessBuilder pb = new ProcessBuilder("sc", "query", "type=", "all", "state=", "all");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) lines.add(line);
            }
            proc.waitFor();

            // Parse service blocks
            String currentServiceName = null;
            String currentDisplayName = null;
            String currentState = null;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("SERVICE_NAME:")) {
                    currentServiceName = trimmed.substring("SERVICE_NAME:".length()).trim();
                } else if (trimmed.startsWith("DISPLAY_NAME:")) {
                    currentDisplayName = trimmed.substring("DISPLAY_NAME:".length()).trim();
                } else if (trimmed.startsWith("STATE")) {
                    currentState = trimmed.contains("RUNNING") ? "RUNNING" : "STOPPED";
                } else if (trimmed.isEmpty() && currentDisplayName != null) {
                    // End of a service block – check if it's a VMS
                    VmsVendor vendor = matchService(currentDisplayName, currentServiceName);
                    if (vendor != null && !runningVendors.contains(vendor)) {
                        VmsInfo vms = new VmsInfo();
                        vms.setVendor(vendor);
                        vms.setProcessName(currentServiceName);
                        vms.setStatus("RUNNING".equals(currentState) ? "RUNNING" : "STOPPED");
                        vms.setRunning("RUNNING".equals(currentState));
                        vms.setInstallPath("(Windows Service: " + currentDisplayName + ")");
                        found.add(vms);
                        if ("RUNNING".equals(currentState)) runningVendors.add(vendor);
                        log.debug("VMS service detected: {} [{}]", vendor.getDisplayName(), currentState);
                    }
                    currentServiceName = null;
                    currentDisplayName = null;
                    currentState = null;
                }
            }
        } catch (Exception e) {
            log.warn("Windows service VMS scan failed: {}", e.getMessage());
        }
        return found;
    }

    private VmsVendor matchService(String displayName, String serviceName) {
        String dl = displayName.toLowerCase();
        String sl = serviceName != null ? serviceName.toLowerCase() : "";
        for (Map.Entry<String, VmsVendor> entry : SERVICE_MAP.entrySet()) {
            if (dl.contains(entry.getKey()) || sl.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private VmsVendor guessVendorFromPath(String path) {
        if (path == null || path.isEmpty()) return null;
        String lower = path.toLowerCase();
        if (lower.contains("milestone"))   return VmsVendor.MILESTONE;
        if (lower.contains("hikvision"))   return VmsVendor.HIKVISION;
        if (lower.contains("dahua"))       return VmsVendor.DAHUA;
        if (lower.contains("avigilon"))    return VmsVendor.AVIGILON;
        if (lower.contains("genetec"))     return VmsVendor.GENETEC;
        if (lower.contains("axis camera")) return VmsVendor.AXIS;
        if (lower.contains("bosch"))       return VmsVendor.BOSCH;
        if (lower.contains("hanwha") || lower.contains("wisenet")) return VmsVendor.HANWHA;
        if (lower.contains("pelco"))       return VmsVendor.PELCO;
        if (lower.contains("exacq"))       return VmsVendor.EXACQ;
        if (lower.contains("nuuo"))        return VmsVendor.NUUO;
        if (lower.contains("digifort"))    return VmsVendor.DIGIFORT;
        return null;
    }

    private String extractExeName(String name) {
        if (name == null) return "";
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return sep >= 0 ? name.substring(sep + 1) : name;
    }
}
