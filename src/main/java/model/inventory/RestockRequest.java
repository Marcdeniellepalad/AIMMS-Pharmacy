package model.inventory;

import java.time.LocalDate;

public class RestockRequest {

    private final String requestID;
    private final String itemID;
    private final int quantityRequested;
    private final int quantityReceived;
    private final String requestedByPharmacistID;
    private final LocalDate dateRequested;
    private final String receivedByID;
    private final LocalDate dateReceived;
    private final RestockRequestStatus status;
    private final String details;

    public RestockRequest(
            String requestID,
            String itemID,
            int quantityRequested,
            int quantityReceived,
            String requestedByPharmacistID,
            LocalDate dateRequested,
            String receivedByID,
            LocalDate dateReceived,
            RestockRequestStatus status,
            String details) {
        if (quantityRequested <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive.");
        }
        if (quantityReceived < 0 || quantityReceived > quantityRequested) {
            throw new IllegalArgumentException("Received quantity must be between zero and the requested quantity.");
        }
        this.requestID = requestID;
        this.itemID = itemID;
        this.quantityRequested = quantityRequested;
        this.quantityReceived = quantityReceived;
        this.requestedByPharmacistID = requestedByPharmacistID;
        this.dateRequested = dateRequested;
        this.receivedByID = receivedByID;
        this.dateReceived = dateReceived;
        this.status = status;
        this.details = details;
    }

    public String getRequestID() {
        return requestID;
    }

    public String getItemID() {
        return itemID;
    }

    public int getQuantityRequested() {
        return quantityRequested;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public int getQuantityRemaining() {
        return quantityRequested - quantityReceived;
    }

    public String getRequestedByPharmacistID() {
        return requestedByPharmacistID;
    }

    public LocalDate getDateRequested() {
        return dateRequested;
    }

    public String getReceivedByID() {
        return receivedByID;
    }

    public LocalDate getDateReceived() {
        return dateReceived;
    }

    public RestockRequestStatus getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    public RestockRequest recordReceipt(int quantity, String receivedByID, LocalDate dateReceived) {
        if (status != RestockRequestStatus.REQUESTED && status != RestockRequestStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Only an open restock request can receive stock.");
        }
        if (quantity <= 0 || quantity > getQuantityRemaining()) {
            throw new IllegalArgumentException("Receipt quantity must be positive and cannot exceed the remaining request.");
        }
        int newQuantityReceived = quantityReceived + quantity;
        RestockRequestStatus newStatus = newQuantityReceived == quantityRequested
                ? RestockRequestStatus.RECEIVED
                : RestockRequestStatus.PARTIALLY_RECEIVED;
        return new RestockRequest(
                requestID,
                itemID,
                quantityRequested,
                newQuantityReceived,
                requestedByPharmacistID,
                dateRequested,
                receivedByID,
                dateReceived,
                newStatus,
                details);
    }
}
