package com.shelfline.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Small data holders for request and response JSON. */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Requests ----------

    public record AddBatchRequest(String medicineName, String strength, String batchNo,
                                  Integer quantity, Double unitPrice, LocalDate expiryDate) {
    }

    public record UpdateBatchRequest(Integer quantity, Double unitPrice, LocalDate expiryDate) {
    }

    public record SellRequest(String medicine, Integer quantity) {
    }

    // ---------- Stock views ----------

    /** status is SAFE, EXPIRES_SOON or EXPIRED. front and rear mark the position in the queue. */
    public record BatchView(String batchNo, int quantity, double unitPrice, LocalDate expiryDate,
                            long daysToExpiry, String status, String statusLabel,
                            boolean front, boolean rear, double value) {
    }

    public record MedicineView(String key, String name, String strength, long totalUnits,
                               double totalValue, boolean lowStock, int lowStockThreshold,
                               String sellsNext, List<BatchView> batches) {
    }

    public record ActionResult(String message, MedicineView medicine) {
    }

    // ---------- Selling ----------

    public record SellLine(String batchNo, int unitsTaken, int batchQuantityBefore,
                           boolean willBeEmpty, double unitPrice, double lineValue,
                           LocalDate expiryDate) {
    }

    public record SellPlan(String medicine, int requestedQuantity, List<SellLine> lines,
                           double totalValue, long remainingTotal) {
    }

    public record SellResult(String message, String medicine, int quantitySold,
                             List<SellLine> batchesUsed, List<String> batchesDequeued,
                             long remainingTotal, double totalValue) {
    }

    // ---------- Maintenance and alerts ----------

    public record RemovedItem(String medicine, String batchNo, int quantity, double value) {
    }

    public record RemoveExpiredResult(String message, int removedCount, List<RemovedItem> removed,
                                      long wastageUnits, double wastageValue) {
    }

    public record AlertItem(String medicine, String batchNo, int quantity, LocalDate expiryDate,
                            long daysToExpiry, String status, String statusLabel) {
    }

    public record LowStockItem(String medicine, long totalUnits, int threshold) {
    }

    public record AlertsView(List<AlertItem> expired, List<AlertItem> expiringSoon,
                             List<LowStockItem> lowStock) {
    }

    // ---------- Dashboard and log ----------

    public record SummaryView(int totalMedicines, int totalBatches, int expiringSoonCount,
                              int lowStockCount, double totalStockValue, int lowStockThreshold) {
    }

    public record ActivityEntry(LocalDateTime time, String action, String medicine,
                                String batchNo, String details) {
    }
}
