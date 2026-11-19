package model.log;

public enum PrescriptionLogAction {
    RECEIVED,
    VERIFIED,
    CLARIFICATION_REQUESTED,
    REJECT,
    EXPIRE,
    PARTIAL_DISPENSE,
    DISPENSE,
    CANCEL
}
