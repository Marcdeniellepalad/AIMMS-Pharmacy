package validation;

import java.math.BigDecimal;

/** Input rules for counter-side account actions: registration, staff presence, and top-ups. */
public final class AccountValidator {

    private AccountValidator() {
        // static policy holder
    }

    /** Walk-in registration rules enforced at the counter before any file is touched. */
    public static void validateCustomerRegistration(String username, char[] password, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("The customer's full name is required.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A username is required.");
        }
        String normalizedUsername = username.trim().toLowerCase();
        if (!normalizedUsername.matches("[a-z0-9._-]{4,24}")) {
            throw new IllegalArgumentException(
                    "Usernames use 4-24 characters: letters, numbers, dots, dashes, or underscores.");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Passwords must be at least 8 characters.");
        }
    }

    /** Every counter action must be attributed to a signed-in staff member. */
    public static void validateCounterActor(String actorID, String actorRole) {
        if (actorID == null || actorID.isBlank() || actorRole == null || actorRole.isBlank()) {
            throw new IllegalArgumentException("A signed-in staff member is required.");
        }
    }

    /** Top-ups must add money; zero and negative amounts are rejected. */
    public static void validateTopUp(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Top-up amount must be greater than zero.");
        }
    }
}