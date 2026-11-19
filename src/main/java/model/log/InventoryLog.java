package model.log;

import java.time.LocalDateTime;

public class InventoryLog {

    private final String logID;
    private final String batchID;
    private final String itemID;
    private final String actorID;
    private final String actorRole;
    private final InventoryLogAction action;
    private final int quantity;
    private final LocalDateTime dateTime;
    private final String reason;
    private final String outcome;
    private final String relatedRecordID;
    private final String details;

    public InventoryLog(
            String logID,
            String batchID,
            String itemID,
            String actorID,
            String actorRole,
            InventoryLogAction action,
            int quantity,
            LocalDateTime dateTime,
            String reason,
            String outcome,
            String relatedRecordID,
            String details) {
        this.logID = logID;
        this.batchID = batchID;
        this.itemID = itemID;
        this.actorID = actorID;
        this.actorRole = actorRole;
        this.action = action;
        this.quantity = quantity;
        this.dateTime = dateTime;
        this.reason = reason;
        this.outcome = outcome;
        this.relatedRecordID = relatedRecordID;
        this.details = details;
    }

    public String getLogID() {
        return logID;
    }

    public String getBatchID() {
        return batchID;
    }

    public String getItemID() {
        return itemID;
    }

    public String getActorID() {
        return actorID;
    }

    public String getActorRole() {
        return actorRole;
    }

    public InventoryLogAction getAction() {
        return action;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public String getReason() {
        return reason;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getRelatedRecordID() {
        return relatedRecordID;
    }

    public String getDetails() {
        return details;
    }
}
