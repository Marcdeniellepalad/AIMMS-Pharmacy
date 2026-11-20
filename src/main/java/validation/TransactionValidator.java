package validation;

/** Input rules for the transaction lifecycle, currently the cancel-with-refund flow. */
public final class TransactionValidator {

    private TransactionValidator() {
        // static policy holder
    }

    /** Cancelling a sale requires the transaction, an audit reason, and an authorised staff member. */
    public static void validateCancellationRequest(String transactionID, String reason,
            String actorID, String actorRole) {
        if (transactionID == null || transactionID.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A cancellation reason is required for the audit record.");
        }
        AccountValidator.validateCounterActor(actorID, actorRole);
        if (!"PHARMACIST".equals(actorRole) && !"ADMIN".equals(actorRole)) {
            throw new IllegalArgumentException("Only pharmacists and administrators can cancel a sale.");
        }
    }
}