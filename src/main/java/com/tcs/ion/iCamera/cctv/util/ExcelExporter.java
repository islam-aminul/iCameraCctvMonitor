package com.tcs.ion.iCamera.cctv.util;

import com.tcs.ion.iCamera.cctv.model.*;
import com.tcs.ion.iCamera.cctv.service.DataStore;
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
 * Supports both directory-based export (legacy) and file-based export with sheet protection.
 */
public class ExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelExporter.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Legacy export: writes to the given output directory with auto-generated filename.
     */
    public static String exportAll(DataStore store, String outputDir) throws IOException {
        new File(outputDir).mkdirs();
        String filename = outputDir + File.separator + "iCamera_Report_" + LocalDateTime.now().format(DTF) + ".xlsx";
        File outputFile = new File(filename);
        exportToFile(store, outputFile);
        return filename;
    }

    /**
     * Exports all data to the specified file with protected/read-only sheets and locked workbook structure.
     */
    public static void exportToFile(DataStore store, File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle greenStyle = createStatusStyle(wb, IndexedColors.GREEN);
            CellStyle redStyle = createStatusStyle(wb, IndexedColors.RED);
            CellStyle amberStyle = createStatusStyle(wb, IndexedColors.GOLD);

            XSSFSheet proxySheet = createProxySheet(wb, headerStyle, store.getProxyData(), store.getSystemMetrics());
            XSSFSheet cctvSheet = createCctvSheet(wb, headerStyle, store.getAllCctv());
            XSSFSheet alertsSheet = createAlertsSheet(wb, headerStyle, greenStyle, redStyle, amberStyle, store.getAlerts());
            XSSFSheet networkSheet = createNetworkSheet(wb, headerStyle, store.getNetworkHistory());

            // Protect all sheets (read-only)
            String sheetPassword = "iCamera2026";
            proxySheet.protectSheet(sheetPassword);
            cctvSheet.protectSheet(sheetPassword);
            alertsSheet.protectSheet(sheetPassword);
            networkSheet.protectSheet(sheetPassword);

            // Lock workbook structure (prevent adding/removing/renaming sheets)
            wb.lockStructure();

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                wb.write(fos);
            }
        }
        log.info("Exported report to {}", outputFile.getAbsolutePath());
    }

    private static XSSFSheet createProxySheet(XSSFWorkbook wb, CellStyle headerStyle,
                                               ProxyData pd, SystemMetrics sm) {
        XSSFSheet sheet = wb.createSheet("Proxy & System");
        int row = 0;

        addRow(sheet, row++, headerStyle, "Parameter", "Value");
        if (pd != null) {
            addRow(sheet, row++, null, "Proxy ID", String.valueOf(pd.getProxyId()));
            addRow(sheet, row++, null, "Proxy Name", pd.getProxyName());
            addRow(sheet, row++, null, "TC Code", pd.getTcCode());
            addRow(sheet, row++, null, "Status", pd.getStatus());
            addRow(sheet, row++, null, "Service Status", pd.getServiceStatus());
            addRow(sheet, row++, null, "Uptime", pd.getUptimeString());
            addRow(sheet, row++, null, "Process CPU %", String.format("%.2f", pd.getProcessCpuPercent()));
            addRow(sheet, row++, null, "Process Memory MB", String.format("%.2f", pd.getProcessMemoryMb()));
            addRow(sheet, row++, null, "Current MAC", pd.getCurrentMacAddress());
            addRow(sheet, row++, null, "Last MAC", pd.getLastMacAddress());
            addRow(sheet, row++, null, "MAC Mismatch", pd.isMacMismatch() ? "Yes" : "No");
            addRow(sheet, row++, null, "HSQLDB Service Status", pd.getHsqldbStatus());
            addRow(sheet, row++, null, "HSQLDB JMX Status", pd.getHsqldbJmxStatus() != null ? pd.getHsqldbJmxStatus() : "N/A");
            addRow(sheet, row++, null, "HSQLDB Directly Reachable", pd.isHsqldbDirectlyReachable() ? "Yes" : "No");
        }
        if (sm != null) {
            row++;
            addRow(sheet, row++, headerStyle, "System Metric", "Value");
            addRow(sheet, row++, null, "CPU Name", sm.getCpuName());
            addRow(sheet, row++, null, "Physical Cores", String.valueOf(sm.getPhysicalCores()));
            addRow(sheet, row++, null, "Logical Cores", String.valueOf(sm.getLogicalCores()));
            addRow(sheet, row++, null, "System CPU %", String.format("%.2f", sm.getSystemCpuPercent()));
            addRow(sheet, row++, null, "Total RAM MB", String.format("%.2f", sm.getTotalMemoryMb()));
            addRow(sheet, row++, null, "Free RAM MB", String.format("%.2f", sm.getFreeMemoryMb()));
            addRow(sheet, row++, null, "Memory Used %", String.format("%.1f", sm.getMemoryUsedPercent()));
            addRow(sheet, row++, null, "Network Speed MB/s", String.format("%.2f", sm.getNetworkSpeedMbps()));
            addRow(sheet, row++, null, "System Health", sm.isHealthy() ? "STABLE" : "UNSTABLE");
            for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                addRow(sheet, row++, null, "Drive " + di.getName(),
                        "Total=" + di.getTotalSpaceMb() + "MB Free=" + di.getFreeSpaceMb() + "MB Used=" +
                                String.format("%.1f%%", di.getUsedPercent()));
            }
        }
        autoSizeColumns(sheet, 2);
        return sheet;
    }

    private static XSSFSheet createCctvSheet(XSSFWorkbook wb, CellStyle headerStyle,
                                              Collection<CctvData> cctvList) {
        XSSFSheet sheet = wb.createSheet("CCTV Details");
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
        return sheet;
    }

    private static XSSFSheet createAlertsSheet(XSSFWorkbook wb, CellStyle headerStyle,
                                                CellStyle greenStyle, CellStyle redStyle,
                                                CellStyle amberStyle, List<AlertData> alerts) {
        XSSFSheet sheet = wb.createSheet("Alerts");
        int row = 0;
        addRow(sheet, row++, headerStyle, "ID", "Timestamp", "Severity", "Category",
                "Source", "Parameter", "Message", "Resolution", "Resolved");
        for (AlertData a : alerts) {
            IssueResolutionProvider.Resolution res = IssueResolutionProvider.getResolution(a.getParameter());
            String resolutionText = res.getRootCause();

            Row dataRow = sheet.createRow(row++);
            String[] values = {
                    a.getId().substring(0, 8),
                    a.getTimestampDisplay(),
                    a.getSeverity().name(),
                    a.getCategory().name(),
                    a.getSource(),
                    a.getParameter(),
                    a.getMessage(),
                    resolutionText,
                    a.isResolved() ? "Yes" : "No"
            };

            // Choose severity style for the row
            CellStyle rowStyle = null;
            if (!a.isResolved()) {
                if (a.getSeverity() == AlertData.Severity.CRITICAL) rowStyle = redStyle;
                else if (a.getSeverity() == AlertData.Severity.WARNING) rowStyle = amberStyle;
            }

            for (int i = 0; i < values.length; i++) {
                Cell cell = dataRow.createCell(i);
                cell.setCellValue(values[i] != null ? values[i] : "");
                if (rowStyle != null && (i == 2)) cell.setCellStyle(rowStyle); // color the severity cell
            }
        }
        autoSizeColumns(sheet, 9);
        return sheet;
    }

    private static XSSFSheet createNetworkSheet(XSSFWorkbook wb, CellStyle headerStyle,
                                                 List<NetworkDataPoint> history) {
        XSSFSheet sheet = wb.createSheet("Network");
        int row = 0;
        addRow(sheet, row++, headerStyle, "Time", "Upload Speed (MB/s)");
        for (NetworkDataPoint pt : history) {
            addRow(sheet, row++, null, pt.getTimestampDisplay(),
                    String.format("%.2f", pt.getUploadSpeedMbps()));
        }
        autoSizeColumns(sheet, 2);
        return sheet;
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
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(headerFont);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createStatusStyle(XSSFWorkbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(color.getIndex());
        style.setFont(font);
        return style;
    }

    /**
     * Exports only alerts to the specified file with sheet protection.
     */
    public static void exportAlertsToFile(List<AlertData> alerts, File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle greenStyle = createStatusStyle(wb, IndexedColors.GREEN);
            CellStyle redStyle = createStatusStyle(wb, IndexedColors.RED);
            CellStyle amberStyle = createStatusStyle(wb, IndexedColors.GOLD);

            XSSFSheet sheet = createAlertsSheet(wb, headerStyle, greenStyle, redStyle, amberStyle, alerts);

            sheet.protectSheet("iCamera2026");
            wb.lockStructure();

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                wb.write(fos);
            }
        }
        log.info("Exported alerts to {}", outputFile.getAbsolutePath());
    }

    private static void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignored) {}
        }
    }
}
