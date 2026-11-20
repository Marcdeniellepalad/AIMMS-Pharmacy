package validation;

import java.time.LocalDate;

/** Input rules for registering and reviewing externally issued paper prescriptions. */
public final class PrescriptionValidator {

    private PrescriptionValidator() {
        // static policy holder
    }

    /** Header-level checks for a paper prescription intake (per-line rules are validated separately). */
    public static void validateRegistration(String patientName, String prescriberName,
            String prescriberLicenseNumber, LocalDate prescriptionDate, LocalDate validUntil, int lineCount) {
        if (patientName == null || patientName.isBlank()) {
            throw new IllegalArgumentException("The patient's name is required.");
        }
        if (prescriberName == null || prescriberName.isBlank()) {
            throw new IllegalArgumentException("The prescriber's name is required.");
        }
        if (prescriberLicenseNumber == null || prescriberLicenseNumber.isBlank()) {
            throw new IllegalArgumentException("The prescriber's license number is required.");
        }
        if (prescriptionDate == null || prescriptionDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The prescription date cannot be in the future.");
        }
        if (validUntil == null || validUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("The valid-until date must be today or later.");
        }
        if (validUntil.isBefore(prescriptionDate)) {
            throw new IllegalArgumentException("The valid-until date cannot be before the prescription date.");
        }
        if (lineCount <= 0) {
            throw new IllegalArgumentException("Add at least one prescribed medicine.");
        }
    }

    /** One medicine line entered when registering a paper prescription. */
    public static void validateLine(String itemID, int quantityPrescribed) {
        if (itemID == null || itemID.isBlank()) {
            throw new IllegalArgumentException("Every prescription line needs a medicine.");
        }
        if (quantityPrescribed <= 0) {
            throw new IllegalArgumentException("Prescribed quantities must be greater than zero.");
        }
    }

    /** A prescription review needs the prescription it applies to. */
    public static void validateReviewID(String prescriptionID) {
        if (prescriptionID == null || prescriptionID.isBlank()) {
            throw new IllegalArgumentException("Prescription ID is required.");
        }
    }
}