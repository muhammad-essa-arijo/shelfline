package com.shelfline.model;

import com.shelfline.structure.Queue;

/** One medicine and its own queue of batches. */
public class MedicineStock {

    private final String name;
    private final String strength;
    private final Queue queue = new Queue();

    public MedicineStock(String name, String strength) {
        this.name = name;
        this.strength = strength;
    }

    public String getName() { return name; }
    public String getStrength() { return strength; }
    public Queue getQueue() { return queue; }

    /** Display name and lookup text, for example "Paracetamol 500 mg". */
    public String getKey() { return name + " " + strength; }
}
