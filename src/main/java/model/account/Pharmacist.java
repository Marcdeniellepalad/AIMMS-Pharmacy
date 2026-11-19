package model.account;

import java.time.LocalDate;

public class Pharmacist extends User {

    private final StaffStatus status;
    private final LocalDate dateRegistered;
    private final LocalDate dateApproved;

    public Pharmacist(
            String userID,
            String username,
            String passwordHash,
            String fullName,
            StaffStatus status,
            LocalDate dateRegistered,
            LocalDate dateApproved) {
        super(userID, username, passwordHash, fullName);
        this.status = status;
        this.dateRegistered = dateRegistered;
        this.dateApproved = dateApproved;
    }

    public StaffStatus getStatus() {
        return status;
    }

    public LocalDate getDateRegistered() {
        return dateRegistered;
    }

    public LocalDate getDateApproved() {
        return dateApproved;
    }

    @Override
    public UserRole getRole() {
        return UserRole.PHARMACIST;
    }
}
