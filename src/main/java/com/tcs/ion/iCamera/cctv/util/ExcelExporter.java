package com.tcs.ion.iCamera.cctv.util;

import com.tcs.ion.iCamera.cctv.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

/**
 * Exports iCamera monitor data to Excel (XLSX) using Apache POI.
 */
public class ExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelExporter.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static String exportAll(DataStore store, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        String filename = outputDir + File.separator + "iCamera_Report_" + LocalDateTime.now().format(DTF) + ".xlsx";

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            createProxySheet(wb, store.getProxyData(), store.getSystemMetrics());
            createCctvSheet(wb, store.getAllCctv());
            createAlertsSheet(wb, store.getAlerts());
            createNetworkSheet(wb, store.getNetworkHistory());

            try (FileOutputStream fos = new FileOutputStream(filename)) {
                wb.write(fos);
            }
        }
        log.info("Exported report to {}", filename);
        return filename;
    }

    private static void createProxySheet(XSSFWorkbook wb, ProxyData pd, SystemMetrics sm) {
        XSSFSheet sheet = wb.createSheet("Proxy & System");
        CellStyle headerStyle = createHeaderStyle(wb);
        int row = 0;

        addRow(sheet, row++, headerStyle, "Parameter", "Value");
        if (pd != null) {
            addRow(sheet, row++, null, "Proxy ID", String.valueOf(pd.getProxyId()));
            addRow(sheet, row++, null, "Proxy Name", pd.getProxyName());
            addRow(sheet, row++, null, "TC Code", pd.getTcCode());
            addRow(sheet, row++, null, "Status", pd.getStatus());
            addRow(sheet, row++, null, "Uptime", pd.getUptimeString());
            addRow(sheet, row++, null, "Process CPU %", String.format("%.2f", pd.getProcessCpuPercent()));
            addRow(sheet, row++, null, "Process Memory MB", String.format("%.2f", pd.getProcessMemoryMb()));
            addRow(sheet, row++, null, "Current MAC", pd.getCurrentMacAddress());
            addRow(sheet, row++, null, "Last MAC", pd.getLastMacAddress());
            addRow(sheet, row++, null, "HSQLDB Status", pd.getHsqldbStatus());
        }
        if (sm != null) {
            row++;
            addRow(sheet, row++, headerStyle, "System Metric", "Value");
            addRow(sheet, row++, null, "System CPU %", String.format("%.2f", sm.getSystemCpuPercent()));
            addRow(sheet, row++, null, "Total RAM MB", String.format("%.2f", sm.getTotalMemoryMb()));
            addRow(sheet, row++, null, "Free RAM MB", String.format("%.2f", sm.getFreeMemoryMb()));
            addRow(sheet, row++, null, "Network Speed MB/s", String.format("%.2f", sm.getNetworkSpeedMbps()));
            for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                addRow(sheet, row++, null, "Drive " + di.getName(),
                        "Total=" + di.getTotalSpaceMb() + "MB Free=" + di.getFreeSpaceMb() + "MB Used=" +
                                String.format("%.1f%%", di.getUsedPercent()));
            }
        }
        autoSizeColumns(sheet, 2);
    }

    private static void createCctvSheet(XSSFWorkbook wb, Collection<CctvData> cctvList) {
        XSSFSheet sheet = wb.createSheet("CCTV Details");
        CellStyle headerStyle = createHeaderStyle(wb);
        int row = 0;

        addRow(sheet, row++, headerStyle, "ID", "Name", "IP", "RTSP URL", "Reachable",
                "Active", "Encoding", "Profile", "FPS", "Resolution", "Bitrate(Kbps)",
                "File Gen", "File Upload", "Reason");

        for (CctvData c : cctvList) {
            addRow(sheet, row++, null,
                    String.valueOf(c.getCctvId()),
                    c.getCctvName(),
                    c.getIpAddress(),
                    c.getRtspUrl(),
                    c.isReachable() ? "Yes" : "No",
                    c.isActive() ? "Active" : "Inactive",
                    c.getEncoding(),
                    c.getStreamProfile(),
                    String.format("%.2f", c.getFps()),
                    c.getResolution(),
                    String.valueOf(c.getBitrateKbps()),
                    c.isFileGenerationActive() ? "Active" : "Stale",
                    c.isFileUploadActive() ? "Active" : "Stale",
                    c.getInactiveReason() != null ? c.getInactiveReason() : "");
        }
        autoSizeColumns(sheet, 14);
    }

    private static void createAlertsSheet(XSSFWorkbook wb, List<AlertData> alerts) {
        XSSFSheet sheet = wb.createSheet("Alerts");
        CellStyle headerStyle = createHeaderStyle(wb);
        int row = 0;
        addRow(sheet, row++, headerStyle, "ID", "Timestamp", "Severity", "Category",
                "Source", "Parameter", "Message", "Resolved");
        for (AlertData a : alerts) {
            addRow(sheet, row++, null,
                    a.getId().substring(0, 8),
                    a.getTimestampDisplay(),
                    a.getSeverity().name(),
                    a.getCategory().name(),
                    a.getSource(),
                    a.getParameter(),
                    a.getMessage(),
                    a.isResolved() ? "Yes" : "No");
        }
        autoSizeColumns(sheet, 8);
    }

    private static void createNetworkSheet(XSSFWorkbook wb, List<NetworkDataPoint> history) {
        XSSFSheet sheet = wb.createSheet("Network History");
        CellStyle headerStyle = createHeaderStyle(wb);
        int row = 0;
        addRow(sheet, row++, headerStyle, "Time", "Upload Speed (MB/s)");
        for (NetworkDataPoint pt : history) {
            addRow(sheet, row++, null, pt.getTimestampDisplay(),
                    String.format("%.2f", pt.getUploadSpeedMbps()));
        }
        autoSizeColumns(sheet, 2);
    }

    private static void addRow(Sheet sheet, int rowIdx, CellStyle style, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i] != null ? values[i] : "");
            if (style != null) cell.setCellStyle(style);
        }
    }

    private static CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(headerFont);
        return style;
    }

    private static void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignored) {}
        }
    }
}
