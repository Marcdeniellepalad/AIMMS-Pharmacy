package validation;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input rules for catalog maintenance and the restock request lifecycle. */
public final class InventoryValidator {

    private InventoryValidator() {
        // static policy holder
    }

    /** New catalog entries need a name, a category, and a non-negative price. */
    public static void validateNewItem(String name, String category, BigDecimal price) {
        if (name == null || name.isBlank() || category == null || category.isBlank()) {
            throw new IllegalArgumentException("Medicine name and category are required.");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or greater.");
        }
    }

    /** A restock request needs a positive quantity and the pharmacist who submitted it. */
    public static void validateRestockRequest(int quantity, String pharmacistID) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        }
        if (pharmacistID == null || pharmacistID.isBlank()) {
            throw new IllegalArgumentException("A signed-in pharmacist is required.");
        }
    }

    /** Receiving stock needs the request, the approver, the supplier lot, and a valid expiration date. */
    public static void validateRestockApproval(String requestID, String adminID, String lotNumber,
            LocalDate expirationDate) {
        if (requestID == null || requestID.isBlank()) {
            throw new IllegalArgumentException("Select a restock request first.");
        }
        if (adminID == null || adminID.isBlank()) {
            throw new IllegalArgumentException("A signed-in administrator is required.");
        }
        if (lotNumber == null || lotNumber.isBlank()) {
            throw new IllegalArgumentException("Supplier lot number is required.");
        }
        if (expirationDate == null || expirationDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Enter an expiration date that is today or later.");
        }
    }
}