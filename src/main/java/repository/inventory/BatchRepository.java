package repository.inventory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.inventory.Batch;
import model.inventory.BatchStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;

public class BatchRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/inventory/batchDB.csv");
    private static final List<String> HEADERS = List.of(
            "batchID", "itemID", "lotNumber", "quantity", "expirationDate", "dateReceived", "status");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Batch> findAll() {
        return readBatches();
    }

    public Optional<Batch> findByID(String batchID) {
        if (batchID == null) {
            return Optional.empty();
        }
        return readBatches().stream()
                .filter(batch -> batchID.equals(batch.getBatchID()))
                .findFirst();
    }

    public List<Batch> findByItemID(String itemID) {
        return readBatches().stream()
                .filter(batch -> Objects.equals(itemID, batch.getItemID()))
                .toList();
    }

    public List<Batch> findByStatus(BatchStatus status) {
        return readBatches().stream()
                .filter(batch -> batch.getStatus() == status)
                .toList();
    }

    public void save(Batch batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        List<Batch> batches = readBatches();
        if (batches.stream().anyMatch(existing -> existing.getBatchID().equals(batch.getBatchID()))) {
            throw new IllegalArgumentException("Batch ID already exists: " + batch.getBatchID());
        }
        batches.add(batch);
        writeBatches(batches);
    }

    public void update(Batch batch) {
        Objects.requireNonNull(batch, "batch must not be null");
        List<Batch> batches = readBatches();
        int existingIndex = findIndexByID(batches, batch.getBatchID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown batch ID: " + batch.getBatchID());
        }
        batches.set(existingIndex, batch);
        writeBatches(batches);
    }

    private List<Batch> readBatches() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Batch> batches = new ArrayList<>(records.size());
        Set<String> batchIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Batch batch = new Batch(
                        required(record, "batchID"),
                        required(record, "itemID"),
                        required(record, "lotNumber"),
                        Integer.parseInt(required(record, "quantity")),
                        LocalDate.parse(required(record, "expirationDate")),
                        LocalDate.parse(required(record, "dateReceived")),
                        BatchStatus.valueOf(required(record, "status")));
                if (!batchIDs.add(batch.getBatchID())) {
                    throw new IllegalArgumentException("duplicate batchID: " + batch.getBatchID());
                }
                batches.add(batch);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid batch record: " + ex.getMessage(), ex);
            }
        }
        return batches;
    }

    private void writeBatches(List<Batch> batches) {
        List<Map<String, String>> records = new ArrayList<>(batches.size());
        for (Batch batch : batches) {
            Objects.requireNonNull(batch.getExpirationDate(), "batch expiration date is required");
            Objects.requireNonNull(batch.getDateReceived(), "batch dateReceived is required");
            Objects.requireNonNull(batch.getStatus(), "batch status must not be null");
            records.add(Map.of(
                    "batchID", nonBlank(batch.getBatchID(), "batchID"),
                    "itemID", nonBlank(batch.getItemID(), "itemID"),
                    "lotNumber", nonBlank(batch.getLotNumber(), "lotNumber"),
                    "quantity", Integer.toString(batch.getQuantity()),
                    "expirationDate", batch.getExpirationDate().toString(),
                    "dateReceived", batch.getDateReceived().toString(),
                    "status", batch.getStatus().name()));
        }
        csvFileManager.writeRecords(FILE_PATH, HEADERS, records);
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

    private static int findIndexByID(List<Batch> batches, String batchID) {
        for (int index = 0; index < batches.size(); index++) {
            if (Objects.equals(batches.get(index).getBatchID(), batchID)) {
                return index;
            }
        }
        return -1;
    }
}
