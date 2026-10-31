package com.shelfline.model;

import java.time.LocalDate;

/** One delivery of a medicine. This is the data stored inside a queue Node. */
public class Batch {

    private final String batchNo;
    private int quantity;
    private double unitPrice;
    private LocalDate expiryDate;

    public Batch(String batchNo, int quantity, double unitPrice, LocalDate expiryDate) {
        this.batchNo = batchNo;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.expiryDate = expiryDate;
    }

    public String getBatchNo() { return batchNo; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public LocalDate getExpiryDate() { return expiryDate; }

    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
}
