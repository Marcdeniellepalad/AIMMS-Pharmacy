# AIMMs Pharmacy system workflow

**Baseline:** [Notion “System Workflow Overview”](https://app.notion.com/p/41db0d06c25881c7ab8dea60b6034962), Version 2, recorded November 19, 2026. Version 1 remains archived in Notion. This file restates the workflow baseline; implementation details are marked as proposed elsewhere.

## Accounts and roles

- A customer self-registers and may use a stored account balance.
- A pharmacist account remains pending until an administrator approves it. The pharmacist is also the seller at the counter.
- An administrator account is created manually.
- AIMMs has no internal doctor account. Prescriptions come from an external prescriber and are checked by the pharmacist.

## Counter sale

1. The customer asks the pharmacist for medicine.
2. The pharmacist searches the active catalog, checks available stock, and enters the requested items and quantities.
3. OTC products do not require a prescription. Prescription products require an eligible prescription. The pharmacist processes the sale.

The project overview cites [Republic Act No. 10918](https://elibrary.judiciary.gov.ph//thebookshelf//showdocs/2/70354) and [Mercury Drug's online ordering flow](https://www.mercurydrug.com/orderonline.html) as context for pharmacist-led retail sales. Prescription format and medicine-class rules still need to be confirmed for the actual implementation.

## External prescription intake

The customer presents a paper prescription or an electronic copy only where current rules allow it for that medicine. Every prescription record must include a `validUntil` date. The pharmacist records the prescription and checks the patient, prescriber, medicine, strength, dosage form, directions, quantity, date, and relevant special requirements.

If it is unclear, appears altered, or needs correction, the pharmacist holds the sale and requests clarification or a corrected prescription. AIMMs must retain a reference to the submitted version and record clarifications; it must not silently edit what the prescriber issued.

Prescription states used by the project:

```text
RECEIVED → VERIFIED → PARTIALLY_DISPENSED → DISPENSED
    ├── NEEDS_CLARIFICATION
    ├── REJECTED
    └── EXPIRED (when the applicable rule says it is no longer valid)
```

For a partial fill, record the prescribed amount, each dispensed amount, the remaining quantity, date, pharmacist, and related sale. The remaining quantity is derived from prescribed quantity minus recorded dispenses.

The Notion overview cites [RA 10918, Sections 33 and 37](https://elibrary.judiciary.gov.ph//thebookshelf//showdocs/2/70354) for prescription-profile and record-retention context. It also notes that the 2020 [DOH/NPC e-prescription guidance](https://doh.gov.ph/wp-content/uploads/2023/08/mc2020-0016.pdf) was limited to its COVID-response context. Do not treat it as blanket current permission for every medicine. [Getmeds' prescription policy](https://getmeds.ph/prescription-policy) and [MedExpress ordering information](https://www.medexpress.com.ph/services/delivery) are examples of company practices, not universal legal rules.

## Inventory and restocking

- Keep stock by product and physical batch, including lot number, quantity, and a required expiration date. Reject batches without an expiration date.
- Use FEFO: select eligible stock with the earliest expiration first.
- Expired, quarantined, pulled, depleted, or otherwise blocked batches are unavailable for sale.
- A pharmacist can submit a restock request. An administrator approves it and records the delivered quantity, supplier lot, and expiration date. The recorded receipt creates a batch and updates available stock; approval without receipt details does not add stock.

FEFO is an AIMMs inventory policy, not a legal claim.

## Sale completion, cancellation, and voids

Before completion, the system checks item status, prescription validity and remaining quantity, eligible stock, price, quantity, and available customer balance.

On successful completion, AIMMs records the transaction and line items, deducts stock by batch, deducts the customer balance, updates prescription quantities, and writes audit records. These related changes must be recoverable if a write is interrupted.

- **Cancelled before completion:** record who cancelled, when, and why. Do not deduct stock or balance.
- **Voided after completion:** preserve the original sale. Record the void request and reason, apply any approved balance or stock adjustments, then record the reversal outcome. A failed reversal must remain pending or failed, never be logged as successful.

A failed, uncommitted attempt is not a void. A void reverses an already completed sale.

## Audit history

Logs should identify the actor, time, action, reason, outcome, and related record.

- Prescription history: receipt, verification, clarification, rejection, partial fills, and dispensing.
- Inventory history: restock requests, receipts by batch, sales, holds, releases, and adjustments.
- Transaction history: completion, pre-completion cancellation, void request, reversal, and outcome.
- Balance history: top-ups, purchases, refunds, and adjustments, with before-and-after balances.

Current-state records describe what is true now. Logs preserve what happened.

## Application flow

**Documented architecture:**

```text
User → Controller → Service (business rules) → Repository → CSV Manager → CSV files
```

The service layer owns workflow rules. Repositories provide save and retrieve operations. The CSV manager handles CSV formatting and file access. Shared-counter writes and multi-file recovery are requirements, not completed features; see [CSV storage and recovery](csv-storage-and-recovery.md). A Swing dashboard prototype currently exists; the final UI toolkit and workstation deployment shape remain open.

## Open rules

- Which prescription formats and medicine classes AIMMs will accept under current rules.
- Applicable validity and recordkeeping rules, especially for dangerous drugs.
- Which stock and balance adjustments are allowed for a completed-sale void.
- How the submitted prescription document is stored and protected.
