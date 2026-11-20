# AIMMs Pharmacy

AIMMs is a Java desktop counter system for a retail pharmacy. A pharmacist operates the counter, checks stock, verifies prescriptions received from outside AIMMs, and records sales.

## Accepted direction

- CSV files are the required persistence format for saving and retrieving project data.
- The application has no internal doctor account or doctor approval workflow.
- Inventory is tracked by product and physical batch, including lot number and expiration date; eligible stock follows FEFO.
- Several workstations share stock, so they must use one coordinated write path. Separate CSV copies on each workstation are not shared inventory.
- No database service such as Supabase is part of this direction.
- Passwords are stored as salted one-way PBKDF2-HMAC-SHA256 hashes, never plaintext or reversible ciphers.

## Project documents

- [System workflow](docs/system-workflow.md) — current workflow aligned with Notion Version 2.
- [CSV data model](docs/csv-data-model.md) — file headers, relationships, and status meanings.
- [CSV storage and recovery](docs/csv-storage-and-recovery.md) — requirements for safe shared writes.
- [Project structure](docs/project-structure.md) — where code, templates, and team skills belong.
- [Demo inventory price references](docs/sample-inventory-prices.md) — source notes for the synthetic startup catalog.

## Java structure

The documented flow is:

```text
User → Controller → Service → Repository → CSV Manager → CSV files
```

The Swing application has a staff sign-in screen, an administrator workspace, and a pharmacist counter workspace. CSV-backed pages load in the background and show loading skeletons. Catalog item creation writes through `ItemRepository`. Pharmacists can submit restock requests and complete OTC counter sales. Admins can approve restock requests while recording delivered quantity, lot number, and expiration; saving creates a batch and increases available stock. Sale and receipt updates use local recovery staging. Prescription-only items remain blocked at checkout until prescription verification and dispensing are connected. These local writes are for single-machine testing only; multi-workstation shared inventory still needs the documented central writer and hosting design. Prescription intake/review, customer balances, cancellation/voids, and several account-management actions are not implemented yet. Synthetic inventory starter records are kept in `data/sample/inventory/` and copied to ignored runtime files. See [project decisions](docs/decisions.md) and [CSV storage and recovery](docs/csv-storage-and-recovery.md) for accepted, proposed, and open items.

### Run with synthetic demo records

From the project folder, compile and launch the app with:

```powershell
mvn clean compile
java -cp target/classes Main --demo
```

Demo mode seeds the ignored `data/runtime/` folder when it is empty or contains only the unchanged synthetic starter inventory, then opens the staff sign-in screen. The catalog and batch records come from `data/sample/inventory/`; existing non-demo records are preserved.

- Admin: `admin`
- Pharmacist: `pharmacist`
- Password for both: `AIMMsDemo1!`

All sample names, accounts, prescriptions, sales, and inventory records are synthetic. Demo mode preserves existing non-demo data.

## Four OOP pillars

- **Encapsulation:** model state is held in private fields and exposed through methods.
- **Abstraction:** `User` is an abstract base type; repository contracts should keep business rules independent from CSV details.
- **Inheritance:** `Admin`, `Customer`, and `Pharmacist` share the common `User` fields.
- **Polymorphism:** each user subtype supplies its own role through `getRole()`.

Use inheritance only for genuine shared behavior; do not add artificial product or prescription subclasses just to demonstrate OOP.

## Data safety

Use synthetic examples only. Do not commit real customer, account, or prescription information. See [CSV storage and recovery](docs/csv-storage-and-recovery.md) before enabling multiple counters.
