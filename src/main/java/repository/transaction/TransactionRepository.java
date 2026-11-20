package repository.transaction;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.transaction.Transaction;
import model.transaction.TransactionStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class TransactionRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/transactions/transactionsDB.csv");
    private static final List<String> HEADERS = List.of(
            "transactionID", "customerID", "pharmacistID", "totalAmount", "status", "dateTime");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Transaction> findAll() {
        return readTransactions();
    }

    public Optional<Transaction> findByID(String transactionID) {
        if (transactionID == null) {
            return Optional.empty();
        }
        return readTransactions().stream()
                .filter(transaction -> transactionID.equals(transaction.getTransactionID()))
                .findFirst();
    }

    public List<Transaction> findByCustomerID(String customerID) {
        if (customerID == null) {
            return List.of();
        }
        return readTransactions().stream()
                .filter(transaction -> customerID.equals(transaction.getCustomerID()))
                .toList();
    }

    public List<Transaction> findByStatus(TransactionStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return readTransactions().stream()
                .filter(transaction -> transaction.getStatus() == status)
                .toList();
    }

    public void save(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        List<Transaction> transactions = readTransactions();
        if (transactions.stream().anyMatch(existing -> existing.getTransactionID()
                .equals(transaction.getTransactionID()))) {
            throw new IllegalArgumentException("Transaction ID already exists: " + transaction.getTransactionID());
        }
        transactions.add(transaction);
        writeTransactions(transactions);
    }

    public void update(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        List<Transaction> transactions = readTransactions();
        int existingIndex = findIndexByID(transactions, transaction.getTransactionID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown transaction ID: "
                    + transaction.getTransactionID());
        }
        transactions.set(existingIndex, transaction);
        writeTransactions(transactions);
    }

    /** Builds one coordinated-write entry so a sale commits its transaction header with items, stock, and logs atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(transactions));
    }

    private List<Transaction> readTransactions() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Transaction> transactions = new ArrayList<>(records.size());
        Set<String> transactionIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Transaction transaction = new Transaction(
                        required(record, "transactionID"),
                        optionalText(record, "customerID"),
                        required(record, "pharmacistID"),
                        new BigDecimal(required(record, "totalAmount")),
                        TransactionStatus.valueOf(required(record, "status")),
                        LocalDateTime.parse(required(record, "dateTime")));
                if (!transactionIDs.add(transaction.getTransactionID())) {
                    throw new IllegalArgumentException("duplicate transactionID: "
                            + transaction.getTransactionID());
                }
                transactions.add(transaction);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid transaction record: " + ex.getMessage(), ex);
            }
        }
        return transactions;
    }

    private void writeTransactions(List<Transaction> transactions) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(transactions));
    }

    private List<Map<String, String>> encodeRows(List<Transaction> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        List<Map<String, String>> records = new ArrayList<>(transactions.size());
        for (Transaction transaction : transactions) {
            Objects.requireNonNull(transaction.getTotalAmount(), "transaction totalAmount must not be null");
            Objects.requireNonNull(transaction.getStatus(), "transaction status must not be null");
            Objects.requireNonNull(transaction.getDateTime(), "transaction dateTime must not be null");
            records.add(Map.of(
                    "transactionID", nonBlank(transaction.getTransactionID(), "transactionID"),
                    "customerID", transaction.getCustomerID() == null ? "" : transaction.getCustomerID(),
                    "pharmacistID", nonBlank(transaction.getPharmacistID(), "pharmacistID"),
                    "totalAmount", transaction.getTotalAmount().toPlainString(),
                    "status", transaction.getStatus().name(),
                    "dateTime", transaction.getDateTime().withNano(0).toString()));
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

    private static String nonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static int findIndexByID(List<Transaction> transactions, String transactionID) {
        for (int index = 0; index < transactions.size(); index++) {
            if (Objects.equals(transactions.get(index).getTransactionID(), transactionID)) {
                return index;
            }
        }
        return -1;
    }
}