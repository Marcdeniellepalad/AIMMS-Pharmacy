package model.log;

public enum InventoryLogAction {
    RESTOCK_REQUESTED,
    RESTOCK_RECEIVED,
    SALE,
    ADJUSTMENT,
    QUARANTINE,
    RELEASE_QUARANTINE,
    PULL
}
