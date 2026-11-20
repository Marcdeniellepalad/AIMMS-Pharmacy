package service;

import java.util.Arrays;
import java.util.Optional;

import model.account.Admin;
import model.account.AdminStatus;
import model.account.Pharmacist;
import model.account.StaffStatus;
import model.account.UserRole;
import repository.account.AdminRepository;
import repository.account.PharmacistRepository;
import security.PasswordHasher;

/** Authenticates staff accounts through the admin and pharmacist repositories. */
public final class AuthenticationService {

    private final AdminRepository adminRepository;
    private final PharmacistRepository pharmacistRepository;

    public AuthenticationService() {
        this(new AdminRepository(), new PharmacistRepository());
    }

    AuthenticationService(AdminRepository adminRepository, PharmacistRepository pharmacistRepository) {
        this.adminRepository = adminRepository;
        this.pharmacistRepository = pharmacistRepository;
    }

    public UserSession authenticate(String username, char[] password) {
        if (password == null) {
            throw invalidCredentials();
        }
        try {
            String normalized = username == null ? "" : username.trim();
            if (normalized.isEmpty() || password.length == 0) {
                throw invalidCredentials();
            }

            Optional<Admin> admin = adminRepository.findByUsername(normalized)
                    .filter(account -> PasswordHasher.verify(password.clone(), account.getPasswordHash()));
            if (admin.isPresent()) {
                Admin account = admin.get();
                if (account.getStatus() != AdminStatus.ACTIVE) {
                    throw new AuthenticationException("This administrator account is inactive.");
                }
                return new UserSession(account.getUserID(), account.getUsername(),
                        account.getFullName(), UserRole.ADMIN);
            }

            Optional<Pharmacist> pharmacist = pharmacistRepository.findByUsername(normalized)
                    .filter(account -> PasswordHasher.verify(password.clone(), account.getPasswordHash()));
            if (pharmacist.isPresent()) {
                Pharmacist account = pharmacist.get();
                if (account.getStatus() == StaffStatus.ACTIVE) {
                    return new UserSession(account.getUserID(), account.getUsername(),
                            account.getFullName(), UserRole.PHARMACIST);
                }
                throw new AuthenticationException(account.getStatus() == StaffStatus.REJECTED
                        ? "This pharmacist application was not approved."
                        : "Your pharmacist account is awaiting administrator approval.");
            }
            throw invalidCredentials();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static AuthenticationException invalidCredentials() {
        return new AuthenticationException("Username or password is incorrect.");
    }

    public record UserSession(String userID, String username, String fullName, UserRole role) { }

    public static final class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}