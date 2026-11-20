package repository.account;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import model.account.Customer;
import model.account.CustomerStatus;
import repository.csv.CsvDataException;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;

public class CustomerRepository {

    private static final Path FILE_PATH = Path.of("data/runtime/accounts/customerDB.csv");
    private static final List<String> HEADERS = List.of(
            "customerID", "username", "passwordHash", "fullName", "balance", "status", "dateRegistered");
    private final CsvFileManager csvFileManager = new CsvFileManager();

    public List<Customer> findAll() {
        return readCustomers();
    }

    public Optional<Customer> findByID(String customerID) {
        if (customerID == null) {
            return Optional.empty();
        }
        return readCustomers().stream()
                .filter(customer -> customerID.equals(customer.getUserID()))
                .findFirst();
    }

    public Optional<Customer> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String normalized = username.trim();
        return readCustomers().stream()
                .filter(customer -> customer.getUsername().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public void save(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        List<Customer> customers = readCustomers();
        if (customers.stream().anyMatch(existing -> existing.getUserID().equals(customer.getUserID()))) {
            throw new IllegalArgumentException("Customer ID already exists: " + customer.getUserID());
        }
        customers.add(customer);
        writeCustomers(customers);
    }

    public void update(Customer customer) {
        Objects.requireNonNull(customer, "customer must not be null");
        List<Customer> customers = readCustomers();
        int existingIndex = findIndexByID(customers, customer.getUserID());
        if (existingIndex < 0) {
            throw new IllegalArgumentException("Cannot update unknown customer ID: " + customer.getUserID());
        }
        customers.set(existingIndex, customer);
        writeCustomers(customers);
    }

    /** Builds one coordinated-write entry so balance changes commit together with the other sale files atomically. */
    public CsvWriteCoordinator.Update asUpdate(List<Customer> customers) {
        Objects.requireNonNull(customers, "customers must not be null");
        return new CsvWriteCoordinator.Update(FILE_PATH, HEADERS, encodeRows(customers));
    }

    private List<Customer> readCustomers() {
        List<Map<String, String>> records = csvFileManager.readRecords(FILE_PATH, HEADERS);
        List<Customer> customers = new ArrayList<>(records.size());
        Set<String> customerIDs = new HashSet<>();
        for (int index = 0; index < records.size(); index++) {
            Map<String, String> record = records.get(index);
            int recordNumber = index + 1;
            try {
                Customer customer = new Customer(
                        required(record, "customerID"),
                        required(record, "username"),
                        required(record, "passwordHash"),
                        required(record, "fullName"),
                        new BigDecimal(required(record, "balance")),
                        CustomerStatus.valueOf(required(record, "status")),
                        LocalDate.parse(required(record, "dateRegistered")));
                if (!customerIDs.add(customer.getUserID())) {
                    throw new IllegalArgumentException("duplicate customerID: " + customer.getUserID());
                }
                customers.add(customer);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new CsvDataException(FILE_PATH, recordNumber,
                        "invalid customer record: " + ex.getMessage(), ex);
            }
        }
        return customers;
    }

    private void writeCustomers(List<Customer> customers) {
        csvFileManager.writeRecords(FILE_PATH, HEADERS, encodeRows(customers));
    }

    private List<Map<String, String>> encodeRows(List<Customer> customers) {
        Objects.requireNonNull(customers, "customers must not be null");
        List<Map<String, String>> records = new ArrayList<>(customers.size());
        for (Customer customer : customers) {
            Objects.requireNonNull(customer.getBalance(), "customer balance must not be null");
            Objects.requireNonNull(customer.getStatus(), "customer status must not be null");
            Objects.requireNonNull(customer.getDateRegistered(), "customer dateRegistered must not be null");
            records.add(Map.of(
                    "customerID", nonBlank(customer.getUserID(), "customerID"),
                    "username", nonBlank(customer.getUsername(), "username"),
                    "passwordHash", nonBlank(customer.getPasswordHash(), "passwordHash"),
                    "fullName", nonBlank(customer.getFullName(), "fullName"),
                    "balance", customer.getBalance().toPlainString(),
                    "status", customer.getStatus().name(),
                    "dateRegistered", customer.getDateRegistered().toString()));
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

    private static int findIndexByID(List<Customer> customers, String customerID) {
        for (int index = 0; index < customers.size(); index++) {
            if (Objects.equals(customers.get(index).getUserID(), customerID)) {
                return index;
            }
        }
        return -1;
    }
}