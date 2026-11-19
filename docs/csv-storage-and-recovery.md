# CSV storage and recovery

CSV is the required save-and-retrieve format for this project. A CSV file does not provide database transactions or coordinate concurrent edits by itself.

The repository code should read and write under `data/runtime/`. The tracked CSV files at the top-level data paths are schema templates, not live records. Prescription source documents must stay outside Git as well.

## Inventory CSV repository API

The inventory persistence slice uses the existing public methods on `ItemRepository` and `BatchRepository`; callers should keep using those methods rather than opening CSV files directly. A missing runtime file is initialized with its schema header. Existing files must have the exact documented header and rows with the expected number of fields. Invalid values report the file and data-record number through `CsvDataException`.

- `ItemRepository`: `findAll()`, `findByID(String)`, `findByStatus(ItemStatus)`, `save(Item)`, and `update(Item)`.
- `BatchRepository`: `findAll()`, `findByID(String)`, `findByItemID(String)`, `findByStatus(BatchStatus)`, `save(Batch)`, and `update(Batch)`.
- `CsvFileManager`: `readRecords(Path, List<String>)` and `writeRecords(Path, List<String>, List<Map<String, String>>)`, for header-validated CSV data.

Inventory dates use ISO `yyyy-MM-dd`; enum values use their uppercase Java names; `prescriptionRequired` uses `true` or `false`. The helper replaces one file at a time. This does not provide coordinated multi-workstation writes or atomic updates across files, and must not be treated as the sale journal/recovery protocol described below.

## Shared-counter requirement

**Accepted requirement:** all workstations must use one coordinated write path for the shared stock. Do not let each workstation maintain and overwrite a separate copy of the inventory CSV files.

**Proposed design:** one central Java service owns the CSV files and serializes writes. The transport and hosting setup still need team confirmation.

## Sale consistency requirement

A sale can change transaction files, batch quantities, customer balance, prescription quantities, and several audit logs. Replacing each CSV file atomically is not enough to make the whole sale atomic.

Before enabling live sales, the CSV persistence layer must:

1. Serialize writes through the single writer.
2. Validate the full sale before changing files.
3. Stage every affected CSV update and record a durable operation/journal identifier.
4. Commit the staged set as one recoverable operation.
5. On startup, detect an incomplete operation and finish or restore it before accepting new sales.
6. Keep the original sale and report the recovery outcome in the audit log.

The exact journal format and commit protocol are not implemented yet. Do not describe separate temp-file moves as a complete multi-file transaction.

## Failure versus void

- If a sale never commits, it must not deduct stock or balance. Record a cancellation or failed attempt where an operation ID exists.
- If a completed sale is voided, preserve the completed transaction and record a separate, auditable reversal. A failed reversal remains pending or failed.

## Prescription document handling

`sourceReference` is a schema placeholder for the original paper scan or electronic submission. The team still needs to select protected storage, access controls, backup, and applicable retention rules. Never commit real prescription files to Git.
