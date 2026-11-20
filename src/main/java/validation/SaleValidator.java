package validation;

/** Input rules for counter sales before any stock or balance is checked. */
public final class SaleValidator {

    private SaleValidator() {
        // static policy holder
    }

    /** A sale needs at least one line and a signed-in staff member to attribute it to. */
    public static void validateSaleRequest(int lineCount, String actorID, String actorRole) {
        if (lineCount <= 0) {
            throw new IllegalArgumentException("Add at least one medicine.");
        }
        AccountValidator.validateCounterActor(actorID, actorRole);
    }
}