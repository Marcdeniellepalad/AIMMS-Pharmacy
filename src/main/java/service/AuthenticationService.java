package service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import model.account.UserRole;
import repository.csv.CsvFileManager;
import security.PasswordHasher;

/** Authenticates staff accounts stored in the configured CSV workspace. */
public final class AuthenticationService {

    private static final List<String> ADMIN_HEADERS = List.of(
            "adminID", "username", "passwordHash", "fullName", "status", "dateCreated");
    private static final List<String> PHARMACIST_HEADERS = List.of(
            "pharmacistID", "username", "passwordHash", "fullName", "status", "dateRegistered", "dateApproved");

    private final CsvFileManager csvFileManager = new CsvFileManager();

    public UserSession authenticate(String username, char[] password) {
        if (password == null) {
            throw invalidCredentials();
        }
        try {
            String normalized = username == null ? "" : username.trim();
            if (normalized.isEmpty() || password.length == 0) {
                throw invalidCredentials();
            }

            for (Map<String, String> account : read("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS)) {
                if (normalized.equalsIgnoreCase(account.get("username"))
                        && PasswordHasher.verify(password.clone(), account.get("passwordHash"))) {
                    if (!"ACTIVE".equals(account.get("status"))) {
                        throw new AuthenticationException("This administrator account is inactive.");
                    }
                    return new UserSession(account.get("adminID"), account.get("username"),
                            account.get("fullName"), UserRole.ADMIN);
                }
            }

            for (Map<String, String> account : read("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS)) {
                if (normalized.equalsIgnoreCase(account.get("username"))
                        && PasswordHasher.verify(password.clone(), account.get("passwordHash"))) {
                    if (!"ACTIVE".equals(account.get("status"))) {
                        throw new AuthenticationException("Your pharmacist account is awaiting administrator approval.");
                    }
                    return new UserSession(account.get("pharmacistID"), account.get("username"),
                            account.get("fullName"), UserRole.PHARMACIST);
                }
            }
            throw invalidCredentials();
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private List<Map<String, String>> read(String path, List<String> headers) {
        return csvFileManager.readRecords(Path.of(path), headers);
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
