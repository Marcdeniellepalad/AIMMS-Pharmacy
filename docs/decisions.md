# Project decisions

Updated November 20, 2026. The workflow baseline is the [Notion Version 2 overview](https://app.notion.com/p/41db0d06c25881c7ab8dea60b6034962). Keep proposals and open questions distinct from accepted direction.

## Accepted

- The product is a desktop pharmacy counter system.
- Pharmacists are the sellers and process sales.
- Administrators control pharmacist-submitted restock requests. Their approval action records the actual receipt details and adds a dated, lot-tracked batch to shared inventory.
- Several workstations share stock, so stock-changing work needs one coordinated write path.
- Prescriptions come from outside AIMMs; there is no internal doctor account or approval role.
- Java and CSV files are the course direction for backend save and retrieve operations, and the project must demonstrate the four OOP pillars.
- Every inventory batch and every prescription must have an expiry date.
- User passwords are stored as salted one-way password hashes, not reversible ciphers.
- Preserve completed sales when they are voided; record reversals as separate auditable events.

## Proposed for implementation

- Keep the documented Controller → Service → Repository → CSV Manager → CSV files separation.
- Store live CSV files under `data/runtime/`, separate from the tracked header templates.
- Use one central Java writer/service for all counters and a journal/recovery protocol for multi-file sale updates.
- Use the schemas in [CSV data model](csv-data-model.md), including prescription source references, partial-fill quantities, batch lot numbers, and explicit audit fields.

These are implementation proposals unless stated as accepted above. The current code does not yet implement the CSV manager or coordinated multi-file writes.

## Open

- Whether to keep the current Swing dashboard prototype or adopt another desktop UI toolkit.
- How counter workstations connect to the central writer and where it runs.
- The exact CSV journal and recovery protocol.
- Which prescription formats and medicine classes are accepted, and their validity/retention rules.
- How original prescription documents are protected and retained.
- Which balance/stock changes are allowed for a sale void.
