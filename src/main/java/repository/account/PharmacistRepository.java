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

import model.account.Pharmacist;
import model.account.StaffStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class PharmacistRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/accounts/pharmacistDB.csv");
    private static final List<String> HEADERS = List.of(
            "pharmacistID", "username", "passwordHash", "fullName", "status", "dateRegistered", "dateApproved");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Pharmacist> findAll() {
        return readPharmacists();
    }

    public Optional<Pharmacist> findByID(String pharmacistID) {
        if (pharmacistID == null) {
            return Optional.empty();
        }
        return readPharmacists().stream()
                .filter(pharmacist -> pharmacistID.equals(pharmacist.getUserID()))
                .findFirst();
    }

    public Optional<Pharmacist> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String normalized = username.trim();
        return readPharmacists().stream()
                .filter(pharmacist -> pharmacist.getUsername().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public List<Pharmacist> findByStatus(StaffStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return readPharmacists().stream()
                .filter(pharmacist -> pharmacist.getStatus() == status)
                .toList();
    }

    public void save(Pharmacist pharmacist) {
        Objects.requireNonNull(pharmacist, "pharmacist must not be null");
        List<Pharmacist> pharmacists = readPharmacists();
        if (pharmacists.stream().anyMatch(existing -> existing.getUserID().equals(pharmacist.getUserID()))) {
            throw new IllegalArgumentException("Pharmacist ID already exists: " + pharmacist.getUserID());
        }
        pharmacists.add(pharmacist);
        writePharmacists(pharmacists);
    }

    public void update(Pharmacist pharmacist) {
        Objects.requireNonNull(pharmacist, "pharmacist must not be null");
        List<Pharmacist> pharmacists = readPharmacists();
        int existingIndex = findIndexByID(pharmacists, pharmacist.getUserID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown pharmacist ID: " + pharmacist.getUserID());
        }
        pharmacists.set(existingIndex, pharmacist);
        writePharmacists(pharmacists);
    }

    /** Builds one coordinated-write entry so approval changes commit together with audit files atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<Pharmacist> pharmacists) {
        Objects.requireNonNull(pharmacists, "pharmacists must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(pharmacists));
    }

    private List<Pharmacist> readPharmacists() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Pharmacist> pharmacists = new ArrayList<>(records.size());
        Set<String> pharmacistIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Pharmacist pharmacist = new Pharmacist(
                        required(record, "pharmacistID"),
                        required(record, "username"),
                        required(record, "passwordHash"),
                        required(record, "fullName"),
                        StaffStatus.valueOf(required(record, "status")),
                        LocalDate.parse(required(record, "dateRegistered")),
                        optionalDate(record, "dateApproved"));
                if (!pharmacistIDs.add(pharmacist.getUserID())) {
                    throw new IllegalArgumentException("duplicate pharmacistID: " + pharmacist.getUserID());
                }
                pharmacists.add(pharmacist);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid pharmacist record: " + ex.getMessage(), ex);
            }
        }
        return pharmacists;
    }

    private void writePharmacists(List<Pharmacist> pharmacists) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(pharmacists));
    }

    private List<Map<String, String>> encodeRows(List<Pharmacist> pharmacists) {
        Objects.requireNonNull(pharmacists, "pharmacists must not be null");
        List<Map<String, String>> records = new ArrayList<>(pharmacists.size());
        for (Pharmacist pharmacist : pharmacists) {
            Objects.requireNonNull(pharmacist.getStatus(), "pharmacist status must not be null");
            Objects.requireNonNull(pharmacist.getDateRegistered(), "pharmacist dateRegistered must not be null");
            records.add(Map.of(
                    "pharmacistID", nonBlank(pharmacist.getUserID(), "pharmacistID"),
                    "username", nonBlank(pharmacist.getUsername(), "username"),
                    "passwordHash", nonBlank(pharmacist.getPasswordHash(), "passwordHash"),
                    "fullName", nonBlank(pharmacist.getFullName(), "fullName"),
                    "status", pharmacist.getStatus().name(),
                    "dateRegistered", pharmacist.getDateRegistered().toString(),
                    "dateApproved", pharmacist.getDateApproved() == null
                            ? "" : pharmacist.getDateApproved().toString()));
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

    private static LocalDate optionalDate(Map<String, String> record, String field) {
        String value = record.get(field);
        return value == null || value.isBlank() ? null : LocalDate.parse(value.trim());
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static int findIndexByID(List<Pharmacist> pharmacists, String pharmacistID) {
        for (int index = 0; index < pharmacists.size(); index++) {
            if (Objects.equals(pharmacists.get(index).getUserID(), pharmacistID)) {
                return index;
            }
        }
        return -1;
    }
}