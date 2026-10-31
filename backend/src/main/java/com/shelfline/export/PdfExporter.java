package com.shelfline.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.shelfline.dto.Dtos.BatchView;
import com.shelfline.dto.Dtos.MedicineView;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Builds the A4 stock report with OpenPDF. */
public final class PdfExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final String FOOTER_TEXT = "ShelfLine, K-25SW-040, SW 212 Data Structures & Algorithms, Page ";

    private PdfExporter() {
    }

    public static byte[] build(List<MedicineView> stock, String scope, LocalDateTime generated) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new FooterEvent());
            doc.open();

            doc.add(headerBand(generated));

            Paragraph scopeLine = new Paragraph("Scope: " + scope, font(9, Font.NORMAL, Palette.BODY));
            scopeLine.setSpacingAfter(10);
            doc.add(scopeLine);

            doc.add(summaryTiles(stock));

            if (stock.isEmpty()) {
                doc.add(new Paragraph("No batches match this report.", font(11, Font.NORMAL, Palette.BODY)));
            } else {
                doc.add(sectionTitle("Stock by batch"));
                doc.add(stockTable(stock));
                doc.add(sectionTitle("Queues, front to rear"));
                for (MedicineView medicine : stock) {
                    doc.add(diagram(medicine));
                }
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Could not build the PDF report.", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    // ---------- Header and tiles ----------

    private static PdfPTable headerBand(LocalDateTime generated) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 1f});
        table.setSpacingAfter(10);

        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(new Color(Palette.TEAL));
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(14);
        left.addElement(new Paragraph("ShelfLine", font(22, Font.BOLD, Palette.WHITE)));
        left.addElement(new Paragraph("Oldest stock out first.", font(10, Font.NORMAL, Palette.WHITE)));

        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(new Color(Palette.TEAL));
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(14);
        Paragraph title = new Paragraph("Stock report", font(12, Font.BOLD, Palette.WHITE));
        title.setAlignment(Element.ALIGN_RIGHT);
        Paragraph when = new Paragraph("Generated " + generated.format(DATE_TIME), font(10, Font.NORMAL, Palette.WHITE));
        when.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        right.addElement(when);

        table.addCell(left);
        table.addCell(right);
        return table;
    }

    private static PdfPTable summaryTiles(List<MedicineView> stock) throws DocumentException {
        int batches = 0;
        int soon = 0;
        double value = 0;
        for (MedicineView medicine : stock) {
            for (BatchView batch : medicine.batches()) {
                batches++;
                value += batch.value();
                if (batch.status().equals("EXPIRES_SOON")) {
                    soon++;
                }
            }
        }

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 0.06f, 1f, 0.06f, 1f, 0.06f, 1.3f});
        table.setSpacingAfter(14);

        table.addCell(tile("Medicines", String.valueOf(stock.size())));
        table.addCell(gap());
        table.addCell(tile("Batches", String.valueOf(batches)));
        table.addCell(gap());
        table.addCell(tile("Expiring soon", String.valueOf(soon)));
        table.addCell(gap());
        table.addCell(tile("Stock value", money(value)));
        return table;
    }

    private static PdfPCell tile(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(new Color(Palette.BORDER));
        cell.setBorderWidth(1f);
        cell.setPadding(10);
        cell.addElement(new Paragraph(label, font(8, Font.NORMAL, Palette.MUTED)));
        cell.addElement(new Paragraph(value, font(15, Font.BOLD, Palette.STRONG)));
        return cell;
    }

    private static PdfPCell gap() {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static Paragraph sectionTitle(String text) {
        Paragraph p = new Paragraph(text, font(12, Font.BOLD, Palette.STRONG));
        p.setSpacingBefore(6);
        p.setSpacingAfter(6);
        return p;
    }

    // ---------- Stock table ----------

    private static PdfPTable stockTable(List<MedicineView> stock) throws DocumentException {
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{26f, 11f, 9f, 13f, 15f, 13f, 17f});
        table.setSpacingAfter(12);
        table.setHeaderRows(1);   // header row repeats on every page

        String[] heads = {"Medicine", "Batch", "Qty", "Unit price", "Value", "Expiry", "Status"};
        for (String head : heads) {
            PdfPCell cell = new PdfPCell(new Phrase(head, font(9, Font.BOLD, Palette.WHITE)));
            cell.setBackgroundColor(new Color(Palette.TEAL));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(6);
            table.addCell(cell);
        }

        int row = 0;
        for (MedicineView medicine : stock) {
            if (medicine.batches().isEmpty()) {
                PdfPCell empty = bodyCell(medicine.key() + ": no batches in this queue.", Element.ALIGN_LEFT, row);
                empty.setColspan(7);
                table.addCell(empty);
                row++;
                continue;
            }
            boolean first = true;
            for (BatchView batch : medicine.batches()) {
                String name = medicine.key();
                if (first && medicine.lowStock()) {
                    name = name + "\nLow stock: " + medicine.totalUnits() + " units";
                }
                table.addCell(bodyCell(name, Element.ALIGN_LEFT, row));
                table.addCell(bodyCell(batch.batchNo(), Element.ALIGN_LEFT, row));
                table.addCell(bodyCell(String.valueOf(batch.quantity()), Element.ALIGN_RIGHT, row));
                table.addCell(bodyCell(money(batch.unitPrice()), Element.ALIGN_RIGHT, row));
                table.addCell(bodyCell(money(batch.value()), Element.ALIGN_RIGHT, row));
                table.addCell(bodyCell(batch.expiryDate().format(DATE), Element.ALIGN_LEFT, row));
                table.addCell(statusCell(batch));
                first = false;
                row++;
            }
        }
        return table;
    }

    private static PdfPCell bodyCell(String text, int align, int row) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(8.5f, Font.NORMAL, Palette.BODY)));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(Palette.BORDER));
        cell.setBorderWidth(0.5f);
        if (row % 2 == 1) {
            cell.setBackgroundColor(new Color(Palette.TINT));
        }
        return cell;
    }

    private static PdfPCell statusCell(BatchView batch) {
        Palette.StatusColors colors = Palette.forStatus(batch.status());
        PdfPCell cell = new PdfPCell(new Phrase(batch.statusLabel(), font(8, Font.BOLD, colors.text())));
        cell.setBackgroundColor(new Color(colors.bg()));
        cell.setBorderColor(new Color(colors.border()));
        cell.setBorderWidth(0.8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }

    // ---------- FRONT to REAR box diagram ----------

    private static PdfPTable diagram(MedicineView medicine) throws DocumentException {
        final int perRow = 5;
        PdfPTable table = new PdfPTable(9);   // 5 boxes and 4 arrows
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20f, 4f, 20f, 4f, 20f, 4f, 20f, 4f, 20f});
        table.setKeepTogether(true);
        table.setSpacingAfter(10);

        String title = medicine.key() + "   " + medicine.totalUnits() + " units"
                + (medicine.lowStock() ? "   Low stock" : "");
        PdfPCell titleCell = new PdfPCell(new Phrase(title, font(10, Font.BOLD, Palette.STRONG)));
        titleCell.setColspan(9);
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingBottom(4);
        table.addCell(titleCell);

        List<BatchView> batches = medicine.batches();
        if (batches.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No batches in this queue.", font(9, Font.NORMAL, Palette.BODY)));
            empty.setColspan(9);
            empty.setBorder(Rectangle.NO_BORDER);
            table.addCell(empty);
            return table;
        }

        for (int start = 0; start < batches.size(); start += perRow) {
            for (int i = 0; i < perRow; i++) {
                int index = start + i;
                if (index < batches.size()) {
                    if (i > 0) {
                        table.addCell(arrowCell());
                    }
                    table.addCell(boxCell(batches.get(index)));
                } else {
                    if (i > 0) {
                        table.addCell(blankCell());
                    }
                    table.addCell(blankCell());
                }
            }
            if (start + perRow < batches.size()) {
                PdfPCell space = new PdfPCell(new Phrase(" ", font(4, Font.NORMAL, Palette.BODY)));
                space.setColspan(9);
                space.setBorder(Rectangle.NO_BORDER);
                space.setFixedHeight(6);
                table.addCell(space);
            }
        }
        return table;
    }

    private static PdfPCell boxCell(BatchView batch) {
        Palette.StatusColors colors = Palette.forStatus(batch.status());
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(new Color(colors.bg()));
        cell.setBorderColor(new Color(batch.front() ? Palette.TEAL : colors.border()));
        cell.setBorderWidth(batch.front() ? 1.6f : 0.8f);
        cell.setPadding(5);

        String label = " ";
        if (batch.front() && batch.rear()) {
            label = "FRONT and REAR";
        } else if (batch.front()) {
            label = "FRONT, sells next";
        } else if (batch.rear()) {
            label = "REAR";
        }
        cell.addElement(new Paragraph(label, font(6.5f, Font.BOLD, Palette.TEAL)));
        cell.addElement(new Paragraph(batch.batchNo(), font(9, Font.BOLD, Palette.STRONG)));
        cell.addElement(new Paragraph(batch.quantity() + " units", font(8, Font.NORMAL, Palette.BODY)));
        cell.addElement(new Paragraph(batch.expiryDate().format(DATE), font(8, Font.NORMAL, Palette.BODY)));
        cell.addElement(new Paragraph(batch.statusLabel(), font(7, Font.BOLD, colors.text())));
        return cell;
    }

    private static PdfPCell arrowCell() {
        PdfPCell cell = new PdfPCell(new Phrase(">", font(12, Font.BOLD, Palette.ARROW)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static PdfPCell blankCell() {
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    // ---------- Helpers ----------

    private static Font font(float size, int style, int rgb) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, new Color(rgb));
    }

    private static String money(double value) {
        return String.format(Locale.US, "Rs. %,.2f", value);
    }

    /** Writes "..., Page X of Y" on every page. Y is filled in when the document closes. */
    private static final class FooterEvent extends PdfPageEventHelper {

        private PdfTemplate total;
        private BaseFont base;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("Could not load the PDF footer font.", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            String text = FOOTER_TEXT + writer.getPageNumber() + " of ";
            float y = 24;
            canvas.beginText();
            canvas.setFontAndSize(base, 8);
            canvas.setColorFill(new Color(Palette.MUTED));
            canvas.setTextMatrix(document.left(), y);
            canvas.showText(text);
            canvas.endText();
            canvas.addTemplate(total, document.left() + base.getWidthPoint(text, 8), y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, 8);
            total.setColorFill(new Color(Palette.MUTED));
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }
}
