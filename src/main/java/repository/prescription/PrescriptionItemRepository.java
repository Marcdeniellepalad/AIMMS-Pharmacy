package repository.prescription;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.prescription.PrescriptionItem;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class PrescriptionItemRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv");
    private static final List<String> HEADERS = List.of(
            "prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
            "directions", "quantityPrescribed", "quantityDispensed");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<PrescriptionItem> findAll() {
        return readPrescriptionItems();
    }

    public Optional<PrescriptionItem> findByID(String prescriptionItemID) {
        if (prescriptionItemID == null) {
            return Optional.empty();
        }
        return readPrescriptionItems().stream()
                .filter(item -> prescriptionItemID.equals(item.getPrescriptionItemID()))
                .findFirst();
    }

    public List<PrescriptionItem> findByPrescriptionID(String prescriptionID) {
        if (prescriptionID == null) {
            return List.of();
        }
        return readPrescriptionItems().stream()
                .filter(item -> prescriptionID.equals(item.getPrescriptionID()))
                .toList();
    }

    public void save(PrescriptionItem prescriptionItem) {
        Objects.requireNonNull(prescriptionItem, "prescriptionItem must not be null");
        List<PrescriptionItem> items = readPrescriptionItems();
        if (items.stream().anyMatch(existing -> existing.getPrescriptionItemID()
                .equals(prescriptionItem.getPrescriptionItemID()))) {
            throw new IllegalArgumentException("Prescription item ID already exists: "
                    + prescriptionItem.getPrescriptionItemID());
        }
        items.add(prescriptionItem);
        writePrescriptionItems(items);
    }

    public void update(PrescriptionItem prescriptionItem) {
        Objects.requireNonNull(prescriptionItem, "prescriptionItem must not be null");
        List<PrescriptionItem> items = readPrescriptionItems();
        int existingIndex = findIndexByID(items, prescriptionItem.getPrescriptionItemID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown prescription item ID: "
                    + prescriptionItem.getPrescriptionItemID());
        }
        items.set(existingIndex, prescriptionItem);
        writePrescriptionItems(items);
    }

    /** Builds one coordinated-write entry so dispensing commits item progress with stock and sale files atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<PrescriptionItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(items));
    }

    private List<PrescriptionItem> readPrescriptionItems() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<PrescriptionItem> items = new ArrayList<>(records.size());
        Set<String> itemIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                PrescriptionItem item = new PrescriptionItem(
                        required(record, "prescriptionItemID"),
                        required(record, "prescriptionID"),
                        required(record, "itemID"),
                        required(record, "medicineName"),
                        optionalText(record, "strength"),
                        optionalText(record, "dosageForm"),
                        optionalText(record, "directions"),
                        parseQuantity(record.get("quantityPrescribed"), "quantityPrescribed"),
                        parseQuantity(record.get("quantityDispensed"), "quantityDispensed"));
                if (!itemIDs.add(item.getPrescriptionItemID())) {
                    throw new IllegalArgumentException("duplicate prescriptionItemID: "
                            + item.getPrescriptionItemID());
                }
                items.add(item);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid prescription item record: " + ex.getMessage(), ex);
            }
        }
        return items;
    }

    private void writePrescriptionItems(List<PrescriptionItem> items) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(items));
    }

    private List<Map<String, String>> encodeRows(List<PrescriptionItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        List<Map<String, String>> records = new ArrayList<>(items.size());
        for (PrescriptionItem item : items) {
            records.add(Map.of(
                    "prescriptionItemID", nonBlank(item.getPrescriptionItemID(), "prescriptionItemID"),
                    "prescriptionID", nonBlank(item.getPrescriptionID(), "prescriptionID"),
                    "itemID", nonBlank(item.getItemID(), "itemID"),
                    "medicineName", nonBlank(item.getMedicineName(), "medicineName"),
                    "strength", item.getStrength() == null ? "" : item.getStrength(),
                    "dosageForm", item.getDosageForm() == null ? "" : item.getDosageForm(),
                    "directions", item.getDirections() == null ? "" : item.getDirections(),
                    "quantityPrescribed", Integer.toString(item.getQuantityPrescribed()),
                    "quantityDispensed", Integer.toString(item.getQuantityDispensed())));
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

    private static int parseQuantity(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return Integer.parseInt(value.trim());
    }

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static int findIndexByID(List<PrescriptionItem> items, String prescriptionItemID) {
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(items.get(index).getPrescriptionItemID(), prescriptionItemID)) {
                return index;
            }
        }
        return -1;
    }
}