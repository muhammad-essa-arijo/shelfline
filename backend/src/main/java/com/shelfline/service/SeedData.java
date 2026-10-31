package com.shelfline.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Starting stock. Dates are set from today, so the expired and expiring-soon
 * batches stay correct whenever the server starts.
 */
@Component
public class SeedData implements ApplicationRunner {

    private final ShelfLineService service;

    public SeedData(ShelfLineService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate t = service.today();

        // Paracetamol: front batch expires in 25 days
        service.loadSeed("Paracetamol", "500 mg", "B-1987", 120, 2.50, t.plusDays(25));
        service.loadSeed("Paracetamol", "500 mg", "B-2041", 300, 2.75, t.plusDays(210));
        service.loadSeed("Paracetamol", "500 mg", "B-2166", 500, 2.75, t.plusDays(540));

        service.loadSeed("Amoxicillin", "500 mg", "B-2203", 200, 18.00, t.plusDays(90));
        service.loadSeed("Amoxicillin", "500 mg", "B-2310", 350, 18.50, t.plusDays(300));

        // Metformin: front batch expires in 18 days
        service.loadSeed("Metformin", "500 mg", "B-1850", 150, 6.00, t.plusDays(18));
        service.loadSeed("Metformin", "500 mg", "B-2074", 400, 6.25, t.plusDays(260));
        service.loadSeed("Metformin", "500 mg", "B-2288", 600, 6.50, t.plusDays(480));

        service.loadSeed("Omeprazole", "20 mg", "B-2119", 180, 14.00, t.plusDays(150));
        service.loadSeed("Omeprazole", "20 mg", "B-2251", 240, 14.50, t.plusDays(330));

        // Cetirizine: 45 units in total, below the threshold of 50
        service.loadSeed("Cetirizine", "10 mg", "B-2092", 30, 7.00, t.plusDays(120));
        service.loadSeed("Cetirizine", "10 mg", "B-2180", 15, 7.00, t.plusDays(240));

        // Ibuprofen: front batch is expired
        service.loadSeed("Ibuprofen", "400 mg", "B-1764", 60, 8.00, t.minusDays(9));
        service.loadSeed("Ibuprofen", "400 mg", "B-2005", 220, 8.50, t.plusDays(75));
        service.loadSeed("Ibuprofen", "400 mg", "B-2234", 300, 9.00, t.plusDays(365));

        service.loadSeed("Azithromycin", "250 mg", "B-2147", 90, 55.00, t.plusDays(45));
        service.loadSeed("Azithromycin", "250 mg", "B-2262", 120, 56.00, t.plusDays(310));

        service.loadSeed("ORS Sachets", "20 g", "B-2190", 400, 35.00, t.plusDays(200));
        service.loadSeed("ORS Sachets", "20 g", "B-2275", 500, 36.00, t.plusDays(420));
    }
}
