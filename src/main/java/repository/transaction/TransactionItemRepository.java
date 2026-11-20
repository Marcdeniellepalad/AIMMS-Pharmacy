package repository.transaction;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.transaction.TransactionItem;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class TransactionItemRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/transactions/transactionItemsDB.csv");
    private static final List<String> HEADERS = List.of(
            "transactionItemID", "transactionID", "itemID", "batchID", "prescriptionItemID",
            "quantity", "unitPrice", "subtotal");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<TransactionItem> findAll() {
        return readTransactionItems();
    }

    public Optional<TransactionItem> findByID(String transactionItemID) {
        if (transactionItemID == null) {
            return Optional.empty();
        }
        return readTransactionItems().stream()
                .filter(item -> transactionItemID.equals(item.getTransactionItemID()))
                .findFirst();
    }

    public List<TransactionItem> findByTransactionID(String transactionID) {
        if (transactionID == null) {
            return List.of();
        }
        return readTransactionItems().stream()
                .filter(item -> transactionID.equals(item.getTransactionID()))
                .toList();
    }

    public void save(TransactionItem transactionItem) {
        Objects.requireNonNull(transactionItem, "transactionItem must not be null");
        List<TransactionItem> items = readTransactionItems();
        if (items.stream().anyMatch(existing -> existing.getTransactionItemID()
                .equals(transactionItem.getTransactionItemID()))) {
            throw new IllegalArgumentException("Transaction item ID already exists: "
                    + transactionItem.getTransactionItemID());
        }
        items.add(transactionItem);
        writeTransactionItems(items);
    }

    /** Builds one coordinated-write entry so a sale commits its line items with the transaction header atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<TransactionItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(items));
    }

    private List<TransactionItem> readTransactionItems() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<TransactionItem> items = new ArrayList<>(records.size());
        Set<String> itemIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                TransactionItem item = new TransactionItem(
                        required(record, "transactionItemID"),
                        required(record, "transactionID"),
                        required(record, "itemID"),
                        required(record, "batchID"),
                        optionalText(record, "prescriptionItemID"),
                        parseQuantity(record.get("quantity"), "quantity"),
                        new BigDecimal(required(record, "unitPrice")),
                        new BigDecimal(required(record, "subtotal")));
                if (!itemIDs.add(item.getTransactionItemID())) {
                    throw new IllegalArgumentException("duplicate transactionItemID: "
                            + item.getTransactionItemID());
                }
                items.add(item);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid transaction item record: " + ex.getMessage(), ex);
            }
        }
        return items;
    }

    private void writeTransactionItems(List<TransactionItem> items) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(items));
    }

    private List<Map<String, String>> encodeRows(List<TransactionItem> items) {
        Objects.requireNonNull(items, "items must not be null");
        List<Map<String, String>> records = new ArrayList<>(items.size());
        for (TransactionItem item : items) {
            Objects.requireNonNull(item.getUnitPrice(), "transactionItem unitPrice must not be null");
            Objects.requireNonNull(item.getSubtotal(), "transactionItem subtotal must not be null");
            records.add(Map.of(
                    "transactionItemID", nonBlank(item.getTransactionItemID(), "transactionItemID"),
                    "transactionID", nonBlank(item.getTransactionID(), "transactionID"),
                    "itemID", nonBlank(item.getItemID(), "itemID"),
                    "batchID", nonBlank(item.getBatchID(), "batchID"),
                    "prescriptionItemID", item.getPrescriptionItemID() == null
                            ? "" : item.getPrescriptionItemID(),
                    "quantity", Integer.toString(item.getQuantity()),
                    "unitPrice", item.getUnitPrice().toPlainString(),
                    "subtotal", item.getSubtotal().toPlainString()));
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
}