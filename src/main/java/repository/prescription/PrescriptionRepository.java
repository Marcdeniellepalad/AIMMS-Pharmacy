package repository.prescription;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.prescription.Prescription;
import model.prescription.PrescriptionSource;
import model.prescription.PrescriptionStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class PrescriptionRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/prescriptions/prescriptionsDB.csv");
    private static final List<String> HEADERS = List.of(
            "prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
            "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Prescription> findAll() {
        return readPrescriptions();
    }

    public Optional<Prescription> findByID(String prescriptionID) {
        if (prescriptionID == null) {
            return Optional.empty();
        }
        return readPrescriptions().stream()
                .filter(prescription -> prescriptionID.equals(prescription.getPrescriptionID()))
                .findFirst();
    }

    public List<Prescription> findByCustomerID(String customerID) {
        if (customerID == null) {
            return List.of();
        }
        return readPrescriptions().stream()
                .filter(prescription -> customerID.equals(prescription.getCustomerID()))
                .toList();
    }

    public List<Prescription> findByStatus(PrescriptionStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return readPrescriptions().stream()
                .filter(prescription -> prescription.getStatus() == status)
                .toList();
    }

    public void save(Prescription prescription) {
        Objects.requireNonNull(prescription, "prescription must not be null");
        List<Prescription> prescriptions = readPrescriptions();
        if (prescriptions.stream().anyMatch(existing -> existing.getPrescriptionID()
                .equals(prescription.getPrescriptionID()))) {
            throw new IllegalArgumentException("Prescription ID already exists: " + prescription.getPrescriptionID());
        }
        prescriptions.add(prescription);
        writePrescriptions(prescriptions);
    }

    public void update(Prescription prescription) {
        Objects.requireNonNull(prescription, "prescription must not be null");
        List<Prescription> prescriptions = readPrescriptions();
        int existingIndex = findIndexByID(prescriptions, prescription.getPrescriptionID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown prescription ID: "
                    + prescription.getPrescriptionID());
        }
        prescriptions.set(existingIndex, prescription);
        writePrescriptions(prescriptions);
    }

    /** Builds one coordinated-write entry so registration commits the prescription with its items and logs atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<Prescription> prescriptions) {
        Objects.requireNonNull(prescriptions, "prescriptions must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(prescriptions));
    }

    private List<Prescription> readPrescriptions() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Prescription> prescriptions = new ArrayList<>(records.size());
        Set<String> prescriptionIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Prescription prescription = new Prescription(
                        required(record, "prescriptionID"),
                        required(record, "customerID"),
                        required(record, "patientName"),
                        required(record, "prescriberName"),
                        optionalText(record, "prescriberLicenseNumber"),
                        optionalDate(record, "prescriptionDate"),
                        optionalDate(record, "dateReceived"),
                        PrescriptionSource.valueOf(required(record, "sourceType")),
                        optionalText(record, "sourceReference"),
                        PrescriptionStatus.valueOf(required(record, "status")),
                        LocalDate.parse(required(record, "validUntil")));
                if (!prescriptionIDs.add(prescription.getPrescriptionID())) {
                    throw new IllegalArgumentException("duplicate prescriptionID: "
                            + prescription.getPrescriptionID());
                }
                prescriptions.add(prescription);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid prescription record: " + ex.getMessage(), ex);
            }
        }
        return prescriptions;
    }

    private void writePrescriptions(List<Prescription> prescriptions) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(prescriptions));
    }

    private List<Map<String, String>> encodeRows(List<Prescription> prescriptions) {
        Objects.requireNonNull(prescriptions, "prescriptions must not be null");
        List<Map<String, String>> records = new ArrayList<>(prescriptions.size());
        for (Prescription prescription : prescriptions) {
            Objects.requireNonNull(prescription.getSourceType(), "prescription sourceType must not be null");
            Objects.requireNonNull(prescription.getStatus(), "prescription status must not be null");
            Objects.requireNonNull(prescription.getValidUntil(), "prescription validUntil must not be null");
            Map<String, String> record = new java.util.LinkedHashMap<>();
            record.put("prescriptionID", nonBlank(prescription.getPrescriptionID(), "prescriptionID"));
            record.put("customerID", nonBlank(prescription.getCustomerID(), "customerID"));
            record.put("patientName", nonBlank(prescription.getPatientName(), "patientName"));
            record.put("prescriberName", nonBlank(prescription.getPrescriberName(), "prescriberName"));
            record.put("prescriberLicenseNumber", prescription.getPrescriberLicenseNumber() == null
                    ? "" : prescription.getPrescriberLicenseNumber());
            record.put("prescriptionDate", prescription.getPrescriptionDate() == null
                    ? "" : prescription.getPrescriptionDate().toString());
            record.put("dateReceived", prescription.getDateReceived() == null
                    ? "" : prescription.getDateReceived().toString());
            record.put("validUntil", prescription.getValidUntil().toString());
            record.put("sourceType", prescription.getSourceType().name());
            record.put("sourceReference", prescription.getSourceReference() == null
                    ? "" : prescription.getSourceReference());
            record.put("status", prescription.getStatus().name());
            records.add(record);
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

    private static String optionalText(Map<String, String> record, String field) {
        String value = record.get(field);
        return value == null ? "" : value.trim();
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

    private static int findIndexByID(List<Prescription> prescriptions, String prescriptionID) {
        for (int index = 0; index < prescriptions.size(); index++) {
            if (Objects.equals(prescriptions.get(index).getPrescriptionID(), prescriptionID)) {
                return index;
            }
        }
        return -1;
    }
}