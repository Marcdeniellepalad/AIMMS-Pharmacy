# Project structure

```text
AIMMs-Pharmacy/
├── .agents/skills/        Team Codex skills
├── pom.xml                Maven build; Java 17
├── data/
│   ├── accounts/          Tracked CSV schema templates
│   ├── inventory/         Tracked CSV schema templates
│   ├── logs/              Tracked CSV schema templates
│   ├── prescriptions/     Tracked CSV schema templates
│   ├── transactions/      Tracked CSV schema templates
│   └── runtime/           Live CSV records; ignored by Git
├── docs/                  Workflow, schemas, and design notes
└── src/
    ├── main/java/
    │   ├── Main.java       Current Swing dashboard prototype
    │   ├── controller/    User interaction and request coordination
    │   ├── model/         Accounts, inventory, prescriptions, sales, and logs
    │   ├── security/      Password hashing helpers
    │   ├── repository/    Data access contracts and implementations
    │   │   └── csv/       CSV reading and writing
    │   └── service/       Pharmacy business rules and workflows
    └── test/java/         Automated tests
```

This structure follows the documented Controller → Service → Repository → CSV Manager flow. Keep folder names descriptive and avoid numbered package names. Maven and a Swing dashboard prototype are present. The full counter workflow, CSV manager, and coordinated multi-file writes remain future implementation work. The UI toolkit and workstation connection method still need a team decision.

The tracked `data/` CSV files contain headers only and act as schema templates. Application records belong under `data/runtime/`, never in those templates. Prescription source files must be stored outside Git.
