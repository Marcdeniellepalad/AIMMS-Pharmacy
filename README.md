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

## Java structure

The documented flow is:

```text
User → Controller → Service → Repository → CSV Manager → CSV files
```

The Maven project currently includes a Swing dashboard prototype, domain models, and repository placeholders. The full counter workflow, CSV manager, and coordinated multi-file persistence are not implemented yet. See [project decisions](docs/decisions.md) for what is accepted, proposed, and still open.

## Four OOP pillars

- **Encapsulation:** model state is held in private fields and exposed through methods.
- **Abstraction:** `User` is an abstract base type; repository contracts should keep business rules independent from CSV details.
- **Inheritance:** `Admin`, `Customer`, and `Pharmacist` share the common `User` fields.
- **Polymorphism:** each user subtype supplies its own role through `getRole()`.

Use inheritance only for genuine shared behavior; do not add artificial product or prescription subclasses just to demonstrate OOP.

## Data safety

Use synthetic examples only. Do not commit real customer, account, or prescription information. See [CSV storage and recovery](docs/csv-storage-and-recovery.md) before enabling multiple counters.
