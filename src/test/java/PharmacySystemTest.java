import model.account.Admin;
import model.account.AdminStatus;
import model.account.Customer;
import model.account.CustomerStatus;
import model.account.Pharmacist;
import model.account.StaffStatus;
import model.account.User;
import model.account.UserRole;
import org.junit.jupiter.api.Test;
import repository.csv.CsvFileManager;
import repository.csv.CsvWriteCoordinator;
import security.PasswordHasher;
import service.PharmacyScreenService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PharmacySystemTest {

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
}