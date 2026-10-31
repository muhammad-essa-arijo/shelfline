package com.shelfline.service;

import com.shelfline.dto.Dtos.*;
import com.shelfline.exception.ApiException;
import com.shelfline.model.Batch;
import com.shelfline.model.MedicineStock;
import com.shelfline.structure.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * All stock rules live here. Each medicine has its own hand-written Queue.
 * The Map only finds the right queue by medicine name.
 */
@Service
public class ShelfLineService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Karachi");
    private static final int EXPIRY_WARNING_DAYS = 30;
    private static final int LOG_LIMIT = 50;
    private static final DateTimeFormatter LOG_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    // lower-case medicine key -> that medicine and its queue
    private final Map<String, MedicineStock> stocks = new LinkedHashMap<>();

    // Newest entry first. A plain list, not a queue.
    private final List<ActivityEntry> activity = new ArrayList<>();

    private final int lowStockThreshold;

    public ShelfLineService(@Value("${shelfline.low-stock-threshold:50}") int lowStockThreshold) {
        this.lowStockThreshold = lowStockThreshold;
    }

    // =====================================================================
    // CREATE: enqueue
    // =====================================================================

    public synchronized ActionResult addBatch(AddBatchRequest request) {
        if (request == null) {
            throw ApiException.badRequest("The request body is empty. Send the batch details as JSON.");
        }
        String name = clean(request.medicineName());
        String strength = clean(request.strength());
        String batchNo = clean(request.batchNo());
        LocalDate today = today();

        if (name.isEmpty()) {
            throw ApiException.badRequest("Medicine name is required.");
        }
        if (strength.isEmpty()) {
            throw ApiException.badRequest("Strength is required. Example: 500 mg.");
        }
        if (batchNo.isEmpty()) {
            throw ApiException.badRequest("Batch number is required. Example: B-2041.");
        }
        validateQuantity(request.quantity());
        validatePrice(request.unitPrice());
        if (request.expiryDate() == null) {
            throw ApiException.badRequest("Expiry date is required.");
        }
        if (request.expiryDate().isBefore(today)) {
            throw ApiException.badRequest("Expiry date is in the past. Enter today or a later date.");
        }

        String key = name + " " + strength;
        MedicineStock stock = stocks.get(mapKey(key));
        if (stock == null) {
            stock = new MedicineStock(name, strength);
            stocks.put(mapKey(key), stock);
        } else if (stock.getQueue().findByBatchNo(batchNo) != null) {
            throw ApiException.conflict("Batch number " + batchNo
                    + " already exists for this medicine. Use a different number.");
        }

        double price = round2(request.unitPrice());
        stock.getQueue().enqueue(new Batch(batchNo, request.quantity(), price, request.expiryDate()));

        log("ADDED", stock.getKey(), batchNo,
                units(request.quantity()) + " at " + money(price) + ", expires " + date(request.expiryDate()));
        return new ActionResult("Batch " + batchNo + " added to the rear of " + stock.getKey() + ".",
                toMedicineView(stock, today));
    }

    // =====================================================================
    // READ: toList / peek
    // =====================================================================

    public synchronized List<MedicineView> getAllStock() {
        LocalDate today = today();
        List<MedicineView> views = new ArrayList<>();
        for (MedicineStock stock : stocks.values()) {
            views.add(toMedicineView(stock, today));
        }
        return views;
    }

    public synchronized MedicineView getStock(String medicine) {
        return toMedicineView(findStock(medicine), today());
    }

    // =====================================================================
    // UPDATE: traverse and edit
    // =====================================================================

    public synchronized ActionResult updateBatch(String medicine, String batchNo, UpdateBatchRequest request) {
        if (request == null
                || (request.quantity() == null && request.unitPrice() == null && request.expiryDate() == null)) {
            throw ApiException.badRequest("Send at least one of quantity, unitPrice or expiryDate.");
        }
        if (request.quantity() != null) {
            validateQuantity(request.quantity());
        }
        if (request.unitPrice() != null) {
            validatePrice(request.unitPrice());
        }

        MedicineStock stock = findStock(medicine);
        Double price = request.unitPrice() == null ? null : Double.valueOf(round2(request.unitPrice()));
        Batch batch = stock.getQueue().update(clean(batchNo), request.quantity(), price, request.expiryDate());
        if (batch == null) {
            throw ApiException.notFound("Batch " + clean(batchNo) + " was not found in " + stock.getKey() + ".");
        }

        log("UPDATED", stock.getKey(), batch.getBatchNo(),
                units(batch.getQuantity()) + " at " + money(batch.getUnitPrice())
                        + ", expires " + date(batch.getExpiryDate()));
        return new ActionResult("Batch " + batch.getBatchNo() + " of " + stock.getKey() + " updated.",
                toMedicineView(stock, today()));
    }

    // =====================================================================
    // SELL: consumes from the FRONT only
    // =====================================================================

    /** Shows which batches a sale would use. Nothing is changed. */
    public synchronized SellPlan previewSale(SellRequest request) {
        if (request == null) {
            throw ApiException.badRequest("The request body is empty. Send the medicine and quantity as JSON.");
        }
        return buildPlan(request.medicine(), request.quantity());
    }

    public synchronized SellResult sell(SellRequest request) {
        if (request == null) {
            throw ApiException.badRequest("The request body is empty. Send the medicine and quantity as JSON.");
        }
        // The plan checks every rule first, so the real sale below cannot fail half way.
        SellPlan plan = buildPlan(request.medicine(), request.quantity());
        MedicineStock stock = findStock(request.medicine());
        Queue queue = stock.getQueue();

        int remaining = plan.requestedQuantity();
        List<String> dequeued = new ArrayList<>();
        List<String> used = new ArrayList<>();

        while (remaining > 0) {
            Batch front = queue.peek();                       // always the oldest batch
            int take = Math.min(remaining, front.getQuantity());
            front.setQuantity(front.getQuantity() - take);
            remaining -= take;
            used.add(front.getBatchNo());
            if (front.getQuantity() == 0) {
                queue.dequeue();                               // empty batch leaves the front
                dequeued.add(front.getBatchNo());
            }
        }

        StringBuilder message = new StringBuilder("Sold " + units(plan.requestedQuantity())
                + " of " + stock.getKey() + ".");
        for (String batchNo : dequeued) {
            message.append(" Batch ").append(batchNo).append(" is empty and was removed from the front.");
        }

        log("SOLD", stock.getKey(), String.join(", ", used),
                "Sold " + units(plan.requestedQuantity()) + ", " + money(plan.totalValue()));
        return new SellResult(message.toString(), stock.getKey(), plan.requestedQuantity(),
                plan.lines(), dequeued, plan.remainingTotal(), plan.totalValue());
    }

    private SellPlan buildPlan(String medicine, Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw ApiException.badRequest("Quantity must be 1 or more.");
        }
        MedicineStock stock = findStock(medicine);
        long available = totalUnits(stock);
        if (available == 0) {
            throw ApiException.badRequest("No units of " + stock.getKey() + " are in stock. Add a batch first.");
        }
        if (quantity > available) {
            throw ApiException.badRequest("Only " + units(available) + " of " + stock.getKey()
                    + " are in stock. Enter " + available + " or less.");
        }

        LocalDate today = today();
        int remaining = quantity;
        double total = 0;
        List<SellLine> lines = new ArrayList<>();

        // Walk from the front. Never skip a batch to reach a later one.
        for (Batch batch : stock.getQueue().toList()) {
            if (remaining == 0) {
                break;
            }
            if (batch.getExpiryDate().isBefore(today)) {
                int soldSoFar = quantity - remaining;
                if (soldSoFar == 0) {
                    throw ApiException.badRequest("Batch " + batch.getBatchNo()
                            + " is expired and is at the front. Remove expired batches on the Alerts page, then sell again.");
                }
                throw ApiException.badRequest("Batch " + batch.getBatchNo()
                        + " has expired and cannot be sold. At most " + units(soldSoFar) + " can be sold now.");
            }
            int take = Math.min(remaining, batch.getQuantity());
            double lineValue = round2(take * batch.getUnitPrice());
            lines.add(new SellLine(batch.getBatchNo(), take, batch.getQuantity(),
                    take == batch.getQuantity(), batch.getUnitPrice(), lineValue, batch.getExpiryDate()));
            total += lineValue;
            remaining -= take;
        }
        return new SellPlan(stock.getKey(), quantity, lines, round2(total), available - quantity);
    }

    // =====================================================================
    // DELETE: dequeue
    // =====================================================================

    public synchronized ActionResult removeFrontBatch(String medicine) {
        MedicineStock stock = findStock(medicine);
        Batch removed = stock.getQueue().dequeue();
        if (removed == null) {
            throw ApiException.notFound(stock.getKey() + " has no batches to remove.");
        }
        log("REMOVED", stock.getKey(), removed.getBatchNo(),
                "Removed from the front with " + units(removed.getQuantity()) + " left");
        return new ActionResult("Batch " + removed.getBatchNo() + " removed from the front of "
                + stock.getKey() + ".", toMedicineView(stock, today()));
    }

    /** Dequeues expired batches that sit at the front. Expired batches behind a good one stay. */
    public synchronized RemoveExpiredResult removeExpired() {
        LocalDate today = today();
        List<RemovedItem> removed = new ArrayList<>();
        long wastageUnits = 0;
        double wastageValue = 0;

        for (MedicineStock stock : stocks.values()) {
            Queue queue = stock.getQueue();
            while (!queue.isEmpty() && queue.peek().getExpiryDate().isBefore(today)) {
                Batch batch = queue.dequeue();
                double value = round2(batch.getQuantity() * batch.getUnitPrice());
                removed.add(new RemovedItem(stock.getKey(), batch.getBatchNo(), batch.getQuantity(), value));
                wastageUnits += batch.getQuantity();
                wastageValue += value;
                log("EXPIRED", stock.getKey(), batch.getBatchNo(),
                        "Wastage: " + units(batch.getQuantity()) + ", " + money(value));
            }
        }

        wastageValue = round2(wastageValue);
        String message = removed.isEmpty()
                ? "No expired batches are at the front of any queue."
                : "Removed " + removed.size() + (removed.size() == 1 ? " expired batch" : " expired batches")
                + ". Wastage: " + units(wastageUnits) + ", " + money(wastageValue) + ".";
        return new RemoveExpiredResult(message, removed.size(), removed, wastageUnits, wastageValue);
    }

    // =====================================================================
    // ALERTS, SUMMARY, ACTIVITY
    // =====================================================================

    public synchronized AlertsView getAlerts() {
        LocalDate today = today();
        List<AlertItem> expired = new ArrayList<>();
        List<AlertItem> soon = new ArrayList<>();
        List<LowStockItem> low = new ArrayList<>();

        for (MedicineStock stock : stocks.values()) {
            for (Batch batch : stock.getQueue().toList()) {
                BatchView view = toBatchView(batch, today, false, false);
                AlertItem item = new AlertItem(stock.getKey(), batch.getBatchNo(), batch.getQuantity(),
                        batch.getExpiryDate(), view.daysToExpiry(), view.status(), view.statusLabel());
                if (view.status().equals("EXPIRED")) {
                    expired.add(item);
                } else if (view.status().equals("EXPIRES_SOON")) {
                    soon.add(item);
                }
            }
            long total = totalUnits(stock);
            if (total < lowStockThreshold) {
                low.add(new LowStockItem(stock.getKey(), total, lowStockThreshold));
            }
        }
        return new AlertsView(expired, soon, low);
    }

    public synchronized SummaryView getSummary() {
        LocalDate today = today();
        int batches = 0;
        int soon = 0;
        int low = 0;
        double value = 0;

        for (MedicineStock stock : stocks.values()) {
            for (Batch batch : stock.getQueue().toList()) {
                batches++;
                value += batch.getQuantity() * batch.getUnitPrice();
                long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
                if (days >= 0 && days <= EXPIRY_WARNING_DAYS) {
                    soon++;
                }
            }
            if (totalUnits(stock) < lowStockThreshold) {
                low++;
            }
        }
        return new SummaryView(stocks.size(), batches, soon, low, round2(value), lowStockThreshold);
    }

    public synchronized List<ActivityEntry> getActivity() {
        return new ArrayList<>(activity);
    }

    // =====================================================================
    // SEED DATA (called once at start, skips validation and the log)
    // =====================================================================

    public synchronized void loadSeed(String name, String strength, String batchNo,
                                      int quantity, double unitPrice, LocalDate expiryDate) {
        String key = name + " " + strength;
        MedicineStock stock = stocks.get(mapKey(key));
        if (stock == null) {
            stock = new MedicineStock(name, strength);
            stocks.put(mapKey(key), stock);
        }
        stock.getQueue().enqueue(new Batch(batchNo, quantity, unitPrice, expiryDate));
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private MedicineStock findStock(String medicine) {
        MedicineStock stock = stocks.get(mapKey(medicine));
        if (stock == null) {
            throw ApiException.notFound("Medicine " + clean(medicine) + " was not found. Check the name and strength.");
        }
        return stock;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1) {
            throw ApiException.badRequest("Quantity must be 1 or more.");
        }
    }

    private void validatePrice(Double price) {
        if (price == null || price.isNaN() || price <= 0) {
            throw ApiException.badRequest("Unit price must be above Rs. 0.00.");
        }
    }

    private MedicineView toMedicineView(MedicineStock stock, LocalDate today) {
        List<Batch> list = stock.getQueue().toList();
        List<BatchView> views = new ArrayList<>();
        double value = 0;
        long totalUnits = 0;

        for (int i = 0; i < list.size(); i++) {
            Batch batch = list.get(i);
            views.add(toBatchView(batch, today, i == 0, i == list.size() - 1));
            value += batch.getQuantity() * batch.getUnitPrice();
            totalUnits += batch.getQuantity();
        }
        Batch next = stock.getQueue().peek();
        return new MedicineView(stock.getKey(), stock.getName(), stock.getStrength(), totalUnits,
                round2(value), totalUnits < lowStockThreshold, lowStockThreshold,
                next == null ? null : next.getBatchNo(), views);
    }

    private BatchView toBatchView(Batch batch, LocalDate today, boolean front, boolean rear) {
        long days = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
        String status;
        String label;
        if (days < 0) {
            status = "EXPIRED";
            label = "Expired";
        } else if (days <= EXPIRY_WARNING_DAYS) {
            status = "EXPIRES_SOON";
            label = days == 0 ? "Expires today" : days == 1 ? "Expires in 1 day" : "Expires in " + days + " days";
        } else {
            status = "SAFE";
            label = "Safe";
        }
        return new BatchView(batch.getBatchNo(), batch.getQuantity(), batch.getUnitPrice(),
                batch.getExpiryDate(), days, status, label, front, rear,
                round2(batch.getQuantity() * batch.getUnitPrice()));
    }

    private long totalUnits(MedicineStock stock) {
        long total = 0;
        for (Batch batch : stock.getQueue().toList()) {
            total += batch.getQuantity();
        }
        return total;
    }

    private void log(String action, String medicine, String batchNo, String details) {
        activity.add(0, new ActivityEntry(LocalDateTime.now(ZONE), action, medicine, batchNo, details));
        while (activity.size() > LOG_LIMIT) {
            activity.remove(activity.size() - 1);
        }
    }

    /** Trims the text and turns many spaces into one. */
    private static String clean(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String mapKey(String medicine) {
        return clean(medicine).toLowerCase(Locale.ROOT);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String money(double value) {
        return String.format(Locale.US, "Rs. %,.2f", value);
    }

    private static String date(LocalDate value) {
        return value.format(LOG_DATE);
    }

    private static String units(long count) {
        return count == 1 ? "1 unit" : count + " units";
    }
}
