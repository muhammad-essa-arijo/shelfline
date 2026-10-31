package com.shelfline.export;

import com.shelfline.dto.Dtos.BatchView;
import com.shelfline.dto.Dtos.MedicineView;
import com.shelfline.exception.ApiException;
import com.shelfline.service.ShelfLineService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Download endpoints for the PDF report and the Excel workbook. */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final String XLSX_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ShelfLineService service;

    public ExportController(ShelfLineService service) {
        this.service = service;
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) String medicine,
                                      @RequestParam(defaultValue = "false") boolean onlyAlerts) {
        List<MedicineView> stock = service.getAllStock();
        String scope = "whole stock";

        if (medicine != null && !medicine.isBlank()) {
            String wanted = medicine.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            List<MedicineView> match = new ArrayList<>();
            for (MedicineView view : stock) {
                if (view.key().toLowerCase(Locale.ROOT).equals(wanted)) {
                    match.add(view);
                }
            }
            if (match.isEmpty()) {
                throw ApiException.notFound("Medicine " + medicine.trim() + " was not found. Check the name and strength.");
            }
            stock = match;
            scope = match.get(0).key();
        }

        if (onlyAlerts) {
            stock = onlyBatchesThatNeedAttention(stock);
            scope = scope + ", only batches that need attention";
        }

        byte[] bytes = PdfExporter.build(stock, scope, LocalDateTime.now(ZoneId.of("Asia/Karachi")));
        return download(bytes, MediaType.APPLICATION_PDF_VALUE, "pdf");
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel() {
        byte[] bytes = ExcelExporter.build(service.getAllStock(), service.getSummary(),
                service.getAlerts(), service.getActivity(), LocalDateTime.now(ZoneId.of("Asia/Karachi")));
        return download(bytes, XLSX_TYPE, "xlsx");
    }

    /** Keeps expired and expiring batches, and every batch of a low-stock medicine. */
    private List<MedicineView> onlyBatchesThatNeedAttention(List<MedicineView> stock) {
        List<MedicineView> result = new ArrayList<>();
        for (MedicineView view : stock) {
            List<BatchView> kept = new ArrayList<>();
            for (BatchView batch : view.batches()) {
                if (view.lowStock() || !batch.status().equals("SAFE")) {
                    kept.add(batch);
                }
            }
            if (!kept.isEmpty()) {
                result.add(new MedicineView(view.key(), view.name(), view.strength(), view.totalUnits(),
                        view.totalValue(), view.lowStock(), view.lowStockThreshold(), view.sellsNext(), kept));
            }
        }
        return result;
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String contentType, String extension) {
        LocalDate today = service.today();
        String filename = "shelfline-stock-" + today + "." + extension;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
