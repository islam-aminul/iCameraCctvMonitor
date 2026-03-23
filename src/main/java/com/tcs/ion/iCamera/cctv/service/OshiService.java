package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.ProcessInfo;
import com.tcs.ion.iCamera.cctv.model.SystemMetrics;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Uses OSHI to collect hardware and OS-level metrics,
 * and merges them into the SystemMetrics in DataStore.
 *
 * Also collects top-5 processes by:
 *   • CPU usage
 *   • Memory (RSS)
 *   • Disk I/O (read + write bytes/s)
 */
public class OshiService {

    private static final Logger log = LoggerFactory.getLogger(OshiService.class);
    private static final int TOP_N = 5;

    private final SystemInfo si  = new SystemInfo();
    private final HardwareAbstractionLayer hal = si.getHardware();
    private final OperatingSystem os = si.getOperatingSystem();
    private final DataStore store = DataStore.getInstance();

    // Cached static hardware info
    private String cpuName;
    private int physicalCores, logicalCores;
    private double cpuMaxFreqGhz;
    private long totalRamBytes;
    private String ramType;
    private boolean staticInfoLoaded = false;

    private long[] prevCpuTicks;

    // Previous process disk-read/write snapshots for delta calculation
    private final Map<Integer, long[]> prevDiskTicks = new HashMap<>(); // pid → [read, write, timeMs]

    public void poll() {
        try {
            SystemMetrics sm = store.getSystemMetrics() != null
                    ? store.getSystemMetrics() : new SystemMetrics();

            if (!staticInfoLoaded) loadStaticInfo();

            // ---- CPU utilisation ----
            CentralProcessor cpu = hal.getProcessor();
            if (prevCpuTicks == null) {
                prevCpuTicks = cpu.getSystemCpuLoadTicks();
                Thread.sleep(250);
            }
            long[] newTicks = cpu.getSystemCpuLoadTicks();
            double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100.0;
            prevCpuTicks = newTicks;
            sm.setSystemCpuPercent(cpuLoad);

            // ---- Memory ----
            GlobalMemory mem = hal.getMemory();
            sm.setTotalMemoryMb(mem.getTotal() / (1024.0 * 1024));
            sm.setFreeMemoryMb(mem.getAvailable() / (1024.0 * 1024));
            sm.setAvailableRamBytes(mem.getAvailable());

            // ---- Disks / file stores ----
            List<SystemMetrics.DriveInfo> drives = buildDriveList();
            sm.setDrives(drives);

            // ---- Static hardware fields ----
            sm.setCpuName(cpuName);
            sm.setPhysicalCores(physicalCores);
            sm.setLogicalCores(logicalCores);
            sm.setCpuMaxFreqGhz(cpuMaxFreqGhz);
            sm.setTotalRamBytes(totalRamBytes);
            sm.setRamType(ramType);

            // ---- Top-N process lists ----
            collectTopProcesses(sm);

            store.updateSystemMetrics(sm);
            log.debug("OSHI poll complete – CPU={:.1f}%", cpuLoad);
        } catch (Exception e) {
            log.error("OSHI poll error", e);
        }
    }

    // ---- Top-N processes ----

    private void collectTopProcesses(SystemMetrics sm) {
        List<OSProcess> allProcs = os.getProcesses();

        // Sort by CPU (cumulative load, descending)
        List<ProcessInfo> topCpu = allProcs.stream()
                .sorted(Comparator.comparingDouble(OSProcess::getProcessCpuLoadCumulative).reversed())
                .limit(TOP_N)
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
        sm.setTopCpuProcesses(topCpu);

        // Sort by RSS memory (descending)
        List<ProcessInfo> topMem = allProcs.stream()
                .sorted(Comparator.comparingLong(OSProcess::getResidentSetSize).reversed())
                .limit(TOP_N)
                .map(this::toProcessInfo)
                .collect(Collectors.toList());
        sm.setTopMemoryProcesses(topMem);

        // Disk I/O – compute bytes/s delta from previous snapshot
        long now = System.currentTimeMillis();
        List<ProcessInfo> topDisk = new ArrayList<>();

        for (OSProcess p : allProcs) {
            int pid = p.getProcessID();
            long reads  = p.getBytesRead();
            long writes = p.getBytesWritten();

            long[] prev = prevDiskTicks.get(pid);
            ProcessInfo pi = toProcessInfo(p);
            if (prev != null && prev[2] > 0) {
                double elapsedSec = (now - prev[2]) / 1000.0;
                if (elapsedSec > 0) {
                    pi.setDiskReadBytesPerSec((long) ((reads  - prev[0]) / elapsedSec));
                    pi.setDiskWriteBytesPerSec((long) ((writes - prev[1]) / elapsedSec));
                }
            }
            prevDiskTicks.put(pid, new long[]{reads, writes, now});
            topDisk.add(pi);
        }
        // Sort by total disk I/O (read + write) descending, take top N
        topDisk.sort(Comparator.comparingLong(
                pi -> -(pi.getDiskReadBytesPerSec() + pi.getDiskWriteBytesPerSec())));
        sm.setTopDiskIoProcesses(topDisk.stream().limit(TOP_N).collect(Collectors.toList()));

        // Prune stale entries from prevDiskTicks (PIDs no longer alive)
        Set<Integer> livePids = allProcs.stream()
                .map(OSProcess::getProcessID).collect(Collectors.toSet());
        prevDiskTicks.keySet().retainAll(livePids);
    }

    private ProcessInfo toProcessInfo(OSProcess p) {
        ProcessInfo pi = new ProcessInfo();
        pi.setPid(p.getProcessID());
        pi.setName(p.getName());
        pi.setPath(p.getPath());
        pi.setCpuPercent(p.getProcessCpuLoadCumulative() * 100.0);
        pi.setMemoryBytes(p.getResidentSetSize());
        pi.setUser(p.getUser());
        return pi;
    }

    // ---- Drive list ----

    private List<SystemMetrics.DriveInfo> buildDriveList() {
        List<SystemMetrics.DriveInfo> drives = new ArrayList<>();

        // Start from physical disk stores to get RPM info
        Map<String, String> rpmByModel = new HashMap<>();
        for (HWDiskStore disk : hal.getDiskStores()) {
            String model = disk.getModel() != null ? disk.getModel() : "";
            String rpm = model.toLowerCase().contains("ssd") ? "SSD" : "HDD";
            rpmByModel.put(disk.getName(), rpm);
        }

        // File stores give us per-partition free/total (more useful)
        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
            if (fs.getTotalSpace() == 0) continue; // skip pseudo filesystems
            SystemMetrics.DriveInfo di = new SystemMetrics.DriveInfo(
                    fs.getName() + " (" + fs.getMount() + ")",
                    fs.getTotalSpace() / (1024 * 1024),
                    fs.getFreeSpace() / (1024 * 1024));
            // Attempt to match RPM from disk store by mount letter
            String rpm = rpmByModel.values().stream().findFirst().orElse("N/A");
            di.setRpm(rpm);
            drives.add(di);
        }
        return drives;
    }

    // ---- Static hardware info ----

    private void loadStaticInfo() {
        try {
            CentralProcessor cpu = hal.getProcessor();
            cpuName        = cpu.getProcessorIdentifier().getName();
            physicalCores  = cpu.getPhysicalProcessorCount();
            logicalCores   = cpu.getLogicalProcessorCount();
            cpuMaxFreqGhz  = cpu.getMaxFreq() / 1_000_000_000.0;

            GlobalMemory mem = hal.getMemory();
            totalRamBytes = mem.getTotal();

            List<PhysicalMemory> modules = mem.getPhysicalMemory();
            ramType = (!modules.isEmpty() && modules.get(0).getMemoryType() != null)
                    ? modules.get(0).getMemoryType() : "DDR";

            staticInfoLoaded = true;
        } catch (Exception e) {
            log.warn("Could not load static hardware info: {}", e.getMessage());
        }
    }
}
