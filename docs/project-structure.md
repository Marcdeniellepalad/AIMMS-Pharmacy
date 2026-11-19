# Project structure

```text
AIMMs-Pharmacy/
├── .agents/skills/        Team Codex skills
├── pom.xml                Maven build; Java 17
├── data/
│   ├── accounts/          Tracked CSV schema templates
│   ├── inventory/         Tracked CSV schema templates
│   ├── sample/inventory/  Synthetic startup catalog and batch records
│   ├── logs/              Tracked CSV schema templates
│   ├── prescriptions/     Tracked CSV schema templates
│   ├── transactions/      Tracked CSV schema templates
│   └── runtime/           Live CSV records; ignored by Git
├── docs/                  Workflow, schemas, and design notes
└── src/
    ├── main/java/
    │   ├── Main.java       Application launcher
    │   ├── ui/             Screen-based Swing workspace
    │   ├── controller/    User interaction and request coordination
    │   ├── model/         Accounts, inventory, prescriptions, sales, and logs
    │   ├── security/      Password hashing helpers
    │   ├── repository/    Data access contracts and implementations
    │   │   └── csv/       CSV reading and writing
    │   └── service/       Pharmacy business rules and workflows
    └── test/java/         Automated tests
```

This structure follows the documented Controller → Service → Repository → CSV Manager flow. Keep folder names descriptive and avoid numbered package names. Maven and the Swing screen workspace are present. The full counter workflow and coordinated multi-file writes remain future implementation work. The workstation connection method still needs a team decision.

The tracked schema CSV files contain headers only and act as templates. Synthetic inventory seed records live under `data/sample/inventory/` and are copied to ignored runtime files on first startup. Demo mode can also seed other synthetic runtime records for screen review. Application records belong under `data/runtime/`, never in the schema templates. Prescription source files must be stored outside Git.
