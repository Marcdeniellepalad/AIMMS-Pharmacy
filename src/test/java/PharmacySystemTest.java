import model.account.Admin;
import model.account.AdminStatus;
import model.account.Customer;
import model.account.CustomerStatus;
import model.account.Pharmacist;
import model.account.StaffStatus;
import model.account.User;
import model.account.UserRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;
import security.PasswordHasher;
import service.DemoDataSeeder;
import service.PharmacyScreenService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PharmacySystemTest {

    @BeforeAll
    static void resetRuntimeData() {
        Path runtime = Path.of("data/runtime");
        if (Files.exists(runtime)) {
            try (Stream<Path> paths = Files.walk(runtime)) {
                paths.sorted(Comparator.reverseOrder())
                        .filter(path -> !".gitkeep".equals(path.getFileName().toString()))
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                                // keep the directory tree; the seeder reuses it
                            }
                        });
            } catch (Exception ignored) {
                // fall through and seed over whatever remains
            }
        }
        DemoDataSeeder.seedIfEmpty();
    }

    @Test
    void testPasswordHasherVerifiesValidAndRejectsInvalidPasswords() {
        String hash = PasswordHasher.hash("TupPharma2026!".toCharArray());
        assertTrue(hash.startsWith("pbkdf2-sha256$"));
        assertTrue(PasswordHasher.verify("TupPharma2026!".toCharArray(), hash));
        assertFalse(PasswordHasher.verify("WrongPassword".toCharArray(), hash));
    }

    @Test
    void testUserInheritanceAndPolymorphism() {
        String validHash = PasswordHasher.hash("TestPass123!".toCharArray());
        User admin = new Admin("ADM-99", "admin99", validHash, "Test Admin", AdminStatus.ACTIVE, LocalDate.now());
        User pharmacist = new Pharmacist("PHR-99", "phr99", validHash, "Test RPh", StaffStatus.ACTIVE, LocalDate.now(), LocalDate.now());
        User customer = new Customer("CUS-99", "cus99", validHash, "Test Patient", BigDecimal.TEN, CustomerStatus.ACTIVE, LocalDate.now());

        assertEquals(UserRole.ADMIN, admin.getRole());
        assertEquals(UserRole.PHARMACIST, pharmacist.getRole());
        assertEquals(UserRole.CUSTOMER, customer.getRole());
    }

    @Test
    void testCsvWriteCoordinatorAtomicCommit() throws Exception {
        CsvFileManager csv = new CsvFileManager();
        CsvWriteCoordinator coordinator = new CsvWriteCoordinator();
        Path testCsv = Path.of("data/runtime/test_atomic.csv");

        var headers = List.of("id", "status");
        var rows = List.of(Map.of("id", "ROW-1", "status", "OK"));

        coordinator.commit(List.of(new CsvWriteCoordinator.Update(testCsv, headers, rows)));

        var readBack = csv.readRecords(testCsv, headers);
        assertEquals(1, readBack.size());
        assertEquals("OK", readBack.get(0).get("status"));
        Files.deleteIfExists(testCsv);
    }

    @Test
    void testPrescriptionVerificationUpdatesStatus() {
        PharmacyScreenService service = new PharmacyScreenService();
        String result = service.reviewPrescription(
                "RX-00001", "VERIFIED", "Verified license PRC-123456", "PHR-00001", "PHARMACIST");
        assertTrue(result.contains("VERIFIED"));
    }

    @Test
    void testVoidTransactionPreservesRecordAsCancelled() {
        PharmacyScreenService service = new PharmacyScreenService();
        var saleRows = service.load(PharmacyScreenService.Screen.COUNTER_SALE).rows();
        String activeItemID = saleRows.stream()
                .filter(r -> Integer.parseInt(r.get(3)) > 0 && !r.toString().toLowerCase().contains("rx") && !r.toString().toLowerCase().contains("prescription"))
                .map(r -> r.get(2))
                .findFirst()
                .orElseThrow();

        var lines = List.of(new PharmacyScreenService.SaleLine(activeItemID, 1));
        String txnId = service.completeSale("CUS-00001", lines, "PHR-00001", "PHARMACIST");
        assertNotNull(txnId);

        String voidedId = service.voidTransaction(txnId, "Customer returned unopened item", "PHR-00001", "PHARMACIST");
        assertEquals(txnId, voidedId);

        assertThrows(IllegalStateException.class, () ->
                service.voidTransaction(txnId, "Duplicate void attempt", "PHR-00001", "PHARMACIST"));
    }

    @Test
    void testTopUpIncreasesBalanceAndWritesAuditRow() {
        PharmacyScreenService service = new PharmacyScreenService();
        List<String> customerHeaders = List.of("customerID", "username", "passwordHash",
                "fullName", "balance", "status", "dateRegistered");
        List<Map<String, String>> customers = new CsvFileManager().readRecords(
                Path.of("data/runtime/accounts/customerDB.csv"), customerHeaders);
        BigDecimal before = new BigDecimal(customers.stream()
                .filter(r -> "CUS-00001".equals(r.get("customerID"))).findFirst().orElseThrow()
                .get("balance"));
        BigDecimal newBalance = service.topUpCustomerBalance(
                "CUS-00001", new BigDecimal("250.00"), "PHR-00001", "PHARMACIST");
        assertEquals(0, newBalance.compareTo(before.add(new BigDecimal("250.00"))));

        List<Map<String, String>> logs = new CsvFileManager().readRecords(
                Path.of("data/runtime/logs/balanceLogs.csv"),
                List.of("logID", "customerID", "actorID", "actorRole", "action", "amount",
                        "balanceBefore", "balanceAfter", "dateTime", "reason", "outcome",
                        "relatedRecordID", "details"));
        Map<String, String> lastLog = logs.get(logs.size() - 1);
        assertEquals("TOP_UP", lastLog.get("action"));
        assertEquals("CUS-00001", lastLog.get("customerID"));
        assertEquals("250.00", lastLog.get("amount"));

        assertThrows(IllegalArgumentException.class, () ->
                service.topUpCustomerBalance("CUS-00001", BigDecimal.ZERO, "PHR-00001", "PHARMACIST"));
        assertThrows(IllegalArgumentException.class, () ->
                service.topUpCustomerBalance("CUS-99999", new BigDecimal("50.00"), "PHR-00001", "PHARMACIST"));
    }

    @Test
    void testRegisterCustomerCreatesActiveZeroBalanceAccount() {
        PharmacyScreenService service = new PharmacyScreenService();
        String customerID = service.registerCustomer(
                "test.customer", "CounterPass1!".toCharArray(), "Bea Santos");
        assertTrue(customerID.startsWith("CUS-"));
        List<Map<String, String>> customers = new CsvFileManager().readRecords(
                Path.of("data/runtime/accounts/customerDB.csv"),
                List.of("customerID", "username", "passwordHash", "fullName", "balance",
                        "status", "dateRegistered"));
        Map<String, String> saved = customers.stream()
                .filter(r -> customerID.equals(r.get("customerID"))).findFirst().orElseThrow();
        assertEquals("test.customer", saved.get("username"));
        assertEquals("0.00", saved.get("balance"));
        assertEquals("ACTIVE", saved.get("status"));
        assertTrue(saved.get("passwordHash").startsWith("pbkdf2-sha256$"));

        assertThrows(IllegalArgumentException.class, () ->
                service.registerCustomer("test.customer", "CounterPass1!".toCharArray(), "Duplicate User"));
        assertThrows(IllegalArgumentException.class, () ->
                service.registerCustomer("shortpw", "short".toCharArray(), "Weak Password"));
    }

    @Test
    void testRejectPharmacistBlocksApproval() {
        PharmacyScreenService service = new PharmacyScreenService();
        String rejectedName = service.rejectPharmacist("PHR-00002", "ADM-00001");
        assertEquals("Jamie Cruz", rejectedName);
        List<Map<String, String>> pharmacists = new CsvFileManager().readRecords(
                Path.of("data/runtime/accounts/pharmacistDB.csv"),
                List.of("pharmacistID", "username", "passwordHash", "fullName", "status",
                        "dateRegistered", "dateApproved"));
        assertEquals("REJECTED", pharmacists.stream()
                .filter(r -> "PHR-00002".equals(r.get("pharmacistID"))).findFirst().orElseThrow()
                .get("status"));
        assertThrows(IllegalStateException.class, () ->
                service.approvePharmacist("PHR-00002", "ADM-00001"));
        assertThrows(IllegalArgumentException.class, () ->
                service.rejectPharmacist("PHR-99999", "ADM-00001"));
    }

    @Test
    void testRegisterPaperPrescriptionCreatesReceivableRecords() {
        PharmacyScreenService service = new PharmacyScreenService();
        String customerID = service.registerCustomer(
                "marco.reyes", "CounterPass1!".toCharArray(), "Marco Reyes");
        String rxID = service.registerPrescription(customerID, "Marco Reyes", "Dr. Iris Chan", "PRC-998877",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "PAPER-TEST-001",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-007", 10, "Take one tablet daily")),
                "PHR-00001", "PHARMACIST");
        assertTrue(rxID.startsWith("RX-"));
        List<Map<String, String>> prescriptions = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionsDB.csv"),
                List.of("prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
                        "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status"));
        Map<String, String> saved = prescriptions.stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow();
        assertEquals("RECEIVED", saved.get("status"));
        assertEquals("PAPER", saved.get("sourceType"));
        assertEquals(customerID, saved.get("customerID"));
        assertEquals("Marco Reyes", saved.get("patientName"));
        List<Map<String, String>> rxItems = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed"));
        Map<String, String> item = rxItems.stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow();
        assertTrue(item.get("prescriptionItemID").startsWith("RXI-"));
        assertEquals("DEMO-ITEM-007", item.get("itemID"));
        assertEquals("0", item.get("quantityDispensed"));
        List<Map<String, String>> logs = new CsvFileManager().readRecords(
                Path.of("data/runtime/logs/prescriptionLogs.csv"),
                List.of("logID", "prescriptionID", "actorID", "actorRole", "action", "dateTime",
                        "reason", "outcome", "relatedRecordID", "details"));
        assertEquals("REGISTER", logs.get(logs.size() - 1).get("action"));

        assertThrows(IllegalArgumentException.class, () -> service.registerPrescription("CUS-99999",
                "Ghost Patient", "Dr. Who", "PRC-000000", LocalDate.now(), LocalDate.now().plusDays(5), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-007", 5, "")),
                "PHR-00001", "PHARMACIST"));
        assertThrows(IllegalArgumentException.class, () -> service.registerPrescription("CUS-00001",
                "Marco Reyes", "Dr. Iris Chan", "PRC-998877", LocalDate.now(), LocalDate.now().minusDays(1), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-007", 5, "")),
                "PHR-00001", "PHARMACIST"));
        assertThrows(IllegalArgumentException.class, () -> service.registerPrescription("CUS-00001",
                "Marco Reyes", "Dr. Iris Chan", "PRC-998877", LocalDate.now(), LocalDate.now().plusDays(5), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-001", 5, "")),
                "PHR-00001", "PHARMACIST"));
    }

    @Test
    void testPrescriptionSaleRequiresVerifiedLinkAndRespectsQuantity() {
        PharmacyScreenService service = new PharmacyScreenService();
        String customerID = service.registerCustomer(
                "nina.dolores", "CounterPass1!".toCharArray(), "Nina Dolores");
        service.topUpCustomerBalance(customerID, new BigDecimal("500.00"), "PHR-00001", "PHARMACIST");
        String rxID = service.registerPrescription(customerID, "Nina Dolores", "Dr. Iris Chan", "PRC-998877",
                LocalDate.now(), LocalDate.now().plusDays(14), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-008", 5, "")),
                "PHR-00001", "PHARMACIST");
        String rxItemID = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed")).stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow()
                .get("prescriptionItemID");

        assertThrows(IllegalArgumentException.class, () -> service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-008", 1)), "PHR-00001", "PHARMACIST"));
        assertThrows(IllegalArgumentException.class, () -> service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-008", 1, rxItemID)), "PHR-00001", "PHARMACIST"));
        service.reviewPrescription(rxID, "VERIFIED", "License confirmed", "PHR-00001", "PHARMACIST");
        assertThrows(IllegalArgumentException.class, () -> service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-008", 6, rxItemID)), "PHR-00001", "PHARMACIST"));
    }

    @Test
    void testVerifiedPrescriptionSaleDispensesPartiallyThenFully() {
        PharmacyScreenService service = new PharmacyScreenService();
        String customerID = service.registerCustomer(
                "kai.mendoza", "CounterPass1!".toCharArray(), "Kai Mendoza");
        service.topUpCustomerBalance(customerID, new BigDecimal("500.00"), "PHR-00001", "PHARMACIST");
        String rxID = service.registerPrescription(customerID, "Kai Mendoza", "Dr. Iris Chan", "PRC-998877",
                LocalDate.now(), LocalDate.now().plusDays(21), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-007", 10, "")),
                "PHR-00001", "PHARMACIST");
        service.reviewPrescription(rxID, "VERIFIED", "License confirmed", "PHR-00001", "PHARMACIST");
        String rxItemID = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed")).stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow()
                .get("prescriptionItemID");
        assertTrue(service.eligiblePrescriptionItems(customerID).stream()
                .anyMatch(rx -> rx.prescriptionItemID().equals(rxItemID)));

        String firstSale = service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-007", 4, rxItemID)), "PHR-00001", "PHARMACIST");
        assertNotNull(firstSale);
        List<Map<String, String>> rxItems = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed"));
        assertEquals("4", rxItems.stream()
                .filter(row -> rxItemID.equals(row.get("prescriptionItemID"))).findFirst().orElseThrow()
                .get("quantityDispensed"));
        List<Map<String, String>> prescriptions = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionsDB.csv"),
                List.of("prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
                        "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status"));
        assertEquals("PARTIALLY_DISPENSED", prescriptions.stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow().get("status"));
        List<Map<String, String>> txnItems = new CsvFileManager().readRecords(
                Path.of("data/runtime/transactions/transactionItemsDB.csv"),
                List.of("transactionItemID", "transactionID", "itemID", "batchID", "prescriptionItemID",
                        "quantity", "unitPrice", "subtotal"));
        assertTrue(txnItems.stream().anyMatch(row -> firstSale.equals(row.get("transactionID"))
                && rxItemID.equals(row.get("prescriptionItemID"))));

        service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-007", 6, rxItemID)), "PHR-00001", "PHARMACIST");
        List<Map<String, String>> rxItemsAfterSecondSale = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed"));
        assertEquals("10", rxItemsAfterSecondSale.stream()
                .filter(row -> rxItemID.equals(row.get("prescriptionItemID"))).findFirst().orElseThrow()
                .get("quantityDispensed"));
        assertEquals("DISPENSED", new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionsDB.csv"),
                List.of("prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
                        "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status"))
                .stream().filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow().get("status"));
        assertTrue(service.eligiblePrescriptionItems(customerID).stream()
                .noneMatch(rx -> rx.prescriptionItemID().equals(rxItemID)));
        assertThrows(IllegalArgumentException.class, () -> service.completeSale(customerID,
                List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-007", 1, rxItemID)), "PHR-00001", "PHARMACIST"));
    }

    @Test
    void testExpiredPrescriptionCannotBeDispensed() {
        PharmacyScreenService service = new PharmacyScreenService();
        String customerID = service.registerCustomer(
                "ruth.villanueva", "CounterPass1!".toCharArray(), "Ruth Villanueva");
        service.topUpCustomerBalance(customerID, new BigDecimal("500.00"), "PHR-00001", "PHARMACIST");
        String rxID = service.registerPrescription(customerID, "Ruth Villanueva", "Dr. Iris Chan", "PRC-998877",
                LocalDate.now(), LocalDate.now().plusDays(5), "",
                List.of(new PharmacyScreenService.PrescriptionLine("DEMO-ITEM-008", 6, "")),
                "PHR-00001", "PHARMACIST");
        service.reviewPrescription(rxID, "VERIFIED", "License confirmed", "PHR-00001", "PHARMACIST");
        String rxItemID = new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionItemsDB.csv"),
                List.of("prescriptionItemID", "prescriptionID", "itemID", "medicineName", "strength", "dosageForm",
                        "directions", "quantityPrescribed", "quantityDispensed")).stream()
                .filter(row -> rxID.equals(row.get("prescriptionID"))).findFirst().orElseThrow()
                .get("prescriptionItemID");

        List<Map<String, String>> prescriptions = new ArrayList<>(new CsvFileManager().readRecords(
                Path.of("data/runtime/prescriptions/prescriptionsDB.csv"),
                List.of("prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
                        "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status")));
        for (int index = 0; index < prescriptions.size(); index++) {
            if (rxID.equals(prescriptions.get(index).get("prescriptionID"))) {
                Map<String, String> updated = new java.util.LinkedHashMap<>(prescriptions.get(index));
                updated.put("validUntil", LocalDate.now().minusDays(1).toString());
                prescriptions.set(index, updated);
            }
        }
        new CsvWriteCoordinator().commit(List.of(new CsvWriteCoordinator.Update(
                Path.of("data/runtime/prescriptions/prescriptionsDB.csv"),
                List.of("prescriptionID", "customerID", "patientName", "prescriberName", "prescriberLicenseNumber",
                        "prescriptionDate", "dateReceived", "validUntil", "sourceType", "sourceReference", "status"),
                prescriptions)));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                service.completeSale(customerID,
                        List.of(new PharmacyScreenService.SaleLine("DEMO-ITEM-008", 1, rxItemID)),
                        "PHR-00001", "PHARMACIST"));
        assertTrue(thrown.getMessage().contains("expired"));
    }
}