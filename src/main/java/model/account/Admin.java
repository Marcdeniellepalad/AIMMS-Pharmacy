package model.account;

import java.time.LocalDate;

// INHERITANCE: Admin reuses User's identity and credential fields, then adds admin-specific data.
public class Admin extends User {

    private final AdminStatus status;
    private final LocalDate dateCreated;

    public Admin(
            String userID,
            String username,
            String passwordHash,
            String fullName,
            AdminStatus status,
            LocalDate dateCreated) {
        super(userID, username, passwordHash, fullName);
        this.status = status;
        this.dateCreated = dateCreated;
    }

    public AdminStatus getStatus() {
        return status;
    }

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    @Override
    // POLYMORPHISM: the shared role method returns the role specific to Admin.
    public UserRole getRole() {
        return UserRole.ADMIN;
    }
}
