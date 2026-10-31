package com.shelfline.export;

import com.shelfline.dto.Dtos.ActivityEntry;
import com.shelfline.dto.Dtos.AlertItem;
import com.shelfline.dto.Dtos.AlertsView;
import com.shelfline.dto.Dtos.BatchView;
import com.shelfline.dto.Dtos.LowStockItem;
import com.shelfline.dto.Dtos.MedicineView;
import com.shelfline.dto.Dtos.SummaryView;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the Excel workbook with Apache POI. */
public final class ExcelExporter {

    private static final String MONEY = "\"Rs. \"#,##0.00";
    private static final String DATE = "dd mmm yyyy";
    private static final String DATE_TIME = "dd mmm yyyy hh:mm";

    private ExcelExporter() {
    }

    public static byte[] build(List<MedicineView> stock, SummaryView summary, AlertsView alerts,
                               List<ActivityEntry> activity, LocalDateTime generated) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            int lastStockRow = writeStock(wb, st, stock);
            writeSummary(wb, st, summary, alerts, generated, lastStockRow);
            writeAlerts(wb, st, alerts);
            writeActivity(wb, st, activity);

            wb.setForceFormulaRecalculation(true);
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel workbook.", e);
        }
    }

    // ---------- Sheet 1: Stock ----------

    /** Returns the 1-based Excel row number of the last data row (1 means no data rows). */
    private static int writeStock(XSSFWorkbook wb, Styles st, List<MedicineView> stock) {
        XSSFSheet sheet = wb.createSheet("Stock");
        String[] heads = {"Medicine", "Strength", "Queue position", "Batch no", "Quantity",
                "Unit price", "Value", "Expiry date", "Days to expiry", "Status"};
        header(sheet, st, heads);

        int r = 1;
        for (MedicineView medicine : stock) {
            int position = 1;
            for (BatchView batch : medicine.batches()) {
                Row row = sheet.createRow(r);
                int excelRow = r + 1;
                text(row, 0, medicine.name(), st.text);
                text(row, 1, medicine.strength(), st.text);
                number(row, 2, position, st.integer);
                text(row, 3, batch.batchNo(), st.text);
                number(row, 4, batch.quantity(), st.integer);
                number(row, 5, batch.unitPrice(), st.money);
                Cell value = row.createCell(6);
                value.setCellFormula("E" + excelRow + "*F" + excelRow);
                value.setCellStyle(st.money);
                Cell date = row.createCell(7);
                date.setCellValue(toDate(batch.expiryDate()));
                date.setCellStyle(st.date);
                number(row, 8, batch.daysToExpiry(), st.integer);
                text(row, 9, batch.statusLabel(), st.status(batch.status()));
                r++;
                position++;
            }
        }

        int lastExcelRow = r;   // r is the 0-based index after the last data row, which equals the last 1-based row
        Row total = sheet.createRow(r);
        text(total, 0, "Total", st.bold);
        if (lastExcelRow >= 2) {
            Cell qty = total.createCell(4);
            qty.setCellFormula("SUM(E2:E" + lastExcelRow + ")");
            qty.setCellStyle(st.boldInteger);
            Cell val = total.createCell(6);
            val.setCellFormula("SUM(G2:G" + lastExcelRow + ")");
            val.setCellStyle(st.boldMoney);
        } else {
            number(total, 4, 0, st.boldInteger);
            number(total, 6, 0, st.boldMoney);
        }

        sheet.createFreezePane(0, 1);
        if (lastExcelRow >= 2) {
            sheet.setAutoFilter(new CellRangeAddress(0, lastExcelRow - 1, 0, heads.length - 1));
        }
        autoWidth(sheet, heads.length);
        return lastExcelRow;
    }

    // ---------- Sheet 2: Summary ----------

    private static void writeSummary(XSSFWorkbook wb, Styles st, SummaryView summary, AlertsView alerts,
                                     LocalDateTime generated, int lastStockRow) {
        XSSFSheet sheet = wb.createSheet("Summary");
        header(sheet, st, new String[]{"Figure", "Value"});

        int r = 1;
        text(sheet.createRow(r), 0, "Report generated", st.text);
        Cell when = sheet.getRow(r).createCell(1);
        when.setCellValue(toDate(generated));
        when.setCellStyle(st.dateTime);
        r++;

        r = figure(sheet, st, r, "Total medicines", summary.totalMedicines());
        r = figure(sheet, st, r, "Total batches", summary.totalBatches());
        r = figure(sheet, st, r, "Expiring soon (30 days or fewer)", summary.expiringSoonCount());
        r = figure(sheet, st, r, "Expired batches", alerts.expired().size());
        r = figure(sheet, st, r, "Low-stock medicines", summary.lowStockCount());
        r = figure(sheet, st, r, "Low-stock threshold (units)", summary.lowStockThreshold());

        Row qtyRow = sheet.createRow(r);
        text(qtyRow, 0, "Total quantity (units)", st.bold);
        Cell qty = qtyRow.createCell(1);
        if (lastStockRow >= 2) {
            qty.setCellFormula("SUM(Stock!E2:E" + lastStockRow + ")");
        } else {
            qty.setCellValue(0);
        }
        qty.setCellStyle(st.boldInteger);
        r++;

        Row valueRow = sheet.createRow(r);
        text(valueRow, 0, "Total stock value", st.bold);
        Cell value = valueRow.createCell(1);
        if (lastStockRow >= 2) {
            value.setCellFormula("SUM(Stock!G2:G" + lastStockRow + ")");
        } else {
            value.setCellValue(0);
        }
        value.setCellStyle(st.boldMoney);

        sheet.createFreezePane(0, 1);
        autoWidth(sheet, 2);
    }

    private static int figure(XSSFSheet sheet, Styles st, int r, String label, double value) {
        Row row = sheet.createRow(r);
        text(row, 0, label, st.text);
        number(row, 1, value, st.integer);
        return r + 1;
    }

    // ---------- Sheet 3: Expiry Alerts ----------

    private static void writeAlerts(XSSFWorkbook wb, Styles st, AlertsView alerts) {
        XSSFSheet sheet = wb.createSheet("Expiry Alerts");
        String[] heads = {"Alert", "Medicine", "Batch no", "Quantity", "Expiry date", "Note"};
        header(sheet, st, heads);

        int r = 1;
        for (AlertItem item : alerts.expired()) {
            r = alertRow(sheet, st, r, "Expired", "EXPIRED", item);
        }
        for (AlertItem item : alerts.expiringSoon()) {
            r = alertRow(sheet, st, r, "Expires soon", "EXPIRES_SOON", item);
        }
        for (LowStockItem low : alerts.lowStock()) {
            Row row = sheet.createRow(r);
            text(row, 0, "Low stock", st.status("LOW"));
            text(row, 1, low.medicine(), st.text);
            text(row, 2, "-", st.text);
            number(row, 3, low.totalUnits(), st.integer);
            text(row, 4, "-", st.text);
            text(row, 5, "Below " + low.threshold() + " units", st.text);
            r++;
        }

        sheet.createFreezePane(0, 1);
        if (r > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, r - 1, 0, heads.length - 1));
        }
        autoWidth(sheet, heads.length);
    }

    private static int alertRow(XSSFSheet sheet, Styles st, int r, String type, String status, AlertItem item) {
        Row row = sheet.createRow(r);
        text(row, 0, type, st.status(status));
        text(row, 1, item.medicine(), st.text);
        text(row, 2, item.batchNo(), st.text);
        number(row, 3, item.quantity(), st.integer);
        Cell date = row.createCell(4);
        date.setCellValue(toDate(item.expiryDate()));
        date.setCellStyle(st.date);
        text(row, 5, item.statusLabel(), st.text);
        return r + 1;
    }

    // ---------- Sheet 4: Activity Log ----------

    private static void writeActivity(XSSFWorkbook wb, Styles st, List<ActivityEntry> activity) {
        XSSFSheet sheet = wb.createSheet("Activity Log");
        String[] heads = {"Time", "Action", "Medicine", "Batch no", "Details"};
        header(sheet, st, heads);

        int r = 1;
        for (ActivityEntry entry : activity) {
            Row row = sheet.createRow(r);
            Cell time = row.createCell(0);
            time.setCellValue(toDate(entry.time()));
            time.setCellStyle(st.dateTime);
            text(row, 1, entry.action(), st.text);
            text(row, 2, entry.medicine(), st.text);
            text(row, 3, entry.batchNo(), st.text);
            text(row, 4, entry.details(), st.text);
            r++;
        }

        sheet.createFreezePane(0, 1);
        if (r > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, r - 1, 0, heads.length - 1));
        }
        autoWidth(sheet, heads.length);
    }

    // ---------- Cell helpers ----------

    private static void header(XSSFSheet sheet, Styles st, String[] heads) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(22);
        for (int i = 0; i < heads.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(heads[i]);
            cell.setCellStyle(st.header);
        }
    }

    private static void text(Row row, int col, String value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int col, double value, XSSFCellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Sets each column width from the longest text in it.
     * POI's own autoSizeColumn needs system fonts, which a small Docker image does not have.
     */
    private static void autoWidth(XSSFSheet sheet, int columns) {
        for (int col = 0; col < columns; col++) {
            int max = 8;
            for (Row row : sheet) {
                Cell cell = row.getCell(col);
                if (cell == null) {
                    continue;
                }
                int length = 0;
                if (cell.getCellType() == CellType.STRING) {
                    length = cell.getStringCellValue().length();
                } else if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
                    length = 14;
                }
                max = Math.max(max, length);
            }
            sheet.setColumnWidth(col, Math.min(max + 3, 60) * 256);
        }
    }

    private static XSSFColor color(int rgb) {
        return new XSSFColor(new byte[]{(byte) ((rgb >> 16) & 0xFF), (byte) ((rgb >> 8) & 0xFF), (byte) (rgb & 0xFF)}, null);
    }

    /** All cell styles, created once per workbook. */
    private static final class Styles {
        final XSSFCellStyle header;
        final XSSFCellStyle text;
        final XSSFCellStyle bold;
        final XSSFCellStyle integer;
        final XSSFCellStyle boldInteger;
        final XSSFCellStyle money;
        final XSSFCellStyle boldMoney;
        final XSSFCellStyle date;
        final XSSFCellStyle dateTime;
        private final Map<String, XSSFCellStyle> statusStyles = new HashMap<>();

        Styles(XSSFWorkbook wb) {
            header = make(wb, Palette.TEAL, Palette.WHITE, true, null, -1, HorizontalAlignment.LEFT);
            text = make(wb, -1, Palette.BODY, false, null, -1, HorizontalAlignment.LEFT);
            bold = make(wb, -1, Palette.STRONG, true, null, -1, HorizontalAlignment.LEFT);
            integer = make(wb, -1, Palette.BODY, false, "#,##0", -1, HorizontalAlignment.RIGHT);
            boldInteger = make(wb, -1, Palette.STRONG, true, "#,##0", -1, HorizontalAlignment.RIGHT);
            money = make(wb, -1, Palette.BODY, false, MONEY, -1, HorizontalAlignment.RIGHT);
            boldMoney = make(wb, -1, Palette.STRONG, true, MONEY, -1, HorizontalAlignment.RIGHT);
            date = make(wb, -1, Palette.BODY, false, DATE, -1, HorizontalAlignment.LEFT);
            dateTime = make(wb, -1, Palette.BODY, false, DATE_TIME, -1, HorizontalAlignment.LEFT);
            for (String status : new String[]{"SAFE", "EXPIRES_SOON", "EXPIRED", "LOW"}) {
                Palette.StatusColors c = Palette.forStatus(status);
                statusStyles.put(status, make(wb, c.bg(), c.text(), true, null, c.border(), HorizontalAlignment.LEFT));
            }
        }

        XSSFCellStyle status(String status) {
            return statusStyles.get(status);
        }

        private static XSSFCellStyle make(XSSFWorkbook wb, int fill, int fontRgb, boolean bold, String format,
                                          int border, HorizontalAlignment align) {
            XSSFCellStyle style = wb.createCellStyle();
            XSSFFont font = wb.createFont();
            font.setBold(bold);
            font.setColor(color(fontRgb));
            style.setFont(font);
            if (fill >= 0) {
                style.setFillForegroundColor(color(fill));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (format != null) {
                style.setDataFormat(wb.createDataFormat().getFormat(format));
            }
            if (border >= 0) {
                style.setBorderTop(BorderStyle.THIN);
                style.setBorderBottom(BorderStyle.THIN);
                style.setBorderLeft(BorderStyle.THIN);
                style.setBorderRight(BorderStyle.THIN);
                style.setTopBorderColor(color(border));
                style.setBottomBorderColor(color(border));
                style.setLeftBorderColor(color(border));
                style.setRightBorderColor(color(border));
            }
            style.setAlignment(align);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }
    }
}
