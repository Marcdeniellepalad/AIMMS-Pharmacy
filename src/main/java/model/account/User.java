package model.account;

import security.PasswordHasher;

// ABSTRACTION: common account details and the role contract are defined once;
// Admin, Pharmacist, and Customer supply their specialized behavior.
public abstract class User {

    // ENCAPSULATION: state is private and final, and callers read it through getters.
    private final String userID;
    private final String username;
    private final String passwordHash;
    private final String fullName;

    protected User(
            String userID,
            String username,
            String passwordHash,
            String fullName) {
        if (!PasswordHasher.isEncodedHash(passwordHash)) {
            throw new IllegalArgumentException("User credentials must be a supported salted password hash.");
        }
        this.userID = userID;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public String getUserID() {
        return userID;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public abstract UserRole getRole();
}
