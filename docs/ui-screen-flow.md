# AIMMs Pharmacy screen flow

This screen map translates the existing [system workflow](system-workflow.md) into desktop navigation. The business workflow already exists; this document adds a screen-level path for the Person 2 workflow and screen work.

## Main navigation

```text
Sign in
├── Administrator workspace
│   └── Admin overview
│       ├── Inventory items
│       ├── Stock batches
│       ├── Restock requests
│       ├── Prescriptions
│       ├── Transactions
│       ├── Customers
│       ├── Pharmacist accounts
│       ├── Admin accounts
│       └── Audit & inventory logs
└── Pharmacist workspace
    ├── Pharmacist dashboard
    ├── Counter sale
    ├── Inventory items
    ├── Stock batches
    ├── Restock requests
    ├── Prescriptions
    ├── Transactions
    ├── Customers
    └── Audit & inventory logs
```

The current sale screen supports a local cart preview and quantity changes. The review action does not commit a sale, change stock, dispense a prescription, adjust a balance, or write audit history. The restock form is also a preview. These actions remain read-only until the shared write workflow is implemented.

## Workflow paths

```text
Counter sale
├── Search the active catalog and add available stock to the cart
├── Prescription medicine → check the external prescription before dispensing
├── Review quantities and estimated total
└── Preview only → no transaction or stock write is performed

Restock request
├── Enter the medicine, requested quantity, and reason
└── Preview only → no request is saved

Admin overview
├── Review pending pharmacist accounts and recent activity
├── Pharmacist accounts → inspect account details
├── Admin accounts → view manually created administrator accounts
└── Audit & inventory logs → filter recorded events; history remains read-only
```

## Important screen behavior

- Catalog item status and batch status are distinct. A batch is available only when its product and batch are active, its quantity is positive, and it has not expired.
- Batch views sort eligible stock by earliest expiration date (FEFO). Blocked batches remain visible with their reason and cannot be selected for sale.
- A receipt cannot be recorded without a lot number and expiration date. Partial receipts create separate batch records and update the same restock request.
- Prescription verification uses externally issued details and a required `validUntil` date. The app records clarifications; it does not silently change the submitted prescription.
- A sale is committed only after all checks pass. A pre-completion cancellation does not deduct stock or balance. Voids preserve the original completed sale and record a separate reversal outcome.
- Administrators can approve pending pharmacist accounts. The current workflow does not define a rejection path or additional verification fields, so the mockups do not invent either.
- Log screens are read-only views of recorded history. Filters help find an event; they do not edit or remove it.
- While a CSV file is being read, keep the destination page layout visible with gray skeleton rows and a concise loading label. During initial startup, show that pharmacy records are loading. If a read fails, show an error and retry action; do not show a failed or unfinished read as an empty list.

## Role notes and unresolved decisions

- Pharmacists process counter sales, verify prescriptions, and submit restock requests.
- Authorized staff record delivered stock and batch details. The team has not decided whether a separate restock approval step is needed, so the proposed screens contain no approve or reject action.
- The exact prescription formats, medicine classes, record-retention rules, void adjustment rules, and multi-workstation deployment remain open in [project decisions](decisions.md). The screens should not imply those policies are settled.

## First visual review set

The mockups cover inventory work, administrator account review and audit, plus startup and page loading states. The Swing app now has data-backed screens for these areas and the related customer, prescription, and transaction records. It loads records asynchronously and shows skeletons while reading CSV files. Catalog item creation is connected to `ItemRepository`; approval, sale, and restock receipt writes still need coordinated workflow services. The selected sidebar row uses solid maroon with white icon and label; primary buttons with maroon backgrounds use white text.
