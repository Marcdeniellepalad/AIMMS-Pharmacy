package service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import repository.csv.CsvFileManager;
import security.PasswordHasher;

/** Seeds ignored runtime CSV files with clearly synthetic records when they are empty. */
public final class DemoDataSeeder {

    private static final String DEMO_PASSWORD = "AIMMsDemo1!";
    private static final List<String> ADMIN_HEADERS = List.of(
            "adminID", "username", "passwordHash", "fullName", "status", "dateCreated");
    private static final List<String> PHARMACIST_HEADERS = List.of(
            "pharmacistID", "username", "passwordHash", "fullName", "status", "dateRegistered", "dateApproved");
    private static final List<String> CUSTOMER_HEADERS = List.of(
            "customerID", "username", "passwordHash", "fullName", "balance", "status", "dateRegistered");
    private static final List<String> ITEM_HEADERS = List.of(
            "itemID", "itemName", "category", "price", "prescriptionRequired", "status");
    private static final List<String> BATCH_HEADERS = List.of(
            "batchID", "itemID", "lotNumber", "quantity", "expirationDate", "dateReceived", "status");
    private static final List<String> RESTOCK_HEADERS = List.of(
            "requestID", "itemID", "quantityRequested", "quantityReceived", "requestedByPharmacistID",
            "dateRequested", "receivedByID", "dateReceived", "status", "details");
    private static final List<String> PRESCRIPTION_HEADERS = List.of(
            "prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
            "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status");
    private static final List<String> PRESCRIPTION_ITEM_HEADERS = List.of(
            "prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
            "directions", "quantityPrescribed", "quantityDispensed");
    private static final List<String> TRANSACTION_HEADERS = List.of(
            "transactionID", "customerID", "pharmacistID", "totalAmount", "status", "dateTime");
    private static final List<String> TRANSACTION_ITEM_HEADERS = List.of(
            "transactionItemID", "transactionID", "itemID", "batchID", "prescriptionItemID",
            "quantity", "unitPrice", "subtotal");
    private static final List<String> INVENTORY_LOG_HEADERS = List.of(
            "logID", "batchID", "itemID", "actorID", "actorRole", "action", "quantity", "dateTime",
            "reason", "outcome", "relatedRecordID", "details");
    private static final List<String> BATCH_LOG_HEADERS = List.of(
            "logID", "batchID", "itemID", "actorID", "actorRole", "action", "dateTime", "reason",
            "outcome", "relatedRecordID", "details");
    private static final List<String> PRESCRIPTION_LOG_HEADERS = List.of(
            "logID", "prescriptionID", "actorID", "actorRole", "action", "dateTime", "reason",
            "outcome", "relatedRecordID", "details");
    private static final List<String> TRANSACTION_LOG_HEADERS = List.of(
            "logID", "transactionID", "customerID", "actorID", "actorRole", "action", "dateTime",
            "reason", "outcome", "relatedRecordID", "details");
    private static final List<String> BALANCE_LOG_HEADERS = List.of(
            "logID", "customerID", "actorID", "actorRole", "action", "amount", "balanceBefore",
            "balanceAfter", "dateTime", "reason", "outcome", "relatedRecordID", "details");

    private final CsvFileManager csv = new CsvFileManager();

    private DemoDataSeeder() { }

    /** Returns true when the demo records were seeded; never overwrites existing runtime rows. */
    public static boolean seedIfEmpty() {
        return new DemoDataSeeder().seed();
    }

    private boolean seed() {
        Path marker = Path.of("data/runtime/.aimms-demo-seeded");
        if (Files.exists(marker)) {
            return true;
        }
        List<PathAndHeaders> files = List.of(
                file("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS),
                file("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS),
                file("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS),
                file("data/runtime/inventory/itemsDB.csv", ITEM_HEADERS),
                file("data/runtime/inventory/batchDB.csv", BATCH_HEADERS),
                file("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS),
                file("data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS),
                file("data/runtime/prescriptions/prescriptionItemsDB.csv", PRESCRIPTION_ITEM_HEADERS),
                file("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS),
                file("data/runtime/transactions/transactionItemsDB.csv", TRANSACTION_ITEM_HEADERS),
                file("data/runtime/logs/inventoryLogs.csv", INVENTORY_LOG_HEADERS),
                file("data/runtime/logs/batchLogs.csv", BATCH_LOG_HEADERS),
                file("data/runtime/logs/prescriptionLogs.csv", PRESCRIPTION_LOG_HEADERS),
                file("data/runtime/logs/transactionLogs.csv", TRANSACTION_LOG_HEADERS),
                file("data/runtime/logs/balanceLogs.csv", BALANCE_LOG_HEADERS));
        if (files.stream().anyMatch(file -> !csv.readRecords(file.path(), file.headers()).isEmpty())) {
            if (isExistingDemoData()) {
                markDemoData(marker);
                return true;
            }
            if (!hasOnlySampleInventory()) {
                return false;
            }
        }

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String adminPasswordHash = newDemoPasswordHash();
        String pharmacistPasswordHash = newDemoPasswordHash();
        String pendingPasswordHash = newDemoPasswordHash();
        String customerPasswordHash = newDemoPasswordHash();
        write("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS, List.of(
                row("adminID", "ADM-00001", "username", "admin", "passwordHash", adminPasswordHash,
                        "fullName", "AIMMs Administrator", "status", "ACTIVE", "dateCreated", today.toString())));
        write("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS, List.of(
                row("pharmacistID", "PHR-00001", "username", "pharmacist", "passwordHash", pharmacistPasswordHash,
                        "fullName", "Alex Rivera", "status", "ACTIVE", "dateRegistered", today.minusDays(40).toString(),
                        "dateApproved", today.minusDays(39).toString()),
                row("pharmacistID", "PHR-00002", "username", "pending", "passwordHash", pendingPasswordHash,
                        "fullName", "Jamie Cruz", "status", "PENDING", "dateRegistered", today.minusDays(1).toString(),
                        "dateApproved", "")));
        write("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS, List.of(
                row("customerID", "CUS-00001", "username", "sample.customer", "passwordHash", customerPasswordHash,
                        "fullName", "Jordan Santos", "balance", "1250.00", "status", "ACTIVE",
                        "dateRegistered", today.minusDays(90).toString()),
                row("customerID", "CUS-00002", "username", "andrea.lim", "passwordHash", customerPasswordHash,
                        "fullName", "Andrea Lim", "balance", "300.00", "status", "ACTIVE",
                        "dateRegistered", today.minusDays(30).toString())));
        seedInventoryFromSamples();
        write("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS, List.of(
                row("requestID", "RST-00001", "itemID", "DEMO-ITEM-006", "quantityRequested", "60",
                        "quantityReceived", "0", "requestedByPharmacistID", "PHR-00001",
                        "dateRequested", today.minusDays(1).toString(), "receivedByID", "", "dateReceived", "",
                        "status", "REQUESTED", "details", "Amoxicillin stock is running low.")));
        write("data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS, List.of(
                row("prescriptionID", "RX-00001", "customerID", "CUS-00001", "patientName", "Jordan Santos",
                        "prescriberName", "Dr. Taylor Reyes", "prescriberLicenseNumber", "PRC-123456",
                        "prescriptionDate", today.minusDays(2).toString(), "dateReceived", today.toString(),
                        "validUntil", today.plusDays(28).toString(), "sourceType", "PAPER",
                        "sourceReference", "DEMO-RX-00001", "status", "RECEIVED"),
                row("prescriptionID", "RX-00002", "customerID", "CUS-00001", "patientName", "Jordan Santos",
                        "prescriberName", "Dr. Morgan Lee", "prescriberLicenseNumber", "PRC-654321",
                        "prescriptionDate", today.minusDays(3).toString(), "dateReceived", today.minusDays(1).toString(),
                        "validUntil", today.plusDays(17).toString(), "sourceType", "PAPER",
                        "sourceReference", "DEMO-RX-00002", "status", "NEEDS_CLARIFICATION"),
                row("prescriptionID", "RX-00003", "customerID", "CUS-00002", "patientName", "Andrea Lim",
                        "prescriberName", "Dr. Taylor Reyes", "prescriberLicenseNumber", "PRC-123456",
                        "prescriptionDate", today.minusDays(1).toString(), "dateReceived", today.toString(),
                        "validUntil", today.plusDays(20).toString(), "sourceType", "PAPER",
                        "sourceReference", "DEMO-RX-00003", "status", "VERIFIED")));
        write("data/runtime/prescriptions/prescriptionItemsDB.csv", PRESCRIPTION_ITEM_HEADERS, List.of(
                row("prescriptionItemID", "RXI-00001", "prescriptionID", "RX-00001", "itemID", "DEMO-ITEM-006",
                        "medicineName", "Amoxicillin", "strength", "500 mg", "dosageForm", "Capsule",
                        "directions", "Take one capsule three times daily", "quantityPrescribed", "21",
                        "quantityDispensed", "0"),
                row("prescriptionItemID", "RXI-00002", "prescriptionID", "RX-00003", "itemID", "DEMO-ITEM-008",
                        "medicineName", "Losartan 50 mg tablet", "strength", "50 mg", "dosageForm", "Tablet",
                        "directions", "Take one tablet once daily", "quantityPrescribed", "14",
                        "quantityDispensed", "0")));
        String dateTime = now.toString();
        write("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS, List.of(
                row("transactionID", "TXN-00001", "customerID", "CUS-00001", "pharmacistID", "PHR-00001",
                        "totalAmount", "12.00", "status", "COMPLETED", "dateTime", dateTime)));
        write("data/runtime/transactions/transactionItemsDB.csv", TRANSACTION_ITEM_HEADERS, List.of(
                row("transactionItemID", "TXI-00001", "transactionID", "TXN-00001", "itemID", "DEMO-ITEM-001",
                        "batchID", "DEMO-BATCH-001", "prescriptionItemID", "", "quantity", "3",
                        "unitPrice", "4.00", "subtotal", "12.00")));
        write("data/runtime/logs/inventoryLogs.csv", INVENTORY_LOG_HEADERS, List.of(
                row("logID", "LOG-00001", "batchID", "DEMO-BATCH-001", "itemID", "DEMO-ITEM-001",
                        "actorID", "PHR-00001", "actorRole", "PHARMACIST", "action", "SALE_DISPENSED",
                        "quantity", "3", "dateTime", dateTime, "reason", "Counter sale",
                        "outcome", "SUCCESS", "relatedRecordID", "TXN-00001", "details", "Demo sale stock movement.")));
        write("data/runtime/logs/batchLogs.csv", BATCH_LOG_HEADERS, List.of(
                row("logID", "LOG-00002", "batchID", "DEMO-BATCH-002", "itemID", "DEMO-ITEM-002",
                        "actorID", "ADM-00001", "actorRole", "ADMIN", "action", "BATCH_RECEIVED",
                        "dateTime", now.minusDays(4).toString(), "reason", "Supplier delivery",
                        "outcome", "SUCCESS", "relatedRecordID", "DEMO-BATCH-002", "details", "Demo batch record.")));
        write("data/runtime/logs/prescriptionLogs.csv", PRESCRIPTION_LOG_HEADERS, List.of(
                row("logID", "LOG-00003", "prescriptionID", "RX-00001", "actorID", "PHR-00001",
                        "actorRole", "PHARMACIST", "action", "PRESCRIPTION_RECEIVED", "dateTime", dateTime,
                        "reason", "External prescription intake", "outcome", "PENDING_REVIEW",
                        "relatedRecordID", "RX-00001", "details", "Synthetic demo record."),
                row("logID", "LOG-00006", "prescriptionID", "RX-00003", "actorID", "PHR-00001",
                        "actorRole", "PHARMACIST", "action", "VERIFIED", "dateTime", dateTime,
                        "reason", "Verified license PRC-123456", "outcome", "SUCCESS",
                        "relatedRecordID", "", "details", "Status updated to VERIFIED.")));
        write("data/runtime/logs/transactionLogs.csv", TRANSACTION_LOG_HEADERS, List.of(
                row("logID", "LOG-00004", "transactionID", "TXN-00001", "customerID", "CUS-00001",
                        "actorID", "PHR-00001", "actorRole", "PHARMACIST", "action", "SALE_COMPLETED",
                        "dateTime", dateTime, "reason", "Counter sale", "outcome", "SUCCESS",
                        "relatedRecordID", "TXN-00001", "details", "Synthetic demo transaction.")));
        write("data/runtime/logs/balanceLogs.csv", BALANCE_LOG_HEADERS, List.of(
                row("logID", "LOG-00005", "customerID", "CUS-00001", "actorID", "PHR-00001",
                        "actorRole", "PHARMACIST", "action", "PURCHASE", "amount", "12.00",
                        "balanceBefore", "1262.00", "balanceAfter", "1250.00", "dateTime", dateTime,
                        "reason", "Counter sale", "outcome", "SUCCESS", "relatedRecordID", "TXN-00001",
                        "details", "Synthetic demo balance change.")));
        markDemoData(marker);
        return true;
    }

    private boolean isExistingDemoData() {
        List<Map<String, String>> admins = csv.readRecords(Path.of("data/runtime/accounts/adminDB.csv"), ADMIN_HEADERS);
        List<Map<String, String>> pharmacists = csv.readRecords(
                Path.of("data/runtime/accounts/pharmacistDB.csv"), PHARMACIST_HEADERS);
        return admins.stream().anyMatch(row -> "admin".equals(row.get("username"))
                        && "AIMMs Administrator".equals(row.get("fullName")))
                && pharmacists.stream().anyMatch(row -> "pharmacist".equals(row.get("username"))
                        && "Alex Rivera".equals(row.get("fullName")));
    }

    private boolean hasOnlySampleInventory() {
        Path runtimeItems = Path.of("data/runtime/inventory/itemsDB.csv");
        Path runtimeBatches = Path.of("data/runtime/inventory/batchDB.csv");
        List<Map<String, String>> sampleItems = csv.readRecords(
                Path.of("data/sample/inventory/itemsDB.csv"), ITEM_HEADERS);
        List<Map<String, String>> sampleBatches = csv.readRecords(
                Path.of("data/sample/inventory/batchDB.csv"), BATCH_HEADERS);
        if (sampleItems.isEmpty() || sampleBatches.isEmpty()
                || !sampleItems.equals(csv.readRecords(runtimeItems, ITEM_HEADERS))
                || !sampleBatches.equals(csv.readRecords(runtimeBatches, BATCH_HEADERS))) {
            return false;
        }

        List<PathAndHeaders> otherFiles = List.of(
                file("data/runtime/accounts/adminDB.csv", ADMIN_HEADERS),
                file("data/runtime/accounts/pharmacistDB.csv", PHARMACIST_HEADERS),
                file("data/runtime/accounts/customerDB.csv", CUSTOMER_HEADERS),
                file("data/runtime/inventory/restockRequests.csv", RESTOCK_HEADERS),
                file("data/runtime/prescriptions/prescriptionsDB.csv", PRESCRIPTION_HEADERS),
                file("data/runtime/prescriptions/prescriptionItemsDB.csv", PRESCRIPTION_ITEM_HEADERS),
                file("data/runtime/transactions/transactionsDB.csv", TRANSACTION_HEADERS),
                file("data/runtime/transactions/transactionItemsDB.csv", TRANSACTION_ITEM_HEADERS),
                file("data/runtime/logs/inventoryLogs.csv", INVENTORY_LOG_HEADERS),
                file("data/runtime/logs/batchLogs.csv", BATCH_LOG_HEADERS),
                file("data/runtime/logs/prescriptionLogs.csv", PRESCRIPTION_LOG_HEADERS),
                file("data/runtime/logs/transactionLogs.csv", TRANSACTION_LOG_HEADERS),
                file("data/runtime/logs/balanceLogs.csv", BALANCE_LOG_HEADERS));
        return otherFiles.stream().allMatch(file -> csv.readRecords(file.path(), file.headers()).isEmpty());
    }

    private static String newDemoPasswordHash() {
        return PasswordHasher.hash(DEMO_PASSWORD.toCharArray());
    }

    private static void markDemoData(Path marker) {
        try {
            Files.writeString(marker, "Synthetic AIMMs demo records; safe to delete with the rest of data/runtime.\n",
                    StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Demo data can still be recognized by its synthetic account records on the next launch.
        }
    }

    private void write(String path, List<String> headers, List<Map<String, String>> records) {
        csv.writeRecords(Path.of(path), headers, records);
    }

    private void seedInventoryFromSamples() {
        List<Map<String, String>> items = csv.readRecords(
                Path.of("data/sample/inventory/itemsDB.csv"), ITEM_HEADERS);
        List<Map<String, String>> batches = csv.readRecords(
                Path.of("data/sample/inventory/batchDB.csv"), BATCH_HEADERS);
        if (items.isEmpty() || batches.isEmpty()) {
            throw new IllegalStateException("Synthetic sample inventory CSVs must contain starter records.");
        }
        List<Map<String, String>> demoBatches = new ArrayList<>(batches.size());
        for (Map<String, String> batch : batches) {
            Map<String, String> demoBatch = new LinkedHashMap<>(batch);
            if ("DEMO-BATCH-001".equals(demoBatch.get("batchID"))) {
                // The demo transaction below sells three units from this 48-unit starter batch.
                demoBatch.put("quantity", "45");
            }
            demoBatches.add(demoBatch);
        }
        write("data/runtime/inventory/itemsDB.csv", ITEM_HEADERS, items);
        write("data/runtime/inventory/batchDB.csv", BATCH_HEADERS, demoBatches);
    }

    private static PathAndHeaders file(String path, List<String> headers) {
        return new PathAndHeaders(Path.of(path), headers);
    }

    private static Map<String, String> batch(
            String batchID, String itemID, String lot, String quantity, LocalDate expiry, LocalDate received) {
        return batch(batchID, itemID, lot, quantity, expiry, received, "ACTIVE");
    }

    private static Map<String, String> batch(
            String batchID, String itemID, String lot, String quantity, LocalDate expiry, LocalDate received,
            String status) {
        return row("batchID", batchID, "itemID", itemID, "lotNumber", lot, "quantity", quantity,
                "expirationDate", expiry.toString(), "dateReceived", received.toString(), "status", status);
    }

    private static Map<String, String> row(String... fields) {
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < fields.length; index += 2) {
            values.put(fields[index], fields[index + 1]);
        }
        return values;
    }

    private record PathAndHeaders(Path path, List<String> headers) { }
}