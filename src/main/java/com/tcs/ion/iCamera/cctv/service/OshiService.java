package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.SystemMetrics;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Uses OSHI to collect hardware and OS-level metrics,
 * and merges them into the SystemMetrics in DataStore.
 */
public class OshiService {

    private static final Logger log = LoggerFactory.getLogger(OshiService.class);

    private final SystemInfo si = new SystemInfo();
    private final HardwareAbstractionLayer hal = si.getHardware();
    private final OperatingSystem os = si.getOperatingSystem();
    private final DataStore store = DataStore.getInstance();

    // Cached static hardware info (populated once)
    private String cpuName;
    private int physicalCores;
    private int logicalCores;
    private double cpuMaxFreqGhz;
    private long totalRamBytes;
    private String ramType;
    private boolean staticInfoLoaded = false;

    private long[] prevCpuTicks;

    public void poll() {
        try {
            SystemMetrics sm = store.getSystemMetrics() != null
                    ? store.getSystemMetrics() : new SystemMetrics();

            if (!staticInfoLoaded) loadStaticInfo(sm);

            // CPU utilization
            CentralProcessor cpu = hal.getProcessor();
            if (prevCpuTicks == null) {
                prevCpuTicks = cpu.getSystemCpuLoadTicks();
                Thread.sleep(200);
            }
            long[] newTicks = cpu.getSystemCpuLoadTicks();
            double cpuLoad = cpu.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100.0;
            prevCpuTicks = newTicks;
            sm.setSystemCpuPercent(cpuLoad);

            // Memory
            GlobalMemory mem = hal.getMemory();
            long totalBytes = mem.getTotal();
            long availBytes = mem.getAvailable();
            sm.setTotalMemoryMb(totalBytes / (1024.0 * 1024.0));
            sm.setFreeMemoryMb(availBytes / (1024.0 * 1024.0));
            sm.setAvailableRamBytes(availBytes);

            // Disks
            List<SystemMetrics.DriveInfo> drives = new ArrayList<>();
            for (HWDiskStore disk : hal.getDiskStores()) {
                SystemMetrics.DriveInfo di = new SystemMetrics.DriveInfo();
                di.setName(disk.getName());
                // Use FileSystem for per-drive free/total space
                di.setTotalSpaceMb(disk.getSize() / (1024 * 1024));
                di.setRpm(disk.getModel().toLowerCase().contains("ssd") ? "SSD" :
                          (disk.getCurrentQueueLength() >= 0 ? "HDD" : "N/A"));
                drives.add(di);
            }
            // Add file store info (better for free space)
            for (oshi.software.os.OSFileStore fs : os.getFileSystem().getFileStores()) {
                String mountName = fs.getMount();
                boolean found = false;
                for (SystemMetrics.DriveInfo di : drives) {
                    if (mountName.startsWith(di.getName().substring(0, Math.min(2, di.getName().length())))) {
                        di.setTotalSpaceMb(fs.getTotalSpace() / (1024 * 1024));
                        di.setFreeSpaceMb(fs.getFreeSpace() / (1024 * 1024));
                        di.setName(fs.getName() + " (" + fs.getMount() + ")");
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    SystemMetrics.DriveInfo di = new SystemMetrics.DriveInfo(
                            fs.getName() + " (" + fs.getMount() + ")",
                            fs.getTotalSpace() / (1024 * 1024),
                            fs.getFreeSpace() / (1024 * 1024));
                    drives.add(di);
                }
            }
            sm.setDrives(drives);

            sm.setCpuName(cpuName);
            sm.setPhysicalCores(physicalCores);
            sm.setLogicalCores(logicalCores);
            sm.setCpuMaxFreqGhz(cpuMaxFreqGhz);
            sm.setTotalRamBytes(totalRamBytes);
            sm.setRamType(ramType);

            store.updateSystemMetrics(sm);
            log.debug("OSHI poll complete – CPU={:.1f}%", cpuLoad);
        } catch (Exception e) {
            log.error("OSHI poll error", e);
        }
    }

    private void loadStaticInfo(SystemMetrics sm) {
        try {
            CentralProcessor cpu = hal.getProcessor();
            cpuName = cpu.getProcessorIdentifier().getName();
            physicalCores = cpu.getPhysicalProcessorCount();
            logicalCores = cpu.getLogicalProcessorCount();
            cpuMaxFreqGhz = cpu.getMaxFreq() / 1_000_000_000.0;

            GlobalMemory mem = hal.getMemory();
            totalRamBytes = mem.getTotal();

            // RAM type from first memory module
            List<PhysicalMemory> modules = mem.getPhysicalMemory();
            if (!modules.isEmpty() && modules.get(0).getMemoryType() != null) {
                ramType = modules.get(0).getMemoryType();
            } else {
                ramType = "DDR";
            }
            staticInfoLoaded = true;
        } catch (Exception e) {
            log.warn("Could not load static hardware info: {}", e.getMessage());
        }
    }
}
