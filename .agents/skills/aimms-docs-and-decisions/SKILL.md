---
name: aimms-docs-and-decisions
description: Create or update AIMMs Pharmacy project documentation and decision records from team notes. Preserve dated revisions and distinguish accepted decisions, proposals, and open questions.
---

# AIMMs Pharmacy documentation and decisions

Use this skill when recording or revising AIMMs Pharmacy system descriptions, technical decisions, or project notes.

## Project context

- The intended product is a desktop counter app used by pharmacists as sellers.
- Several workstations share the same stock records.
- Prescriptions are issued outside the system; do not add an internal doctor role unless the team changes this decision.
- The team wants readable documentation with a history that lets them return to earlier versions.

## How to document

- Label each item **Accepted**, **Proposed**, or **Open**. Do not turn a suggestion into a decision without evidence that the team accepted it.
- For a material revision, preserve the previous version and add a dated version entry with a short change summary.
- Write short sections, plain language, and define pharmacy or software terms when first used.
- Link requirements to their source or to the decision record that established them.
- When discussing Philippine law, regulations, or current practice, verify against current primary sources and cite the exact source. State uncertainty where the source does not settle the question.
- Keep implementation choices separate from pharmacy workflow rules so that a framework change does not silently change the intended business behavior.

## Output

Give the reader the current status, the decision or change, its reason, and any unresolved question. Keep summaries concise; put detailed rationale in the version history or references.
