package model.log;

public enum TransactionLogAction {
    COMPLETE,
    CANCELLED,
    VOID_REQUESTED,
    VOID_APPROVED,
    VOID_REVERSED,
    VOID_FAILED
}
