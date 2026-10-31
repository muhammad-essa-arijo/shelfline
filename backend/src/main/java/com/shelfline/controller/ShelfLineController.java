package com.shelfline.controller;

import com.shelfline.dto.Dtos.*;
import com.shelfline.service.ShelfLineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Thin layer: reads the request, calls the service, returns JSON. */
@RestController
@RequestMapping("/api")
public class ShelfLineController {

    private final ShelfLineService service;

    public ShelfLineController(ShelfLineService service) {
        this.service = service;
    }

    @PostMapping("/batches")
    public ResponseEntity<ActionResult> addBatch(@RequestBody(required = false) AddBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addBatch(request));
    }

    @GetMapping("/stock")
    public List<MedicineView> getAllStock() {
        return service.getAllStock();
    }

    @GetMapping("/stock/{medicine}")
    public MedicineView getStock(@PathVariable String medicine) {
        return service.getStock(medicine);
    }

    @PutMapping("/batches/{medicine}/{batchNo}")
    public ActionResult updateBatch(@PathVariable String medicine, @PathVariable String batchNo,
                                    @RequestBody(required = false) UpdateBatchRequest request) {
        return service.updateBatch(medicine, batchNo, request);
    }

    @PostMapping("/sell/preview")
    public SellPlan previewSale(@RequestBody(required = false) SellRequest request) {
        return service.previewSale(request);
    }

    @PostMapping("/sell")
    public SellResult sell(@RequestBody(required = false) SellRequest request) {
        return service.sell(request);
    }

    @DeleteMapping("/batches/{medicine}/front")
    public ActionResult removeFrontBatch(@PathVariable String medicine) {
        return service.removeFrontBatch(medicine);
    }

    @PostMapping("/maintenance/remove-expired")
    public RemoveExpiredResult removeExpired() {
        return service.removeExpired();
    }

    @GetMapping("/alerts")
    public AlertsView getAlerts() {
        return service.getAlerts();
    }

    @GetMapping("/summary")
    public SummaryView getSummary() {
        return service.getSummary();
    }

    @GetMapping("/activity")
    public List<ActivityEntry> getActivity() {
        return service.getActivity();
    }
}
