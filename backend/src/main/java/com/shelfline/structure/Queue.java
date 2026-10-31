package com.shelfline.structure;

import com.shelfline.model.Batch;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A FIFO queue written by hand with linked nodes.
 * front = oldest batch (sold first). rear = newest batch.
 * No java.util queue class is used.
 */
public class Queue {

    private Node front;
    private Node rear;
    private int size;

    // enqueue: O(1). Adds a new node at the rear. No loop.
    public void enqueue(Batch batch) {
        Node node = new Node(batch);
        if (rear == null) {
            front = node;
            rear = node;
        } else {
            rear.next = node;
            rear = node;
        }
        size++;
    }

    // dequeue: O(1). Removes and returns the front batch, or null if empty.
    public Batch dequeue() {
        if (front == null) {
            return null;
        }
        Batch removed = front.data;
        front = front.next;
        if (front == null) {
            rear = null;
        }
        size--;
        return removed;
    }

    // peek: O(1). Returns the front batch without removing it, or null if empty.
    public Batch peek() {
        return front == null ? null : front.data;
    }

    // isEmpty: O(1). Reads the size counter.
    public boolean isEmpty() {
        return size == 0;
    }

    // size: O(1). Returns the counter. No traversal.
    public int size() {
        return size;
    }

    // toList: O(n). Walks from front to rear and copies each batch into a list.
    public List<Batch> toList() {
        List<Batch> list = new ArrayList<>();
        Node current = front;
        while (current != null) {
            list.add(current.data);
            current = current.next;
        }
        return list;
    }

    // findByBatchNo: O(n). Walks from front to rear until the batch number matches.
    public Batch findByBatchNo(String batchNo) {
        Node current = front;
        while (current != null) {
            if (current.data.getBatchNo().equalsIgnoreCase(batchNo)) {
                return current.data;
            }
            current = current.next;
        }
        return null;
    }

    // update: O(n). Finds the batch by traversal, then changes only the values that are not null.
    public Batch update(String batchNo, Integer quantity, Double unitPrice, LocalDate expiryDate) {
        Batch batch = findByBatchNo(batchNo);
        if (batch == null) {
            return null;
        }
        if (quantity != null) {
            batch.setQuantity(quantity);
        }
        if (unitPrice != null) {
            batch.setUnitPrice(unitPrice);
        }
        if (expiryDate != null) {
            batch.setExpiryDate(expiryDate);
        }
        return batch;
    }
}
