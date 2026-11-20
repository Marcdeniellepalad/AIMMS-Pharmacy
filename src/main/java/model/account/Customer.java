package model.account;

import java.math.BigDecimal;
import java.time.LocalDate;

// INHERITANCE: Customer reuses User's common account state and adds customer-specific balance/status.
public class Customer extends User {

    private final BigDecimal balance;
    private final CustomerStatus status;
    private final LocalDate dateRegistered;

    public Customer(
            String userID,
            String username,
            String passwordHash,
            String fullName,
            BigDecimal balance,
            CustomerStatus status,
            LocalDate dateRegistered) {
        super(userID, username, passwordHash, fullName);
        this.balance = balance;
        this.status = status;
        this.dateRegistered = dateRegistered;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public LocalDate getDateRegistered() {
        return dateRegistered;
    }

    @Override
    // POLYMORPHISM: the shared role method returns the role specific to Customer.
    public UserRole getRole() {
        return UserRole.CUSTOMER;
    }
}
