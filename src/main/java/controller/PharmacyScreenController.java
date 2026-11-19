package controller;

import java.math.BigDecimal;
import model.inventory.ItemStatus;
import service.PharmacyScreenService;

/** Coordinates screen requests between the Swing view and pharmacy services. */
public final class PharmacyScreenController {

    private final PharmacyScreenService screenService;

    public PharmacyScreenController() {
        this(new PharmacyScreenService());
    }

    PharmacyScreenController(PharmacyScreenService screenService) {
        this.screenService = screenService;
    }

    public PharmacyScreenService.ScreenData load(PharmacyScreenService.Screen screen) {
        return screenService.load(screen);
    }

    public void createItem(String name, String category, BigDecimal price, boolean prescriptionRequired,
            ItemStatus status) {
        screenService.createItem(name, category, price, prescriptionRequired, status);
    }
}
