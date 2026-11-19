package model.inventory;

import java.time.LocalDate;

public class Batch {

    private final String batchID;
    private final String itemID;
    private final String lotNumber;
    private final int quantity;
    private final LocalDate expirationDate;
    private final LocalDate dateReceived;
    private final BatchStatus status;

    public Batch(
            String batchID,
            String itemID,
            String lotNumber,
            int quantity,
            LocalDate expirationDate,
            LocalDate dateReceived,
            BatchStatus status) {
        if (expirationDate == null) {
            throw new IllegalArgumentException("Batch expiration date is required.");
        }
        this.batchID = batchID;
        this.itemID = itemID;
        this.lotNumber = lotNumber;
        this.quantity = quantity;
        this.expirationDate = expirationDate;
        this.dateReceived = dateReceived;
        this.status = status;
    }

    public String getBatchID() {
        return batchID;
    }

    public String getItemID() {
        return itemID;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public LocalDate getDateReceived() {
        return dateReceived;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public boolean isExpired() {
        return isExpiredOn(LocalDate.now());
    }

    public boolean isExpiredOn(LocalDate date) {
        return expirationDate.isBefore(date);
    }

    public boolean isAvailable() {
        return isAvailableOn(LocalDate.now());
    }

    public boolean isAvailableOn(LocalDate date) {
        return status == BatchStatus.ACTIVE
                && quantity > 0
                && !isExpiredOn(date);
    }
}
