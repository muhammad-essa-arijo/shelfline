package com.shelfline.structure;

import com.shelfline.model.Batch;

/** One link in the queue: the batch it holds and the node behind it. */
class Node {

    Batch data;
    Node next;

    Node(Batch data) {
        this.data = data;
        this.next = null;
    }
}
