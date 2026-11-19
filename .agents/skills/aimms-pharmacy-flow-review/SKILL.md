---
name: aimms-pharmacy-flow-review
description: Review AIMMs Pharmacy requirements or code changes against the agreed pharmacist counter workflow, shared stock, batch and expiry handling, prescription checks, and auditable sales.
---

# AIMMs Pharmacy workflow review

Use this skill when reviewing AIMMs Pharmacy requirements, designs, or code that affects a pharmacy workflow.

## Agreed context

- A pharmacist is the seller and operates the counter system.
- The system does not include an internal doctor role. Prescriptions come from outside the system, including possible emailed prescriptions.
- Multiple workstations use shared stock. They must read and write through one coordinated source of truth; separate local CSV files are not shared inventory.

## Review the flow

- Check whether prescription authenticity and required details are reviewed by the pharmacist before dispensing. Do not invent a definitive edit-detection method; flag the verification procedure as a policy or workflow decision if it is unspecified.
- Check how partial fills, remaining quantities, and later completion are represented. Flag missing rules instead of assuming approval or rejection is enough.
- Check batch-level stock: identify each batch by its attributes, but also require explicit rules for usable status, expiry, quarantine/recall, and FEFO selection. Attributes alone do not make a batch sellable or enforce FEFO.
- Check concurrent counters cannot sell the same final units twice. With CSV storage, require one central writer or another explicit serialization mechanism; clients must not independently overwrite shared files.
- Check that a sale's related CSV records are saved consistently. Multiple CSV file writes are not automatically atomic. Require a staged-write and recovery plan, or another design that prevents an interrupted save from appearing partly completed.
- Distinguish a failed, uncommitted attempt from a pharmacist voiding a completed sale. A void is an audited reversal: record who acted, when, why, what was reversed, and whether the reversal succeeded. Never silently delete a completed sale.
- Check that user identity and an audit trail support accountability for sales, stock adjustments, prescription decisions, and voids.

## Report findings

For each issue, state the workflow step, what is missing or inconsistent, why it matters, and a concrete correction. Separate confirmed requirements from proposed rules and questions for the team. If a finding depends on current Philippine law or regulation, browse primary sources and cite them; do not present an implementation preference as a legal requirement.
