import model.account.Admin;
import model.account.AdminStatus;
import model.account.Customer;
import model.account.CustomerStatus;
import model.account.Pharmacist;
import model.account.StaffStatus;
import model.prescription.Prescription;
import model.prescription.PrescriptionItem;
import model.prescription.PrescriptionSource;
import model.prescription.PrescriptionStatus;
import model.transaction.Transaction;
import model.transaction.TransactionItem;
import model.transaction.TransactionStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import repository.account.AdminRepository;
import repository.account.CustomerRepository;
import repository.account.PharmacistRepository;
import repository.csv.CsvDataException;
import repository.csv.CsvWriteCoordinator;
import repository.prescription.PrescriptionItemRepository;
import repository.prescription.PrescriptionRepository;
import repository.transaction.TransactionItemRepository;
import repository.transaction.TransactionRepository;
import security.PasswordHasher;
import service.DemoDataSeeder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Round-trip tests for the seven Sprint 4 repositories against the real runtime CSV files. */
class RepositoryTest {

    private static final List<String> CUSTOMER_HEADERS = List.of(
            "customerID", "username", "passwordHash", "fullName", "balance", "status", "dateRegistered");

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

    private static String hash() {
        return PasswordHasher.hash("RepoTestPass1!".toCharArray());
    }

    @Test
    void testCustomerRepositoryRoundTrip() {
        CustomerRepository repo = new CustomerRepository();
        Customer saved = new Customer("CUS-90001", "repo.test", hash(), "Repo Test Patient",
                new BigDecimal("120.00"), CustomerStatus.ACTIVE, LocalDate.now());
        repo.save(saved);

        assertTrue(repo.findAll().stream().anyMatch(customer -> "CUS-90001".equals(customer.getUserID())));
        assertTrue(repo.findByID("CUS-90001").isPresent());
        assertTrue(repo.findByUsername("REPO.TEST").isPresent(), "username lookup ignores case");
        assertTrue(repo.findByID("CUS-99999").isEmpty());
        assertEquals(new BigDecimal("120.00"), repo.findByID("CUS-90001").orElseThrow().getBalance());

        assertThrows(IllegalArgumentException.class, () -> repo.save(saved), "duplicate ID is rejected");
        assertThrows(IllegalArgumentException.class, () -> repo.update(new Customer("CUS-99999",
                "repo.test", hash(), "Ghost", BigDecimal.TEN, CustomerStatus.ACTIVE, LocalDate.now())),
                "unknown ID is rejected");

        repo.update(new Customer("CUS-90001", "repo.test", hash(), "Repo Test Patient",
                new BigDecimal("55.50"), CustomerStatus.ACTIVE, LocalDate.now()));
        assertEquals(new BigDecimal("55.50"), repo.findByID("CUS-90001").orElseThrow().getBalance());
    }

    @Test
    void testPharmacistRepositoryRoundTrip() {
        PharmacistRepository repo = new PharmacistRepository();
        repo.save(new Pharmacist("PHR-90001", "repo.rph", hash(), "Repo Pharmacist",
                StaffStatus.ACTIVE, LocalDate.now(), LocalDate.now()));
        repo.save(new Pharmacist("PHR-90002", "repo.pending", hash(), "Repo Applicant",
                StaffStatus.PENDING, LocalDate.now(), null));

        assertTrue(repo.findByID("PHR-90002").isPresent());
        assertNull(repo.findByID("PHR-90002").orElseThrow().getDateApproved(), "pending keeps a blank approval date");
        assertTrue(repo.findByStatus(StaffStatus.PENDING).stream()
                .anyMatch(pharmacist -> "PHR-90002".equals(pharmacist.getUserID())));
        assertTrue(repo.findByStatus(StaffStatus.REJECTED).stream()
                .noneMatch(pharmacist -> "PHR-90002".equals(pharmacist.getUserID())));

        repo.update(new Pharmacist("PHR-90002", "repo.pending", hash(), "Repo Applicant",
                StaffStatus.ACTIVE, LocalDate.now(), LocalDate.now()));
        Pharmacist approved = repo.findByID("PHR-90002").orElseThrow();
        assertEquals(StaffStatus.ACTIVE, approved.getStatus());
        assertNotNull(approved.getDateApproved());
    }

    @Test
    void testAdminRepositoryRoundTrip() {
        AdminRepository repo = new AdminRepository();
        Admin saved = new Admin("ADM-90001", "repo.admin", hash(), "Repo Administrator",
                AdminStatus.ACTIVE, LocalDate.now());
        repo.save(saved);

        assertTrue(repo.findByID("ADM-90001").isPresent());
        assertTrue(repo.findByUsername("repo.admin").isPresent());
        assertThrows(IllegalArgumentException.class, () -> repo.save(saved), "duplicate ID is rejected");

        repo.update(new Admin("ADM-90001", "repo.admin", hash(), "Renamed Administrator",
                AdminStatus.INACTIVE, LocalDate.now()));
        Admin reloaded = repo.findByID("ADM-90001").orElseThrow();
        assertEquals("Renamed Administrator", reloaded.getFullName());
        assertEquals(AdminStatus.INACTIVE, reloaded.getStatus());
    }

    @Test
    void testPrescriptionRepositoryRoundTrip() {
        PrescriptionRepository repo = new PrescriptionRepository();
        Prescription saved = new Prescription("RX-90001", "CUS-90001", "Repo Test Patient", "Dr. Repo Prescriber",
                "PRC-900001", LocalDate.now(), LocalDate.now(), PrescriptionSource.PAPER,
                "REPO-REF-01", PrescriptionStatus.RECEIVED, LocalDate.now().plusDays(30));
        repo.save(saved);

        assertTrue(repo.findByID("RX-90001").isPresent());
        assertTrue(repo.findByCustomerID("CUS-90001").stream()
                .anyMatch(prescription -> "RX-90001".equals(prescription.getPrescriptionID())));
        assertTrue(repo.findByStatus(PrescriptionStatus.RECEIVED).stream()
                .anyMatch(prescription -> "RX-90001".equals(prescription.getPrescriptionID())));

        repo.update(new Prescription("RX-90001", "CUS-90001", "Repo Test Patient", "Dr. Repo Prescriber",
                "PRC-900001", LocalDate.now(), LocalDate.now(), PrescriptionSource.PAPER,
                "REPO-REF-01", PrescriptionStatus.VERIFIED, LocalDate.now().plusDays(30)));
        assertEquals(PrescriptionStatus.VERIFIED, repo.findByID("RX-90001").orElseThrow().getStatus());
        assertEquals("REPO-REF-01", repo.findByID("RX-90001").orElseThrow().getSourceReference());
    }

    @Test
    void testPrescriptionItemRepositoryRoundTrip() {
        PrescriptionItemRepository repo = new PrescriptionItemRepository();
        PrescriptionItem saved = new PrescriptionItem("RXI-90001", "RX-90001", "DEMO-ITEM-006",
                "Amoxicillin 500 mg capsule", "", "", "Take one capsule three times daily", 21, 0);
        repo.save(saved);

        assertTrue(repo.findByID("RXI-90001").isPresent());
        List<PrescriptionItem> forPrescription = repo.findByPrescriptionID("RX-90001");
        assertEquals(1, forPrescription.size());
        assertEquals("", forPrescription.get(0).getStrength(), "blank strength round-trips like intake writes it");

        repo.update(new PrescriptionItem("RXI-90001", "RX-90001", "DEMO-ITEM-006",
                "Amoxicillin 500 mg capsule", "", "", "Take one capsule three times daily", 21, 3));
        assertEquals(3, repo.findByID("RXI-90001").orElseThrow().getQuantityDispensed());
        assertEquals(21, repo.findByID("RXI-90001").orElseThrow().getQuantityPrescribed());

        assertThrows(IllegalArgumentException.class,
                () -> new PrescriptionItem("RXI-90002", "RX-90001", "DEMO-ITEM-006", "Amoxicillin",
                        "", "", "", 5, 6),
                "the model still blocks dispensing more than prescribed");
    }

    @Test
    void testTransactionRepositoryRoundTrip() {
        TransactionRepository repo = new TransactionRepository();
        repo.save(new Transaction("TXN-90001", "CUS-90001", "PHR-00001",
                new BigDecimal("12.00"), TransactionStatus.COMPLETED, LocalDateTime.now().withNano(0)));
        repo.save(new Transaction("TXN-90002", "", "PHR-00001",
                new BigDecimal("26.00"), TransactionStatus.CANCELLED, LocalDateTime.now().withNano(0)));

        assertTrue(repo.findByID("TXN-90001").isPresent());
        assertTrue(repo.findByCustomerID("CUS-90001").stream()
                .anyMatch(transaction -> "TXN-90001".equals(transaction.getTransactionID())));
        assertTrue(repo.findByCustomerID("CUS-90001").stream()
                .noneMatch(transaction -> "TXN-90002".equals(transaction.getTransactionID())),
                "blank customer never matches a customer lookup");
        assertTrue(repo.findByStatus(TransactionStatus.CANCELLED).stream()
                .anyMatch(transaction -> "TXN-90002".equals(transaction.getTransactionID())));

        repo.update(new Transaction("TXN-90001", "CUS-90001", "PHR-00001",
                new BigDecimal("12.00"), TransactionStatus.CANCELLED, LocalDateTime.now().withNano(0)));
        assertEquals(TransactionStatus.CANCELLED, repo.findByID("TXN-90001").orElseThrow().getStatus());
    }

    @Test
    void testTransactionItemRepositoryRoundTrip() {
        TransactionItemRepository repo = new TransactionItemRepository();
        repo.save(new TransactionItem("TXI-90001", "TXN-90001", "DEMO-ITEM-001", "DEMO-BATCH-001",
                "", 3, new BigDecimal("4.00"), new BigDecimal("12.00")));
        repo.save(new TransactionItem("TXI-90002", "TXN-90002", "DEMO-ITEM-008", "DEMO-BATCH-008",
                "RXI-90001", 2, new BigDecimal("13.00"), new BigDecimal("26.00")));

        assertTrue(repo.findByID("TXI-90001").isPresent());
        assertEquals(1, repo.findByTransactionID("TXN-90002").size());
        assertEquals("", repo.findByID("TXI-90001").orElseThrow().getPrescriptionItemID());
        assertEquals("RXI-90001", repo.findByID("TXI-90002").orElseThrow().getPrescriptionItemID());

        assertThrows(IllegalArgumentException.class,
                () -> repo.save(new TransactionItem("TXI-90001", "TXN-90001", "DEMO-ITEM-001",
                        "DEMO-BATCH-001", "", 3, new BigDecimal("4.00"), new BigDecimal("12.00"))),
                "duplicate ID is rejected");
    }

    @Test
    void testRepositoriesCommitAtomicallyThroughCoordinator() {
        CustomerRepository customerRepo = new CustomerRepository();
        TransactionRepository transactionRepo = new TransactionRepository();
        TransactionItemRepository itemRepo = new TransactionItemRepository();
        CsvWriteCoordinator coordinator = new CsvWriteCoordinator();

        List<Customer> customers = new ArrayList<>(customerRepo.findAll());
        if (customerRepo.findByID("CUS-90002").isEmpty()) {
            customerRepo.save(new Customer("CUS-90002", "repo.atomic", hash(), "Repo Atomic Patient",
                    new BigDecimal("80.00"), CustomerStatus.ACTIVE, LocalDate.now()));
            customers = new ArrayList<>(customerRepo.findAll());
        }
        Customer before = customerRepo.findByID("CUS-90002").orElseThrow();
        BigDecimal charge = new BigDecimal("10.00");
        int customerIndex = -1;
        for (int index = 0; index < customers.size(); index++) {
            if ("CUS-90002".equals(customers.get(index).getUserID())) {
                customerIndex = index;
                break;
            }
        }
        assertTrue(customerIndex >= 0, "the account must exist in the snapshot");
        customers.set(customerIndex, new Customer(before.getUserID(), before.getUsername(),
                before.getPasswordHash(), before.getFullName(), before.getBalance().subtract(charge),
                before.getStatus(), before.getDateRegistered()));

        List<Transaction> transactions = new ArrayList<>(transactionRepo.findAll());
        transactions.add(new Transaction("TXN-90003", "CUS-90002", "PHR-00001",
                charge, TransactionStatus.COMPLETED, LocalDateTime.now().withNano(0)));

        List<TransactionItem> items = new ArrayList<>(itemRepo.findAll());
        items.add(new TransactionItem("TXI-90003", "TXN-90003", "DEMO-ITEM-001", "DEMO-BATCH-001",
                "", 2, new BigDecimal("5.00"), charge));

        coordinator.commit(List.of(
                customerRepo.asUpdate(customers),
                transactionRepo.asUpdate(transactions),
                itemRepo.asUpdate(items)));

        assertEquals(before.getBalance().subtract(charge),
                customerRepo.findByID("CUS-90002").orElseThrow().getBalance());
        assertTrue(transactionRepo.findByID("TXN-90003").isPresent());
        assertEquals(charge, transactionRepo.findByID("TXN-90003").orElseThrow().getTotalAmount());
        assertEquals(1, itemRepo.findByTransactionID("TXN-90003").size());
    }

    @Test
    void testRepositoryRejectsCorruptRows() throws Exception {
        CustomerRepository repo = new CustomerRepository();
        Path file = Path.of("data/runtime/accounts/customerDB.csv");
        String original = Files.readString(file, StandardCharsets.UTF_8);
        try {
            Files.writeString(file,
                    CUSTOMER_HEADERS.get(0) + "," + CUSTOMER_HEADERS.get(1) + "," + CUSTOMER_HEADERS.get(2)
                            + "," + CUSTOMER_HEADERS.get(3) + "," + CUSTOMER_HEADERS.get(4) + ","
                            + CUSTOMER_HEADERS.get(5) + "," + CUSTOMER_HEADERS.get(6) + "\r\n"
                            + "CUS-BAD,broken,row\r\n",
                    StandardCharsets.UTF_8);
            assertThrows(CsvDataException.class, repo::findAll,
                    "a short row must stop the read instead of returning partial data");
        } finally {
            Files.writeString(file, original, StandardCharsets.UTF_8);
        }
        assertTrue(repo.findByID("CUS-90001").isPresent(), "the restore brings the records back");
    }
}