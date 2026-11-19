package service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.inventory.Batch;
import model.inventory.BatchStatus;
import model.inventory.Item;
import model.inventory.ItemStatus;
import repository.csv.CsvFileManager;
import repository.inventory.BatchRepository;
import repository.inventory.ItemRepository;

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

    private final ItemRepository itemRepository;
    private final BatchRepository batchRepository;
    private final CsvFileManager csvFileManager;
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
        return "REQUESTED".equals(status) || "PARTIALLY_RECEIVED".equals(status);
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
