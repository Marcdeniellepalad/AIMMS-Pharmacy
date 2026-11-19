package model.account;

import security.PasswordHasher;

public abstract class User {

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
