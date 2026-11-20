import org.junit.jupiter.api.Test;
import validation.AccountValidator;
import validation.InventoryValidator;
import validation.PrescriptionValidator;
import validation.SaleValidator;
import validation.TransactionValidator;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins the counter-policy input rules extracted into the validation package in Sprint 4.
 * The messages here are the exact ones the service layer (and therefore the Swing dialogs)
 * has always shown, so refactoring the checks into validators did not change user-facing text.
 */
class ValidationTest {

    /** Runs the action and returns its IllegalArgumentException message, failing if none was thrown. */
    private static String message(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
        fail("expected an IllegalArgumentException");
        return "";
    }

    @Test
    void testCustomerRegistrationRules() {
        assertDoesNotThrow(() -> AccountValidator.validateCustomerRegistration(
                "new.patient", "LongEnough1".toCharArray(), "New Patient"));
        assertEquals("The customer's full name is required.", message(() ->
                AccountValidator.validateCustomerRegistration("new.patient", "LongEnough1".toCharArray(), "  ")));
        assertEquals("A username is required.", message(() ->
                AccountValidator.validateCustomerRegistration(null, "LongEnough1".toCharArray(), "New Patient")));
        assertEquals("Usernames use 4-24 characters: letters, numbers, dots, dashes, or underscores.", message(() ->
                AccountValidator.validateCustomerRegistration("ab", "LongEnough1".toCharArray(), "New Patient")));
        assertEquals("Usernames use 4-24 characters: letters, numbers, dots, dashes, or underscores.", message(() ->
                AccountValidator.validateCustomerRegistration("has space", "LongEnough1".toCharArray(), "New Patient")));
        assertEquals("Passwords must be at least 8 characters.", message(() ->
                AccountValidator.validateCustomerRegistration("new.patient", "short".toCharArray(), "New Patient")));
        assertEquals("Passwords must be at least 8 characters.", message(() ->
                AccountValidator.validateCustomerRegistration("new.patient", null, "New Patient")));
    }

    @Test
    void testCounterActorAndTopUpRules() {
        assertDoesNotThrow(() -> AccountValidator.validateCounterActor("PHR-00001", "PHARMACIST"));
        assertEquals("A signed-in staff member is required.", message(() ->
                AccountValidator.validateCounterActor("", "PHARMACIST")));
        assertEquals("A signed-in staff member is required.", message(() ->
                AccountValidator.validateCounterActor("PHR-00001", null)));
        assertDoesNotThrow(() -> AccountValidator.validateTopUp(new BigDecimal("1.00")));
        assertEquals("Top-up amount must be greater than zero.", message(() ->
                AccountValidator.validateTopUp(BigDecimal.ZERO)));
        assertEquals("Top-up amount must be greater than zero.", message(() ->
                AccountValidator.validateTopUp(new BigDecimal("-5.00"))));
        assertEquals("Top-up amount must be greater than zero.", message(() ->
                AccountValidator.validateTopUp(null)));
    }

    @Test
    void testPrescriptionRegistrationRules() {
        LocalDate today = LocalDate.now();
        assertDoesNotThrow(() -> PrescriptionValidator.validateRegistration(
                "Patient", "Dr. Chan", "PRC-1", today.minusDays(1), today.plusDays(30), 2));
        assertEquals("The patient's name is required.", message(() ->
                PrescriptionValidator.validateRegistration(" ", "Dr. Chan", "PRC-1", today, today.plusDays(30), 1)));
        assertEquals("The prescriber's name is required.", message(() ->
                PrescriptionValidator.validateRegistration("Patient", null, "PRC-1", today, today.plusDays(30), 1)));
        assertEquals("The prescriber's license number is required.", message(() ->
                PrescriptionValidator.validateRegistration("Patient", "Dr. Chan", " ", today, today.plusDays(30), 1)));
        assertEquals("The prescription date cannot be in the future.", message(() ->
                PrescriptionValidator.validateRegistration("Patient", "Dr. Chan", "PRC-1",
                        today.plusDays(1), today.plusDays(30), 1)));
        assertEquals("The valid-until date must be today or later.", message(() ->
                PrescriptionValidator.validateRegistration("Patient", "Dr. Chan", "PRC-1",
                        today.minusDays(1), today.minusDays(1), 1)));
        // The ordering rule (valid-until before prescription date) stays in the validator as a defensive
        // check, but the two rules above already make that combination impossible to reach from the UI.
        assertEquals("Add at least one prescribed medicine.", message(() ->
                PrescriptionValidator.validateRegistration("Patient", "Dr. Chan", "PRC-1",
                        today, today.plusDays(30), 0)));
        assertEquals("Every prescription line needs a medicine.", message(() ->
                PrescriptionValidator.validateLine("  ", 5)));
        assertEquals("Prescribed quantities must be greater than zero.", message(() ->
                PrescriptionValidator.validateLine("DEMO-ITEM-001", 0)));
        assertEquals("Prescription ID is required.", message(() ->
                PrescriptionValidator.validateReviewID(null)));
    }

    @Test
    void testSaleAndCancellationRules() {
        assertDoesNotThrow(() -> SaleValidator.validateSaleRequest(1, "PHR-00001", "PHARMACIST"));
        assertEquals("Add at least one medicine.", message(() ->
                SaleValidator.validateSaleRequest(0, "PHR-00001", "PHARMACIST")));
        assertEquals("A signed-in staff member is required.", message(() ->
                SaleValidator.validateSaleRequest(1, "", " ")));
        assertDoesNotThrow(() -> TransactionValidator.validateCancellationRequest(
                "TXN-00001", "Wrong item", "PHR-00001", "PHARMACIST"));
        assertDoesNotThrow(() -> TransactionValidator.validateCancellationRequest(
                "TXN-00001", "Wrong item", "ADM-00001", "ADMIN"));
        assertEquals("Transaction ID is required.", message(() ->
                TransactionValidator.validateCancellationRequest(null, "Wrong item", "PHR-00001", "PHARMACIST")));
        assertEquals("A cancellation reason is required for the audit record.", message(() ->
                TransactionValidator.validateCancellationRequest("TXN-00001", "  ", "PHR-00001", "PHARMACIST")));
        assertEquals("A signed-in staff member is required.", message(() ->
                TransactionValidator.validateCancellationRequest("TXN-00001", "Wrong item", "", "PHARMACIST")));
        assertEquals("Only pharmacists and administrators can cancel a sale.", message(() ->
                TransactionValidator.validateCancellationRequest("TXN-00001", "Wrong item", "CUS-00001", "CUSTOMER")));
    }

    @Test
    void testInventoryRules() {
        assertDoesNotThrow(() -> InventoryValidator.validateNewItem("Paracetamol", "Tablet", new BigDecimal("12.50")));
        assertEquals("Medicine name and category are required.", message(() ->
                InventoryValidator.validateNewItem(null, " ", new BigDecimal("12.50"))));
        assertEquals("Price must be zero or greater.", message(() ->
                InventoryValidator.validateNewItem("Paracetamol", "Tablet", new BigDecimal("-1"))));
        assertDoesNotThrow(() -> InventoryValidator.validateRestockRequest(10, "PHR-00001"));
        assertEquals("Requested quantity must be greater than zero.", message(() ->
                InventoryValidator.validateRestockRequest(0, "PHR-00001")));
        assertEquals("A signed-in pharmacist is required.", message(() ->
                InventoryValidator.validateRestockRequest(10, " ")));
        LocalDate today = LocalDate.now();
        assertDoesNotThrow(() -> InventoryValidator.validateRestockApproval(
                "RST-00001", "ADM-00001", "LOT-1", today));
        assertEquals("Select a restock request first.", message(() ->
                InventoryValidator.validateRestockApproval(null, "ADM-00001", "LOT-1", today)));
        assertEquals("A signed-in administrator is required.", message(() ->
                InventoryValidator.validateRestockApproval("RST-00001", "", "LOT-1", today)));
        assertEquals("Supplier lot number is required.", message(() ->
                InventoryValidator.validateRestockApproval("RST-00001", "ADM-00001", " ", today)));
        assertEquals("Enter an expiration date that is today or later.", message(() ->
                InventoryValidator.validateRestockApproval("RST-00001", "ADM-00001", "LOT-1", today.minusDays(1))));
    }
}