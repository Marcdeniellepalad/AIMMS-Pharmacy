package controller;

import java.math.BigDecimal;
import model.inventory.ItemStatus;
import service.PharmacyScreenService;
import java.util.List;

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

    public String completeSale(String customerID, List<PharmacyScreenService.SaleLine> lines, String actorID, String actorRole) {
        return screenService.completeSale(customerID, lines, actorID, actorRole);
    }

    public String createRestockRequest(String itemID, int quantity, String reason, String pharmacistID) {
        return screenService.createRestockRequest(itemID, quantity, reason, pharmacistID);
    }

    public String approveRestockRequest(String requestID, String adminID, int quantityReceived,
            String lotNumber, java.time.LocalDate expirationDate) {
        return screenService.approveRestockRequest(requestID, adminID, quantityReceived, lotNumber, expirationDate);
    }

    public String reviewPrescription(String prescriptionID, String newStatus, String notes,
            String actorID, String actorRole) {
        return screenService.reviewPrescription(prescriptionID, newStatus, notes, actorID, actorRole);
    }

    public String voidTransaction(String transactionID, String reason, String actorID, String actorRole) {
        return screenService.voidTransaction(transactionID, reason, actorID, actorRole);
    }

    public String approvePharmacist(String pharmacistID, String adminID) {
        return screenService.approvePharmacist(pharmacistID, adminID);
    }

    public String registerCustomer(String username, char[] password, String fullName) {
        return screenService.registerCustomer(username, password, fullName);
    }

    public BigDecimal topUpCustomerBalance(String customerID, BigDecimal amount,
            String actorID, String actorRole) {
        return screenService.topUpCustomerBalance(customerID, amount, actorID, actorRole);
    }

    public String rejectPharmacist(String pharmacistID, String adminID) {
        return screenService.rejectPharmacist(pharmacistID, adminID);
    }
}