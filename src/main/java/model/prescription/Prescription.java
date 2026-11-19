package model.prescription;

import java.time.LocalDate;

public class Prescription {

    private final String prescriptionID;
    private final String customerID;
    private final String patientName;
    private final String prescriberName;
    private final String prescriberLicenseNumber;
    private final LocalDate prescriptionDate;
    private final PrescriptionSource sourceType;
    private final String sourceReference;
    private final PrescriptionStatus status;
    private final LocalDate dateReceived;
    private final LocalDate validUntil;

    public Prescription(
            String prescriptionID,
            String customerID,
            String patientName,
            String prescriberName,
            String prescriberLicenseNumber,
            LocalDate prescriptionDate,
            LocalDate dateReceived,
            PrescriptionSource sourceType,
            String sourceReference,
            PrescriptionStatus status,
            LocalDate validUntil) {
        if (validUntil == null) {
            throw new IllegalArgumentException("Prescription valid-until date is required.");
        }
        this.prescriptionID = prescriptionID;
        this.customerID = customerID;
        this.patientName = patientName;
        this.prescriberName = prescriberName;
        this.prescriberLicenseNumber = prescriberLicenseNumber;
        this.prescriptionDate = prescriptionDate;
        this.dateReceived = dateReceived;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.status = status;
        this.validUntil = validUntil;
    }

    public String getPrescriptionID() {
        return prescriptionID;
    }

    public String getCustomerID() {
        return customerID;
    }

    public String getPatientName() {
        return patientName;
    }

    public String getPrescriberName() {
        return prescriberName;
    }

    public String getPrescriberLicenseNumber() {
        return prescriberLicenseNumber;
    }

    public LocalDate getPrescriptionDate() {
        return prescriptionDate;
    }

    public PrescriptionSource getSourceType() {
        return sourceType;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public LocalDate getDateReceived() {
        return dateReceived;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public boolean isValid() {
        return isUsableOn(LocalDate.now());
    }

    public boolean isUsableOn(LocalDate date) {
        boolean verified = status == PrescriptionStatus.VERIFIED
                || status == PrescriptionStatus.PARTIALLY_DISPENSED;
        return verified && !validUntil.isBefore(date);
    }
}
