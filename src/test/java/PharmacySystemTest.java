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
}