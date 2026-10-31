package com.shelfline.structure;

import com.shelfline.model.Batch;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class QueueTest {

    private Batch batch(String batchNo, int quantity) {
        return new Batch(batchNo, quantity, 10.0, LocalDate.of(2030, 1, 1));
    }

    @Test
    void firstInIsFirstOut() {
        Queue queue = new Queue();
        queue.enqueue(batch("B-1", 10));
        queue.enqueue(batch("B-2", 20));

        assertEquals("B-1", queue.peek().getBatchNo());
        assertEquals("B-1", queue.dequeue().getBatchNo());
        assertEquals("B-2", queue.peek().getBatchNo());
        assertEquals(1, queue.size());
    }

    @Test
    void emptyQueueReturnsNull() {
        Queue queue = new Queue();
        assertTrue(queue.isEmpty());
        assertNull(queue.peek());
        assertNull(queue.dequeue());
    }

    @Test
    void toListGoesFrontToRear() {
        Queue queue = new Queue();
        queue.enqueue(batch("B-1", 10));
        queue.enqueue(batch("B-2", 20));
        queue.enqueue(batch("B-3", 30));

        assertEquals("B-1", queue.toList().get(0).getBatchNo());
        assertEquals("B-3", queue.toList().get(2).getBatchNo());
    }

    @Test
    void findAndUpdateWorkByTraversal() {
        Queue queue = new Queue();
        queue.enqueue(batch("B-1", 10));
        queue.enqueue(batch("B-2", 20));

        assertNotNull(queue.findByBatchNo("b-2"));
        assertNull(queue.findByBatchNo("B-9"));

        queue.update("B-2", 55, null, null);
        assertEquals(55, queue.findByBatchNo("B-2").getQuantity());
        assertNull(queue.update("B-9", 1, null, null));
    }

    @Test
    void dequeuingLastItemResetsRear() {
        Queue queue = new Queue();
        queue.enqueue(batch("B-1", 10));
        queue.dequeue();
        queue.enqueue(batch("B-2", 20));

        assertEquals(1, queue.size());
        assertEquals("B-2", queue.peek().getBatchNo());
    }
}
