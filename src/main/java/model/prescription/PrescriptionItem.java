package model.prescription;

public class PrescriptionItem {

    private final String prescriptionItemID;
    private final String prescriptionID;
    private final String itemID;
    private final String medicineName;
    private final String strength;
    private final String dosageForm;
    private final String directions;
    private final int quantityPrescribed;
    private final int quantityDispensed;

    public PrescriptionItem(
            String prescriptionItemID,
            String prescriptionID,
            String itemID,
            String medicineName,
            String strength,
            String dosageForm,
            String directions,
            int quantityPrescribed,
            int quantityDispensed) {
        if (quantityPrescribed <= 0) {
            throw new IllegalArgumentException("Prescribed quantity must be positive.");
        }
        if (quantityDispensed < 0 || quantityDispensed > quantityPrescribed) {
            throw new IllegalArgumentException("Dispensed quantity must be between zero and the prescribed quantity.");
        }
        this.prescriptionItemID = prescriptionItemID;
        this.prescriptionID = prescriptionID;
        this.itemID = itemID;
        this.medicineName = medicineName;
        this.strength = strength;
        this.dosageForm = dosageForm;
        this.directions = directions;
        this.quantityPrescribed = quantityPrescribed;
        this.quantityDispensed = quantityDispensed;
    }

    public String getPrescriptionItemID() {
        return prescriptionItemID;
    }

    public String getPrescriptionID() {
        return prescriptionID;
    }

    public String getItemID() {
        return itemID;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public String getStrength() {
        return strength;
    }

    public String getDosageForm() {
        return dosageForm;
    }

    public String getDirections() {
        return directions;
    }

    public int getQuantityPrescribed() {
        return quantityPrescribed;
    }

    public int getQuantityDispensed() {
        return quantityDispensed;
    }

    public int getQuantityRemaining() {
        return quantityPrescribed - quantityDispensed;
    }

    public PrescriptionItem recordDispense(int quantity) {
        if (quantity <= 0 || quantity > getQuantityRemaining()) {
            throw new IllegalArgumentException("Dispensed quantity must be positive and cannot exceed the remaining quantity.");
        }
        return new PrescriptionItem(
                prescriptionItemID,
                prescriptionID,
                itemID,
                medicineName,
                strength,
                dosageForm,
                directions,
                quantityPrescribed,
                quantityDispensed + quantity);
    }
}
