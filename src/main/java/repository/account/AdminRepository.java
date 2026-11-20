package repository.account;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.account.Admin;
import model.account.AdminStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class AdminRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/accounts/adminDB.csv");
    private static final List<String> HEADERS = List.of(
            "adminID", "username", "passwordHash", "fullName", "status", "dateCreated");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Admin> findAll() {
        return readAdmins();
    }

    public Optional<Admin> findByID(String adminID) {
        if (adminID == null) {
            return Optional.empty();
        }
        return readAdmins().stream()
                .filter(admin -> adminID.equals(admin.getUserID()))
                .findFirst();
    }

    public Optional<Admin> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String normalized = username.trim();
        return readAdmins().stream()
                .filter(admin -> admin.getUsername().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public void save(Admin admin) {
        Objects.requireNonNull(admin, "admin must not be null");
        List<Admin> admins = readAdmins();
        if (admins.stream().anyMatch(existing -> existing.getUserID().equals(admin.getUserID()))) {
            throw new IllegalArgumentException("Admin ID already exists: " + admin.getUserID());
        }
        admins.add(admin);
        writeAdmins(admins);
    }

    public void update(Admin admin) {
        Objects.requireNonNull(admin, "admin must not be null");
        List<Admin> admins = readAdmins();
        int existingIndex = findIndexByID(admins, admin.getUserID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown admin ID: " + admin.getUserID());
        }
        admins.set(existingIndex, admin);
        writeAdmins(admins);
    }

    /** Builds one coordinated-write entry for multi-file commits that touch administrator accounts. */
    public CsvWriteCoordinator.Update asUpdate(List<Admin> admins) {
        Objects.requireNonNull(admins, "admins must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(admins));
    }

    private List<Admin> readAdmins() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Admin> admins = new ArrayList<>(records.size());
        Set<String> adminIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Admin admin = new Admin(
                        required(record, "adminID"),
                        required(record, "username"),
                        required(record, "passwordHash"),
                        required(record, "fullName"),
                        AdminStatus.valueOf(required(record, "status")),
                        LocalDate.parse(required(record, "dateCreated")));
                if (!adminIDs.add(admin.getUserID())) {
                    throw new IllegalArgumentException("duplicate adminID: " + admin.getUserID());
                }
                admins.add(admin);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid admin record: " + ex.getMessage(), ex);
            }
        }
        return admins;
    }

    private void writeAdmins(List<Admin> admins) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(admins));
    }

    private List<Map<String, String>> encodeRows(List<Admin> admins) {
        Objects.requireNonNull(admins, "admins must not be null");
        List<Map<String, String>> records = new ArrayList<>(admins.size());
        for (Admin admin : admins) {
            Objects.requireNonNull(admin.getStatus(), "admin status must not be null");
            Objects.requireNonNull(admin.getDateCreated(), "admin dateCreated must not be null");
            records.add(Map.of(
                    "adminID", nonBlank(admin.getUserID(), "adminID"),
                    "username", nonBlank(admin.getUsername(), "username"),
                    "passwordHash", nonBlank(admin.getPasswordHash(), "passwordHash"),
                    "fullName", nonBlank(admin.getFullName(), "fullName"),
                    "status", admin.getStatus().name(),
                    "dateCreated", admin.getDateCreated().toString()));
        }
        return records;
    }

    private static String required(Map<String, String> record, String field) {
        String value = record.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static int findIndexByID(List<Admin> admins, String adminID) {
        for (int index = 0; index < admins.size(); index++) {
            if (Objects.equals(admins.get(index).getUserID(), adminID)) {
                return index;
            }
        }
        return -1;
    }
}