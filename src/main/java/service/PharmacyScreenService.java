package service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.account.CustomerStatus;
import model.account.StaffStatus;
import model.inventory.Batch;
import model.inventory.BatchStatus;
import model.inventory.Item;
import model.inventory.ItemStatus;
import model.inventory.RestockRequest;
import model.inventory.RestockRequestStatus;
import model.log.BalanceLogAction;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;
import repository.inventory.BatchRepository;
import repository.inventory.ItemRepository;
import security.PasswordHasher;

/** Read-only screen projections for the Swing pharmacy workspace. */
public final class PharmacyScreenService {

    public enum Screen {
        DASHBOARD,
        PHARMACIST_HOME,
        COUNTER_SALE,
        INVENTORY_ITEMS,
        STOCK_BATCHES,
        RESTOCK_REQUESTS,
        PRESCRIPTIONS,
        TRANSACTIONS,
        CUSTOMERS,
        PHARMACISTS,
        ADMINS,
        AUDIT_LOGS
    }

    public record Metric(String label, String value, String note) { }

    public record ScreenData(
            List<String> columns,
            List<List<String>> rows,
            List<Metric> metrics,
            String emptyTitle,
            String emptyMessage) {
        public ScreenData {
            columns = List.copyOf(columns);
            rows = rows.stream().map(List::copyOf).toList();
            metrics = List.copyOf(metrics);
        }
    }

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
    private static final List<String> ITEM_HEADERS = List.of(
            "itemID", "itemName", "category", "price", "prescriptionRequired", "status");
    private static final List<String> BATCH_HEADERS = List.of(
            "batchID", "itemID", "lotNumber", "quantity", "expirationDate", "dateReceived", "status");
    private static final List<String> RESTOCK_HEADERS = List.of(
            "requestID", "itemID", "quantityRequested", "quantityReceived", "requestedByPharmacistID",
            "dateRequested", "receivedByID", "dateReceived", "status", "details");
    private static final List<String> PHARMACIST_HEADERS = List.of(
            "pharmacistID", "username", "passwordHash", "fullName", "status", "dateRegistered", "dateApproved");
    private static final List<String> ADMIN_HEADERS = List.of(
            "adminID", "username", "passwordHash", "fullName", "status", "dateCreated");
    private static final List<String> CUSTOMER_HEADERS = List.of(
            "customerID", "username", "passwordHash", "fullName", "balance", "status", "dateRegistered");
    private static final List<String> PRESCRIPTION_HEADERS = List.of(
            "prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
            "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status");
    private static final List<String> TRANSACTION_HEADERS = List.of(
            "transactionID", "customerID", "pharmacistID", "totalAmount", "status", "dateTime");
    private static final List<String> TRANSACTION_ITEM_HEADERS = List.of(
            "transactionItemID", "transactionID", "itemID", "batchID", "prescriptionItemID",
            "quantity", "unitPrice", "subtotal");
    private static final List<String> TRANSACTION_LOG_HEADERS = List.of(
            "logID", "transactionID", "customerID", "actorID", "actorRole", "action", "dateTime",
            "reason", "outcome", "relatedRecordID", "details");
    private static final List<String> BATCH_LOG_HEADERS = List.of(
            "logID", "batchID", "itemID", "actorID", "actorRole", "action", "dateTime", "reason",
            "outcome", "relatedRecordID", "details");
    private static final List<String> INVENTORY_LOG_HEADERS = List.of(
            "logID", "batchID", "itemID", "actorID", "actorRole", "action", "quantity", "dateTime",
            "reason", "outcome", "relatedRecordID", "details");
    private static final List<String> BALANCE_LOG_HEADERS = List.of(
            "logID", "customerID", "actorID", "actorRole", "action", "amount", "balanceBefore",
            "balanceAfter", "dateTime", "reason", "outcome", "relatedRecordID", "details");
    private static final List<String> PRESCRIPTION_LOG_HEADERS = List.of(
            
            "logID", "prescriptionID", "actorID", "actorRole", "action", "dateTime",
           
            "reason", "outcome", "relatedRecordID", "details");

    private final ItemRepository itemRepository;
    private final BatchRepository batchRepository;
    private final CsvFileManager csvFileManager;
    private final CsvWriteCoordinator writeCoordinator = new CsvWriteCoordinator();
    private static final Pattern ITEM_NUMBER = Pattern.compile(".*?(\\d+)$");

    public PharmacyScreenService() {
        this(new ItemRepository(), new BatchRepository(), new CsvFileManager());
    }

    PharmacyScreenService(
            ItemRepository itemRepository,
            BatchRepository batchRepository,
            CsvFileManager csvFileManager) {
        this.itemRepository = itemRepository;
        this.batchRepository = batchRepository;
        this.csvFileManager = csvFileManager;
    }

    public ScreenData load(Screen screen) {
        return switch (screen) {
            case DASHBOARD -> loadDashboard();
            case PHARMACIST_HOME -> loadPharmacistHome();
            case COUNTER_SALE -> loadCounterSale();
            case INVENTORY_ITEMS -> loadItems();
            case STOCK_BATCHES -> loadBatches();
            case RESTOCK_REQUESTS -> loadRestockRequests();
            case PRESCRIPTIONS -> loadPrescriptions();
            case TRANSACTIONS -> loadTransactions();
            case CUSTOMERS -> loadCustomers();
            case PHARMACISTS -> loadPharmacists();
            case ADMINS -> loadAdmins();
            case AUDIT_LOGS -> loadAuditLogs();
        };
    }

    /** Saves one catalog record through the existing single-file inventory repository. */
    public void createItem(String name, String category, BigDecimal price, boolean prescriptionRequired,
            ItemStatus status) {
        if (name == null || name.isBlank() || category == null || category.isBlank()) {
            throw new IllegalArgumentException("Medicine name and category are required.");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("Price must be zero or greater.");
        }
        List<Item> items = itemRepository.findAll();
        int nextNumber = items.stream()
                .map(Item::getItemID)
                .map(ITEM_NUMBER::matcher)
                .filter(Matcher::matches)
                .mapToInt(matcher -> Integer.parseInt(matcher.group(1)))
                .max()
                .orElse(0) + 1;
        String itemID = String.format("MED-%05d", nextNumber);
        itemRepository.save(new Item(itemID, name.trim(), category.trim(), price,
                prescriptionRequired, status));
    }

    public record SaleLine(String itemID, int quantity) { }

    /** Commits a local counter sale, FEFO stock deductions, customer balance deduction, and audit rows as one recoverable write. */
    public String completeSale(String customerID, List<SaleLine> saleLines, String actorID, String actorRole) {
        if (saleLines == null || saleLines.isEmpty()) throw new IllegalArgumentException("Add at least one medicine.");
        if (actorID == null || actorID.isBlank() || actorRole == null || actorRole.isBlank())
            throw new IllegalArgumentException("A signed-in staff member is required.");
        List<Item> items = itemRepository.findAll();
        Map<String, Item> byId = new HashMap<>();
        items.forEach(item -> byId.put(item.getItemID(), item));
        for (SaleLine line : saleLines) {
            Item item = byId.get(line.itemID());
            if (line.quantity() <= 0 || item == null || item.getStatus() != ItemStatus.ACTIVE)
                throw new IllegalArgumentException("A sale item is invalid or inactive. Refresh the catalog and try again.");
            if (item.isPrescriptionRequired())
                throw new IllegalArgumentException("Prescription medicine cannot be sold until a verified prescription dispensing flow is connected.");
        }
        String customer = customerID == null || customerID.isBlank() ? "" : customerID.trim();
        List<Map<String, String>> customers = new ArrayList<>(
                read("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS));
        int customerIndex = -1;
        BigDecimal balanceBefore = BigDecimal.ZERO;
        if (!customer.isEmpty()) {
            for (int index = 0; index < customers.size(); index++) {
                Map<String, String> candidate = customers.get(index);
                if (customer.equals(candidate.get("customerID")) && "ACTIVE".equals(candidate.get("status"))) {
                    customerIndex = index;
                    break;
                }
            }
            if (customerIndex < 0) throw new IllegalArgumentException("Customer ID was not found or is inactive.");
            try {
                balanceBefore = parseMoney(customers.get(customerIndex).get("balance"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("The customer account has an invalid stored balance.");
            }
            if (balanceBefore.signum() < 0)
                throw new IllegalArgumentException("The customer account has a negative stored balance.");
        }

        List<Map<String, String>> batches = read("data/runtime/inventory/batchDB.csv", BATCH_HEADERS);
        List<Map<String, String>> transactions = new ArrayList<>(read("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS));
        List<Map<String, String>> transactionItems = new ArrayList<>(read("data/runtime/transactions/transactionItemsDB.csv", TRANSACTION_ITEM_HEADERS));
        List<Map<String, String>> transactionLogs = new ArrayList<>(read("data/runtime/logs/transactionLogs.csv", TRANSACTION_LOG_HEADERS));
        List<Map<String, String>> batchLogs = new ArrayList<>(read("data/runtime/logs/batchLogs.csv", BATCH_LOG_HEADERS));
        String transactionID = nextID(transactions, "transactionID", "TXN-");
        String now = LocalDateTime.now().withNano(0).toString();
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, String>> changedBatches = new ArrayList<>(batches);
        for (SaleLine line : saleLines) {
            Item item = byId.get(line.itemID());
            List<Map<String, String>> eligible = changedBatches.stream()
                    .filter(row -> line.itemID().equals(row.get("itemID")))
                    .filter(row -> "ACTIVE".equals(row.get("status")))
                    .filter(row -> LocalDate.parse(row.get("expirationDate")).compareTo(LocalDate.now()) >= 0)
                    .filter(row -> Integer.parseInt(row.get("quantity")) > 0)
                    .sorted(Comparator.comparing(row -> row.get("expirationDate")))
                    .toList();
            int available = eligible.stream().mapToInt(row -> Integer.parseInt(row.get("quantity"))).sum();
            if (available < line.quantity()) throw new IllegalArgumentException("Not enough eligible stock for " + item.getItemName() + ". Refresh and try again.");
            int remaining = line.quantity();
            for (Map<String, String> batch : eligible) {
                if (remaining == 0) break;
                int oldQuantity = Integer.parseInt(batch.get("quantity"));
                int deducted = Math.min(oldQuantity, remaining);
                remaining -= deducted;
                Map<String, String> updated = new java.util.LinkedHashMap<>(batch);
                updated.put("quantity", Integer.toString(oldQuantity - deducted));
                if (oldQuantity == deducted) updated.put("status", "DEPLETED");
                changedBatches.set(changedBatches.indexOf(batch), updated);
                BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(deducted));
                total = total.add(subtotal);
                transactionItems.add(row(TRANSACTION_ITEM_HEADERS,
                        "transactionItemID", nextID(transactionItems, "transactionItemID", "TXI-"),
                        "transactionID", transactionID, "itemID", item.getItemID(), "batchID", batch.get("batchID"),
                        "prescriptionItemID", "", "quantity", Integer.toString(deducted),
                        "unitPrice", item.getPrice().toPlainString(), "subtotal", subtotal.toPlainString()));
                batchLogs.add(row(BATCH_LOG_HEADERS,
                        "logID", nextID(batchLogs, "logID", "LOG-"), "batchID", batch.get("batchID"),
                        "itemID", item.getItemID(), "actorID", actorID, "actorRole", actorRole,
                        "action", "SALE_DEDUCTION", "dateTime", now, "reason", "Counter sale",
                        "outcome", "SUCCESS", "relatedRecordID", transactionID,
                        "details", "Deducted " + deducted + " unit(s) using FEFO."));
            }
        }
        if (customerIndex >= 0 && balanceBefore.compareTo(total) < 0) {
            throw new IllegalArgumentException("Insufficient customer balance. Available "
                    + money(balanceBefore) + " but the sale total is " + money(total) + ".");
        }
        transactions.add(row(TRANSACTION_HEADERS,
                "transactionID", transactionID, "customerID", customer, "pharmacistID", actorID,
                "totalAmount", total.toPlainString(), "status", "COMPLETED", "dateTime", now));
        transactionLogs.add(row(TRANSACTION_LOG_HEADERS,
                "logID", nextID(transactionLogs, "logID", "LOG-"), "transactionID", transactionID,
                "customerID", customer, "actorID", actorID, "actorRole", actorRole,
                "action", "SALE_COMPLETED", "dateTime", now, "reason", "Counter sale",
                "outcome", "SUCCESS", "relatedRecordID", transactionID, "details", "Sale persisted to local CSV storage."));
        List<CsvWriteCoordinator.Update> updates = new ArrayList<>();
        updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/inventory/batchDB.csv"), BATCH_HEADERS, changedBatches));
        updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/transactions/transactionsDB.csv"), TRANSACTION_HEADERS, transactions));
        updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/transactions/transactionItemsDB.csv"), TRANSACTION_ITEM_HEADERS, transactionItems));
        updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/batchLogs.csv"), BATCH_LOG_HEADERS, batchLogs));
        updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/transactionLogs.csv"), TRANSACTION_LOG_HEADERS, transactionLogs));
        if (customerIndex >= 0) {
            BigDecimal balanceAfter = balanceBefore.subtract(total);
            Map<String, String> customerRow = new java.util.LinkedHashMap<>(customers.get(customerIndex));
            customerRow.put("balance", balanceAfter.toPlainString());
            customers.set(customerIndex, customerRow);
            List<Map<String, String>> balanceLogs = new ArrayList<>(
                    read("data/runtime/logs/balanceLogs.csv", BALANCE_LOG_HEADERS));
            balanceLogs.add(row(BALANCE_LOG_HEADERS,
                    "logID", nextID(balanceLogs, "logID", "LOG-"), "customerID", customer,
                    "actorID", actorID, "actorRole", actorRole, "action", BalanceLogAction.PURCHASE.name(),
                    "amount", total.toPlainString(),
                    "balanceBefore", balanceBefore.toPlainString(),
                    "balanceAfter", balanceAfter.toPlainString(),
                    "dateTime", now, "reason", "Counter sale", "outcome", "SUCCESS",
                    "relatedRecordID", transactionID,
                    "details", "Deducted the sale total from the customer balance."));
            updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/accounts/customerDB.csv"), CUSTOMER_HEADERS, customers));
            updates.add(new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/balanceLogs.csv"), BALANCE_LOG_HEADERS, balanceLogs));
        }
        writeCoordinator.commit(updates);
        return transactionID;
    }

    public String createRestockRequest(String itemID, int quantity, String reason, String pharmacistID) {
        if (quantity <= 0) throw new IllegalArgumentException("Requested quantity must be greater than zero.");
        if (itemRepository.findByID(itemID).filter(item -> item.getStatus() == ItemStatus.ACTIVE).isEmpty())
            throw new IllegalArgumentException("Select an active medicine from the catalog.");
        if (pharmacistID == null || pharmacistID.isBlank()) throw new IllegalArgumentException("A signed-in pharmacist is required.");
        List<Map<String, String>> requests = new ArrayList<>(read("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS));
        String id = nextID(requests, "requestID", "RST-");
        String now = LocalDate.now().toString();
        requests.add(row(RESTOCK_HEADERS, "requestID", id, "itemID", itemID, "quantityRequested", Integer.toString(quantity),
                "quantityReceived", "0", "requestedByPharmacistID", pharmacistID, "dateRequested", now,
                "receivedByID", "", "dateReceived", "", "status", "REQUESTED", "details", reason == null ? "" : reason.trim()));
        List<Map<String, String>> inventoryLogs = new ArrayList<>(read("data/runtime/logs/inventoryLogs.csv", INVENTORY_LOG_HEADERS));
        inventoryLogs.add(row(INVENTORY_LOG_HEADERS,
                "logID", nextID(inventoryLogs, "logID", "LOG-"), "batchID", "", "itemID", itemID,
                "actorID", pharmacistID, "actorRole", "PHARMACIST", "action", "RESTOCK_REQUESTED",
                "quantity", Integer.toString(quantity), "dateTime", LocalDateTime.now().withNano(0).toString(),
                "reason", reason == null ? "" : reason.trim(), "outcome", "SUCCESS", "relatedRecordID", id,
                "details", "Restock request saved."));
        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/inventory/restockRequests.csv"), RESTOCK_HEADERS, requests),
                new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/inventoryLogs.csv"), INVENTORY_LOG_HEADERS, inventoryLogs)));
        return id;
    }

    /** Approves a pending request and records the physically received stock as a new batch. */
    public String approveRestockRequest(String requestID, String adminID, int quantityReceived,
            String lotNumber, LocalDate expirationDate) {
        if (requestID == null || requestID.isBlank()) throw new IllegalArgumentException("Select a restock request first.");
        if (adminID == null || adminID.isBlank()) throw new IllegalArgumentException("A signed-in administrator is required.");
        if (lotNumber == null || lotNumber.isBlank()) throw new IllegalArgumentException("Supplier lot number is required.");
        if (expirationDate == null || expirationDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Enter an expiration date that is today or later.");
        List<Map<String, String>> admins = read("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS);
        if (admins.stream().noneMatch(row -> adminID.equals(row.get("adminID")) && "ACTIVE".equals(row.get("status")))) {
            throw new IllegalArgumentException("Only an active administrator can approve restock requests.");
        }

        List<Map<String, String>> requests = new ArrayList<>(read("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS));
        int requestIndex = -1;
        for (int index = 0; index < requests.size(); index++) {
            if (requestID.equals(requests.get(index).get("requestID"))) {
                requestIndex = index;
                break;
            }
        }
        if (requestIndex < 0) throw new IllegalArgumentException("That restock request no longer exists. Refresh the list.");
        Map<String, String> requestRow = new java.util.LinkedHashMap<>(requests.get(requestIndex));
        RestockRequest request;
        try {
            String previousReceiver = requestRow.get("receivedByID");
            String previousDate = requestRow.get("dateReceived");
            request = new RestockRequest(
                    requestID,
                    requestRow.get("itemID"),
                    Integer.parseInt(requestRow.get("quantityRequested")),
                    Integer.parseInt(requestRow.get("quantityReceived")),
                    requestRow.get("requestedByPharmacistID"),
                    LocalDate.parse(requestRow.get("dateRequested")),
                    previousReceiver == null || previousReceiver.isBlank() ? null : previousReceiver,
                    previousDate == null || previousDate.isBlank() ? null : LocalDate.parse(previousDate),
                    RestockRequestStatus.valueOf(requestRow.get("status")),
                    requestRow.get("details"));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("The selected restock request contains invalid data.", ex);
        }
        boolean approvingNow = request.getStatus() == RestockRequestStatus.REQUESTED;
        if (approvingNow) request = request.approve();
        if (request.getStatus() != RestockRequestStatus.APPROVED
                && request.getStatus() != RestockRequestStatus.PARTIALLY_RECEIVED) {
            throw new IllegalArgumentException("Only pending, approved, or partially received requests can receive stock.");
        }
        Item item = itemRepository.findByID(request.getItemID())
                .filter(candidate -> candidate.getStatus() == ItemStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("The requested medicine is missing or inactive."));
        RestockRequest received = request.recordReceipt(quantityReceived, adminID, LocalDate.now());
        String batchID;
        List<Map<String, String>> batches = new ArrayList<>(read("data/runtime/inventory/batchDB.csv", BATCH_HEADERS));
        batchID = nextID(batches, "batchID", "BAT-");
        batches.add(row(BATCH_HEADERS,
                "batchID", batchID,
                "itemID", item.getItemID(),
                "lotNumber", lotNumber.trim(),
                "quantity", Integer.toString(quantityReceived),
                "expirationDate", expirationDate.toString(),
                "dateReceived", LocalDate.now().toString(),
                "status", "ACTIVE"));
        requestRow.put("quantityReceived", Integer.toString(received.getQuantityReceived()));
        requestRow.put("receivedByID", adminID);
        requestRow.put("dateReceived", received.getDateReceived().toString());
        requestRow.put("status", received.getStatus().name());
        requests.set(requestIndex, requestRow);

        List<Map<String, String>> inventoryLogs = new ArrayList<>(read("data/runtime/logs/inventoryLogs.csv", INVENTORY_LOG_HEADERS));
        String timestamp = LocalDateTime.now().withNano(0).toString();
        if (approvingNow) {
            inventoryLogs.add(row(INVENTORY_LOG_HEADERS,
                    "logID", nextID(inventoryLogs, "logID", "LOG-"), "batchID", "", "itemID", item.getItemID(),
                    "actorID", adminID, "actorRole", "ADMIN", "action", "RESTOCK_APPROVED",
                    "quantity", Integer.toString(request.getQuantityRequested()), "dateTime", timestamp,
                    "reason", "Administrator approval", "outcome", "SUCCESS", "relatedRecordID", requestID,
                    "details", "Approved and recorded the received quantity as batch " + batchID + "."));
        }
        inventoryLogs.add(row(INVENTORY_LOG_HEADERS,
                "logID", nextID(inventoryLogs, "logID", "LOG-"), "batchID", batchID, "itemID", item.getItemID(),
                "actorID", adminID, "actorRole", "ADMIN", "action", "RESTOCK_RECEIVED",
                "quantity", Integer.toString(quantityReceived), "dateTime", timestamp,
                "reason", "Restock receipt", "outcome", "SUCCESS", "relatedRecordID", requestID,
                "details", "Lot " + lotNumber.trim() + "; expires " + expirationDate + "."));
        List<Map<String, String>> batchLogs = new ArrayList<>(read("data/runtime/logs/batchLogs.csv", BATCH_LOG_HEADERS));
        batchLogs.add(row(BATCH_LOG_HEADERS,
                "logID", nextID(batchLogs, "logID", "LOG-"), "batchID", batchID, "itemID", item.getItemID(),
                "actorID", adminID, "actorRole", "ADMIN", "action", "CREATE", "dateTime", timestamp,
                "reason", "Approved restock receipt", "outcome", "SUCCESS", "relatedRecordID", requestID,
                "details", "Received " + quantityReceived + " unit(s); lot " + lotNumber.trim()
                        + "; expiration " + expirationDate + "."));
        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/inventory/restockRequests.csv"), RESTOCK_HEADERS, requests),
                new CsvWriteCoordinator.Update(Path.of("data/runtime/inventory/batchDB.csv"), BATCH_HEADERS, batches),
                new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/inventoryLogs.csv"), INVENTORY_LOG_HEADERS, inventoryLogs),
                new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/batchLogs.csv"), BATCH_LOG_HEADERS, batchLogs)));
        return batchID;
    }

    public String reviewPrescription(
            String prescriptionID,
            String newStatus,
            String notes,
            String actorID,
            String actorRole) {
        if (prescriptionID == null || prescriptionID.isBlank()) {
            throw new IllegalArgumentException("Prescription ID is required.");
        }
        Path rxPath = Path.of("data/runtime/prescriptions/prescriptionsDB.csv");
        Path rxLogPath = Path.of("data/runtime/logs/prescriptionLogs.csv");

        List<Map<String, String>> prescriptions = new ArrayList<>(read("data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS));
        List<Map<String, String>> rxLogs = new ArrayList<>(read("data/runtime/logs/prescriptionLogs.csv", PRESCRIPTION_LOG_HEADERS));

        boolean found = false;
        for (int i = 0; i < prescriptions.size(); i++) {
            Map<String, String> row = new java.util.LinkedHashMap<>(prescriptions.get(i));
            if (prescriptionID.trim().equals(row.get("prescriptionID"))) {
                found = true;
                String validUntilStr = row.getOrDefault("validUntil", "");
                if ("VERIFIED".equalsIgnoreCase(newStatus) && !validUntilStr.isBlank()) {
                    LocalDate validUntil = LocalDate.parse(validUntilStr);
                    if (validUntil.isBefore(LocalDate.now())) {
                        throw new IllegalStateException("Cannot verify expired prescription (validUntil: " + validUntilStr + ").");
                    }
                }
                row.put("status", newStatus.toUpperCase());
                prescriptions.set(i, row);
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Prescription not found: " + prescriptionID);
        }

        String action = switch (newStatus.toUpperCase()) {
            case "VERIFIED" -> "VERIFIED";
            case "NEEDS_CLARIFICATION" -> "CLARIFICATION_REQUESTED";
            case "REJECTED" -> "REJECT";
            default -> "VERIFIED";
        };

        rxLogs.add(row(PRESCRIPTION_LOG_HEADERS,
                "logID", nextID(rxLogs, "logID", "LOG-"),
                "prescriptionID", prescriptionID.trim(),
                "actorID", actorID,
                "actorRole", actorRole,
                "action", action,
                "dateTime", LocalDateTime.now().withNano(0).toString(),
                "reason", notes == null || notes.isBlank() ? "Reviewed at counter" : notes.trim(),
                "outcome", "SUCCESS",
                "relatedRecordID", "",
                "details", "Status updated to " + newStatus.toUpperCase()));

        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(rxPath, PRESCRIPTION_HEADERS, prescriptions),
                new CsvWriteCoordinator.Update(rxLogPath, PRESCRIPTION_LOG_HEADERS, rxLogs)));

        return prescriptionID.trim() + " -> " + newStatus.toUpperCase();
    }

    public String voidTransaction(String transactionID, String reason, String actorID, String actorRole) {
        if (transactionID == null || transactionID.isBlank()) {
            throw new IllegalArgumentException("Transaction ID is required.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A void reason is required for audit records.");
        }

        Path txnPath = Path.of("data/runtime/transactions/transactionsDB.csv");
        Path txnLogPath = Path.of("data/runtime/logs/transactionLogs.csv");

        List<Map<String, String>> transactions = new ArrayList<>(read("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS));
        List<Map<String, String>> txnLogs = new ArrayList<>(read("data/runtime/logs/transactionLogs.csv", TRANSACTION_LOG_HEADERS));

        boolean found = false;
        String customerID = "";
        for (int i = 0; i < transactions.size(); i++) {
            Map<String, String> row = new java.util.LinkedHashMap<>(transactions.get(i));
            if (transactionID.trim().equals(row.get("transactionID"))) {
                found = true;
                if ("CANCELLED".equalsIgnoreCase(row.get("status"))) {
                    throw new IllegalStateException("Transaction " + transactionID + " is already CANCELLED.");
                }
                row.put("status", "CANCELLED");
                transactions.set(i, row);
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Transaction not found: " + transactionID);
        }

        txnLogs.add(row(TRANSACTION_LOG_HEADERS,
                "logID", nextID(txnLogs, "logID", "LOG-"),
                "transactionID", transactionID.trim(),
                "customerID", customerID,
                "actorID", actorID,
                "actorRole", actorRole,
                "action", "VOID_REVERSED",
                "dateTime", LocalDateTime.now().withNano(0).toString(),
                "reason", reason.trim(),
                "outcome", "SUCCESS",
                "relatedRecordID", transactionID.trim(),
                "details", "Sale voided; original transaction preserved as CANCELLED"));

        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(txnPath, TRANSACTION_HEADERS, transactions),
                new CsvWriteCoordinator.Update(txnLogPath, TRANSACTION_LOG_HEADERS, txnLogs)));

        return transactionID.trim();
    }

    public String approvePharmacist(String pharmacistID, String adminID) {
        if (pharmacistID == null || pharmacistID.isBlank()) {
            throw new IllegalArgumentException("Pharmacist ID is required.");
        }
        if (adminID == null || adminID.isBlank()) {
            throw new IllegalArgumentException("Administrator ID is required.");
        }
        List<Map<String, String>> pharmacists = new ArrayList<>(
                read("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS));
        boolean found = false;
        String approvedName = "";

        for (Map<String, String> row : pharmacists) {
            if (pharmacistID.trim().equals(row.get("pharmacistID"))) {
                found = true;
                String currentStatus = row.getOrDefault("status", "");
                if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Pharmacist " + pharmacistID + " is already " + currentStatus + ".");
                }
                row.put("status", StaffStatus.ACTIVE.name());
                row.put("dateApproved", java.time.LocalDate.now().toString());
                approvedName = row.getOrDefault("fullName", pharmacistID);
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Pharmacist record not found: " + pharmacistID);
        }

        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/accounts/pharmacistDB.csv"),
                        PHARMACIST_HEADERS, pharmacists)));
        return approvedName;
    }

    /** Registers a walk-in customer at the counter with a zero starting balance. */
    public String registerCustomer(String username, char[] password, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("The customer's full name is required.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A username is required.");
        }
        String normalizedUsername = username.trim().toLowerCase();
        if (!normalizedUsername.matches("[a-z0-9._-]{4,24}")) {
            throw new IllegalArgumentException(
                    "Usernames use 4-24 characters: letters, numbers, dots, dashes, or underscores.");
        }
        if (password == null || password.length < 8) {
            throw new IllegalArgumentException("Passwords must be at least 8 characters.");
        }
        List<Map<String, String>> customers = new ArrayList<>(
                read("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS));
        boolean usernameTaken = customers.stream()
                .anyMatch(existing -> normalizedUsername.equalsIgnoreCase(
                        existing.getOrDefault("username", "").trim()));
        if (usernameTaken) {
            throw new IllegalArgumentException("That username is already taken. Choose another one.");
        }
        String customerID = nextID(customers, "customerID", "CUS-");
        String passwordHash = PasswordHasher.hash(password);
        java.util.Arrays.fill(password, '\0');
        customers.add(row(CUSTOMER_HEADERS,
                "customerID", customerID, "username", normalizedUsername, "passwordHash", passwordHash,
                "fullName", fullName.trim(), "balance", "0.00", "status", CustomerStatus.ACTIVE.name(),
                "dateRegistered", LocalDate.now().toString()));
        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/accounts/customerDB.csv"),
                        CUSTOMER_HEADERS, customers)));
        return customerID;
    }

    /** Adds funds to a customer account at the counter and records a TOP_UP audit row. */
    public BigDecimal topUpCustomerBalance(String customerID, BigDecimal amount,
            String actorID, String actorRole) {
        if (actorID == null || actorID.isBlank() || actorRole == null || actorRole.isBlank()) {
            throw new IllegalArgumentException("A signed-in staff member is required.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Top-up amount must be greater than zero.");
        }
        List<Map<String, String>> customers = new ArrayList<>(
                read("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS));
        int customerIndex = -1;
        for (int index = 0; index < customers.size(); index++) {
            if (customerID != null && customerID.trim().equals(customers.get(index).get("customerID"))) {
                customerIndex = index;
                break;
            }
        }
        if (customerIndex < 0) {
            throw new IllegalArgumentException("Customer ID was not found. Refresh the list.");
        }
        Map<String, String> customerRow = new java.util.LinkedHashMap<>(customers.get(customerIndex));
        if (!"ACTIVE".equals(customerRow.get("status"))) {
            throw new IllegalArgumentException("Only active customer accounts can receive top-ups.");
        }
        BigDecimal balanceBefore;
        try {
            balanceBefore = parseMoney(customerRow.get("balance"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("The customer account has an invalid stored balance.");
        }
        BigDecimal balanceAfter = balanceBefore.add(amount);
        customerRow.put("balance", balanceAfter.toPlainString());
        customers.set(customerIndex, customerRow);
        List<Map<String, String>> balanceLogs = new ArrayList<>(
                read("data/runtime/logs/balanceLogs.csv", BALANCE_LOG_HEADERS));
        balanceLogs.add(row(BALANCE_LOG_HEADERS,
                "logID", nextID(balanceLogs, "logID", "LOG-"), "customerID", customerID.trim(),
                "actorID", actorID, "actorRole", actorRole,
                "action", BalanceLogAction.TOP_UP.name(), "amount", amount.toPlainString(),
                "balanceBefore", balanceBefore.toPlainString(),
                "balanceAfter", balanceAfter.toPlainString(),
                "dateTime", LocalDateTime.now().withNano(0).toString(),
                "reason", "Counter top-up", "outcome", "SUCCESS", "relatedRecordID", customerID.trim(),
                "details", "Added funds to the customer balance at the counter."));
        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/accounts/customerDB.csv"),
                        CUSTOMER_HEADERS, customers),
                new CsvWriteCoordinator.Update(Path.of("data/runtime/logs/balanceLogs.csv"),
                        BALANCE_LOG_HEADERS, balanceLogs)));
        return balanceAfter;
    }

    /** Rejects a pending pharmacist application so the account can never sign in. */
    public String rejectPharmacist(String pharmacistID, String adminID) {
        if (pharmacistID == null || pharmacistID.isBlank()) {
            throw new IllegalArgumentException("Pharmacist ID is required.");
        }
        if (adminID == null || adminID.isBlank()) {
            throw new IllegalArgumentException("Administrator ID is required.");
        }
        List<Map<String, String>> pharmacists = new ArrayList<>(
                read("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS));
        boolean found = false;
        String rejectedName = "";
        for (Map<String, String> pharmacistRow : pharmacists) {
            if (pharmacistID.trim().equals(pharmacistRow.get("pharmacistID"))) {
                found = true;
                String currentStatus = pharmacistRow.getOrDefault("status", "");
                if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                    throw new IllegalStateException("Pharmacist " + pharmacistID + " is already " + currentStatus + ".");
                }
                pharmacistRow.put("status", StaffStatus.REJECTED.name());
                rejectedName = pharmacistRow.getOrDefault("fullName", pharmacistID);
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Pharmacist record not found: " + pharmacistID);
        }
        writeCoordinator.commit(List.of(
                new CsvWriteCoordinator.Update(Path.of("data/runtime/accounts/pharmacistDB.csv"),
                        PHARMACIST_HEADERS, pharmacists)));
        return rejectedName;
    }

    private static String nextID(List<Map<String, String>> rows, String field, String prefix) {
        int max = rows.stream().map(row -> row.get(field)).filter(java.util.Objects::nonNull)
                .map(ITEM_NUMBER::matcher).filter(Matcher::matches).mapToInt(matcher -> Integer.parseInt(matcher.group(1))).max().orElse(0);
        return String.format("%s%05d", prefix, max + 1);
    }

    private static Map<String, String> row(List<String> headers, String... keyValues) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) result.put(keyValues[index], keyValues[index + 1]);
        if (!result.keySet().equals(new java.util.LinkedHashSet<>(headers))) throw new IllegalArgumentException("Incomplete CSV row.");
        return result;
    }

    private ScreenData loadDashboard() {
        List<Item> items = itemRepository.findAll();
        List<Batch> batches = batchRepository.findAll();
        List<Map<String, String>> requests = read("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS);
        List<Map<String, String>> pharmacists = read("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS);
        ScreenData logs = loadAuditLogs();
        long activeItems = items.stream().filter(item -> item.getStatus() == ItemStatus.ACTIVE).count();
        long pendingPharmacists = pharmacists.stream().filter(row -> "PENDING".equals(row.get("status"))).count();
        long openRequests = requests.stream().filter(row -> isOpenRestock(row.get("status"))).count();
        long expiringSoon = batches.stream().filter(batch -> isSellable(batch, items))
                .filter(batch -> !batch.getExpirationDate().isAfter(LocalDate.now().plusDays(30))).count();
        List<List<String>> activity = logs.rows().stream().limit(6).toList();
        return new ScreenData(
                List.of("TIME", "ACTOR", "ACTION", "REASON", "RELATED RECORD", "OUTCOME"),
                activity,
                List.of(
                        new Metric("ACTIVE MEDICINES", Long.toString(activeItems), "available in catalog"),
                        new Metric("PENDING PHARMACISTS", Long.toString(pendingPharmacists), "awaiting review"),
                        new Metric("OPEN RESTOCK REQUESTS", Long.toString(openRequests), "need follow-up"),
                        new Metric("EXPIRING BATCHES", Long.toString(expiringSoon), "within 30 days")),
                "No activity recorded yet",
                "Recent account, inventory, prescription, and transaction events will appear here.");
    }

    private ScreenData loadPharmacistHome() {
        List<Map<String, String>> transactions = read("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS);
        List<Map<String, String>> prescriptions = read(
                "data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS);
        List<Map<String, String>> requests = read("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS);
        List<Item> items = itemRepository.findAll();
        ScreenData logs = loadAuditLogs();
        long todaySales = transactions.stream()
                .filter(row -> "COMPLETED".equals(row.get("status")))
                .filter(row -> row.getOrDefault("dateTime", "").startsWith(LocalDate.now().toString()))
                .count();
        long prescriptionReview = prescriptions.stream()
                .filter(row -> "RECEIVED".equals(row.get("status"))
                        || "NEEDS_CLARIFICATION".equals(row.get("status")))
                .count();
        long openRequests = requests.stream().filter(row -> isOpenRestock(row.get("status"))).count();
        long activeItems = items.stream().filter(item -> item.getStatus() == ItemStatus.ACTIVE).count();
        return new ScreenData(
                logs.columns(), logs.rows().stream().limit(6).toList(),
                List.of(
                        new Metric("SALES TODAY", Long.toString(todaySales), "completed transactions"),
                        new Metric("PRESCRIPTIONS TO REVIEW", Long.toString(prescriptionReview), "need pharmacist review"),
                        new Metric("OPEN RESTOCK REQUESTS", Long.toString(openRequests), "submitted by the team"),
                        new Metric("ACTIVE MEDICINES", Long.toString(activeItems), "in the catalog")),
                "No recent activity",
                "Completed sales and pharmacy actions will appear here.");
    }

    private ScreenData loadCounterSale() {
        List<Batch> batches = batchRepository.findAll();
        List<List<String>> rows = itemRepository.findAll().stream()
                .filter(item -> item.getStatus() == ItemStatus.ACTIVE)
                .sorted(Comparator.comparing(Item::getItemName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> {
                    int available = batches.stream()
                            .filter(batch -> item.getItemID().equals(batch.getItemID()))
                            .filter(batch -> batch.isAvailableOn(LocalDate.now()))
                            .mapToInt(Batch::getQuantity)
                            .sum();
                    return List.of(
                            item.getItemName(), item.getCategory(), item.getItemID(), Integer.toString(available),
                            money(item.getPrice()), item.isPrescriptionRequired() ? "Prescription" : "OTC");
                })
                .toList();
        return new ScreenData(
                List.of("MEDICINE", "CATEGORY", "ITEM ID", "AVAILABLE", "UNIT PRICE", "TYPE"), rows,
                List.of(), "No active medicines available", "Active catalog items will appear here when stock is available.");
    }

    private ScreenData loadItems() {
        List<Item> items = itemRepository.findAll();
        List<List<String>> rows = items.stream()
                .sorted(Comparator.comparing(Item::getItemName, String.CASE_INSENSITIVE_ORDER))
                .map(item -> List.of(
                        item.getItemName(), item.getCategory(), item.getItemID(),
                        item.isPrescriptionRequired() ? "Prescription" : "OTC",
                        money(item.getPrice()), displayStatus(item.getStatus().name())))
                .toList();
        long active = items.stream().filter(item -> item.getStatus() == ItemStatus.ACTIVE).count();
        long prescriptionOnly = items.stream().filter(Item::isPrescriptionRequired).count();
        return new ScreenData(
                List.of("MEDICINE", "CATEGORY", "ITEM ID", "PRESCRIPTION", "PRICE", "STATUS"), rows,
                List.of(
                        new Metric("ACTIVE MEDICINES", Long.toString(active), "in the catalog"),
                        new Metric("PRESCRIPTION REQUIRED", Long.toString(prescriptionOnly), "items"),
                        new Metric("TOTAL MEDICINES", Integer.toString(items.size()), "all statuses")),
                "No medicines in the catalog",
                "Catalog items will appear after they are added to the pharmacy data.");
    }

    private ScreenData loadBatches() {
        List<Item> items = itemRepository.findAll();
        Map<String, Item> itemsById = new HashMap<>();
        for (Item item : items) {
            itemsById.put(item.getItemID(), item);
        }
        List<Batch> batches = batchRepository.findAll();
        batches.sort(Comparator.comparing(Batch::getExpirationDate));
        LocalDate today = LocalDate.now();
        List<List<String>> rows = new ArrayList<>();
        long eligible = 0;
        long blocked = 0;
        long expiring = 0;
        for (Batch batch : batches) {
            Item item = itemsById.get(batch.getItemID());
            boolean sellable = item != null && item.getStatus() == ItemStatus.ACTIVE && batch.isAvailableOn(today);
            if (sellable) {
                eligible++;
                if (!batch.getExpirationDate().isAfter(today.plusDays(30))) {
                    expiring++;
                }
            } else {
                blocked++;
            }
            String status = batch.getExpirationDate().isBefore(today)
                    ? "Expired" : displayStatus(batch.getStatus().name());
            rows.add(List.of(
                    item == null ? batch.getItemID() : item.getItemName(),
                    batch.getLotNumber(), formatDate(batch.getExpirationDate()),
                    formatDate(batch.getDateReceived()), Integer.toString(batch.getQuantity()),
                    status, sellable ? "Sellable" : "Not sellable"));
        }
        return new ScreenData(
                List.of("MEDICINE", "LOT NUMBER", "EXPIRATION", "DATE RECEIVED", "QUANTITY", "STATUS", "SELLABILITY"),
                rows,
                List.of(
                        new Metric("ELIGIBLE BATCHES", Long.toString(eligible), "ordered by earliest expiry"),
                        new Metric("EXPIRING IN 30 DAYS", Long.toString(expiring), "eligible stock"),
                        new Metric("BLOCKED BATCHES", Long.toString(blocked), "unavailable for sale")),
                "No stock batches found",
                "Batches appear here with their lot, quantity, and required expiration date.");
    }

    private ScreenData loadRestockRequests() {
        List<Map<String, String>> requests = read("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS);
        Map<String, String> itemNames = itemNameMap();
        List<List<String>> rows = requests.stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("dateRequested", ""),
                        Comparator.reverseOrder()))
                .map(row -> List.of(
                        safe(row.get("requestID")), itemNames.getOrDefault(row.get("itemID"), safe(row.get("itemID"))),
                        safe(row.get("quantityRequested")), safe(row.get("quantityReceived")),
                        safe(row.get("requestedByPharmacistID")), safe(row.get("dateRequested")),
                        displayStatus(row.get("status")), safe(row.get("details"))))
                .toList();
        long open = requests.stream().filter(row -> isOpenRestock(row.get("status"))).count();
        long partial = requests.stream().filter(row -> "PARTIALLY_RECEIVED".equals(row.get("status"))).count();
        long received = requests.stream().filter(row -> "RECEIVED".equals(row.get("status"))).count();
        return new ScreenData(
                List.of("REQUEST", "MEDICINE", "REQUESTED", "RECEIVED", "REQUESTED BY", "DATE", "STATUS", "DETAILS"),
                rows,
                List.of(
                        new Metric("OPEN REQUESTS", Long.toString(open), "awaiting receipt"),
                        new Metric("PARTIALLY RECEIVED", Long.toString(partial), "remaining quantity open"),
                        new Metric("RECEIVED", Long.toString(received), "completed requests")),
                "No restock requests yet",
                "New requests will appear here with their requested and received quantities.");
    }

    private ScreenData loadPrescriptions() {
        List<Map<String, String>> records = read("data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS);
        List<List<String>> rows = records.stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("dateReceived", ""),
                        Comparator.reverseOrder()))
                .map(row -> List.of(
                        safe(row.get("prescriptionID")), safe(row.get("patientName")), safe(row.get("prescriberName")),
                        safe(row.get("sourceType")), safe(row.get("validUntil")), displayStatus(row.get("status"))))
                .toList();
        return new ScreenData(
                List.of("PRESCRIPTION", "PATIENT", "PRESCRIBER", "SOURCE", "VALID UNTIL", "STATUS"), rows,
                List.of(new Metric("PRESCRIPTION RECORDS", Integer.toString(records.size()), "externally issued")),
                "No prescription records",
                "Prescription records will appear here. Original prescription documents stay outside the repository.");
    }

    private ScreenData loadTransactions() {
        List<Map<String, String>> records = read("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS);
        List<List<String>> rows = records.stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("dateTime", ""),
                        Comparator.reverseOrder()))
                .map(row -> List.of(
                        safe(row.get("transactionID")), safe(row.get("customerID")), safe(row.get("pharmacistID")),
                        money(parseMoney(row.get("totalAmount"))), displayStatus(row.get("status")), safe(row.get("dateTime"))))
                .toList();
        long completed = records.stream().filter(row -> "COMPLETED".equals(row.get("status"))).count();
        return new ScreenData(
                List.of("TRANSACTION", "CUSTOMER", "PHARMACIST", "TOTAL", "STATUS", "DATE & TIME"), rows,
                List.of(new Metric("TRANSACTIONS", Integer.toString(records.size()), "all records"),
                        new Metric("COMPLETED", Long.toString(completed), "preserved in history")),
                "No transactions yet",
                "Completed and cancelled transactions will appear here.");
    }

    private ScreenData loadCustomers() {
        List<Map<String, String>> records = read("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS);
        List<List<String>> rows = records.stream()
                .map(row -> List.of(
                        safe(row.get("customerID")), safe(row.get("fullName")), safe(row.get("username")),
                        money(parseMoney(row.get("balance"))), displayStatus(row.get("status")), safe(row.get("dateRegistered"))))
                .toList();
        return new ScreenData(
                List.of("CUSTOMER ID", "CUSTOMER", "USERNAME", "BALANCE", "STATUS", "REGISTERED"), rows,
                List.of(new Metric("CUSTOMER ACCOUNTS", Integer.toString(records.size()), "account records")),
                "No customer accounts",
                "Customer accounts will appear here. Password data is never shown.");
    }

    private ScreenData loadPharmacists() {
        List<Map<String, String>> records = read("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS);
        List<List<String>> rows = records.stream()
                .map(row -> List.of(
                        safe(row.get("pharmacistID")), safe(row.get("fullName")), safe(row.get("username")),
                        displayStatus(row.get("status")), safe(row.get("dateRegistered")), safe(row.get("dateApproved"))))
                .toList();
        long pending = records.stream().filter(row -> "PENDING".equals(row.get("status"))).count();
        long active = records.stream().filter(row -> "ACTIVE".equals(row.get("status"))).count();
        return new ScreenData(
                List.of("PHARMACIST ID", "PHARMACIST", "USERNAME", "STATUS", "REGISTERED", "APPROVED DATE"), rows,
                List.of(new Metric("PENDING REVIEW", Long.toString(pending), "administrator review"),
                        new Metric("ACTIVE", Long.toString(active), "approved access")),
                "No pharmacist accounts",
                "Newly registered pharmacist accounts will appear here for administrator review.");
    }

    private ScreenData loadAdmins() {
        List<Map<String, String>> records = read("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS);
        List<List<String>> rows = records.stream()
                .map(row -> List.of(
                        safe(row.get("adminID")), safe(row.get("fullName")), safe(row.get("username")),
                        displayStatus(row.get("status")), safe(row.get("dateCreated"))))
                .toList();
        return new ScreenData(
                List.of("ADMIN ID", "ADMINISTRATOR", "USERNAME", "STATUS", "DATE CREATED"), rows,
                List.of(new Metric("ADMIN ACCOUNTS", Integer.toString(records.size()), "manually created")),
                "No administrator accounts",
                "Admin accounts are created through the project's manual setup process.");
    }

    private ScreenData loadAuditLogs() {
        List<Map<String, String>> records = new ArrayList<>();
        records.addAll(read("data/runtime/logs/inventoryLogs.csv", List.of(
                "logID", "batchID", "itemID", "actorID", "actorRole", "action", "quantity", "dateTime",
                "reason", "outcome", "relatedRecordID", "details")));
        records.addAll(read("data/runtime/logs/batchLogs.csv", List.of(
                "logID", "batchID", "itemID", "actorID", "actorRole", "action", "dateTime", "reason",
                "outcome", "relatedRecordID", "details")));
        records.addAll(read("data/runtime/logs/prescriptionLogs.csv", List.of(
                "logID", "prescriptionID", "actorID", "actorRole", "action", "dateTime", "reason",
                "outcome", "relatedRecordID", "details")));
        records.addAll(read("data/runtime/logs/transactionLogs.csv", List.of(
                "logID", "transactionID", "customerID", "actorID", "actorRole", "action", "dateTime",
                "reason", "outcome", "relatedRecordID", "details")));
        records.addAll(read("data/runtime/logs/balanceLogs.csv", List.of(
                "logID", "customerID", "actorID", "actorRole", "action", "amount", "balanceBefore",
                "balanceAfter", "dateTime", "reason", "outcome", "relatedRecordID", "details")));
        List<List<String>> rows = records.stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("dateTime", "")).reversed())
                .map(row -> List.of(
                        safe(row.get("dateTime")), safe(row.get("actorID")), displayStatus(row.get("action")),
                        safe(row.get("reason")), relatedRecord(row), displayStatus(row.get("outcome"))))
                .toList();
        return new ScreenData(
                List.of("TIME", "ACTOR", "ACTION", "REASON", "RELATED RECORD", "OUTCOME"), rows,
                List.of(new Metric("AUDIT EVENTS", Integer.toString(records.size()), "preserved history")),
                "No audit events yet",
                "Inventory, prescription, transaction, and balance history will appear here.");
    }

    private List<Map<String, String>> read(String path, List<String> headers) {
        return csvFileManager.readRecords(Path.of(path), headers);
    }

    private Map<String, String> itemNameMap() {
        Map<String, String> names = new HashMap<>();
        for (Item item : itemRepository.findAll()) {
            names.put(item.getItemID(), item.getItemName());
        }
        return names;
    }

    private boolean isSellable(Batch batch, List<Item> items) {
        return batch.isAvailableOn(LocalDate.now()) && items.stream()
                .anyMatch(item -> item.getItemID().equals(batch.getItemID()) && item.getStatus() == ItemStatus.ACTIVE);
    }

    private static boolean isOpenRestock(String status) {
        return "REQUESTED".equals(status) || "APPROVED".equals(status)
                || "PARTIALLY_RECEIVED".equals(status);
    }

    private static String relatedRecord(Map<String, String> row) {
        String related = row.get("relatedRecordID");
        if (related != null && !related.isBlank()) {
            return related;
        }
        for (String key : List.of("batchID", "prescriptionID", "transactionID", "customerID")) {
            String value = row.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return safe(row.get("logID"));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private static String money(BigDecimal amount) {
        return "₱ " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "—" : DISPLAY_DATE.format(date);
    }

    private static String displayStatus(String status) {
        if (status == null || status.isBlank()) {
            return "—";
        }
        String lower = status.toLowerCase().replace('_', ' ');
        String[] words = lower.split(" ");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (display.length() > 0) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }
}