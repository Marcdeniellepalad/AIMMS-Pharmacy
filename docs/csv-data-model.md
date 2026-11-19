# CSV data model

**Status: proposed schema.** The fields below turn the Notion workflow into a file layout; the workflow requirements are the baseline, while these exact field names and extra data fields are implementation proposals for team review. They are not legal conclusions. These tracked CSV files are templates and currently contain headers only. Live application records are planned for `data/runtime/`, which is ignored by Git. Use synthetic data in examples; never add real customer or prescription records to the repository.

## Accounts

| File | Header |
| --- | --- |
| `data/accounts/adminDB.csv` | `adminID,username,passwordHash,fullName,status,dateCreated` |
| `data/accounts/pharmacistDB.csv` | `pharmacistID,username,passwordHash,fullName,status,dateRegistered,dateApproved` |
| `data/accounts/customerDB.csv` | `customerID,username,passwordHash,fullName,balance,status,dateRegistered` |

`passwordHash` contains a salted PBKDF2-HMAC-SHA256 hash, never a plaintext or reversibly encrypted password. Customer balance is current state; `balanceLogs.csv` is the history.

There is no doctor account file. External prescriber information belongs to a prescription record and does not create an AIMMs login.

## Catalog and inventory

| File | Header |
| --- | --- |
| `data/inventory/itemsDB.csv` | `itemID,itemName,category,price,prescriptionRequired,status` |
| `data/inventory/batchDB.csv` | `batchID,itemID,lotNumber,quantity,expirationDate,dateReceived,status` |
| `data/inventory/restockRequests.csv` | `requestID,itemID,quantityRequested,quantityReceived,requestedByPharmacistID,dateRequested,receivedByID,dateReceived,status,details` |

Each batch belongs to one catalog item and must have an expiration date; batches without one are rejected. A batch is sellable only when its item is active and the batch is active, has positive quantity, and has not expired. `QUARANTINED` stock is blocked. FEFO orders otherwise eligible batches by earliest expiration date. `quantityReceived` on a restock request can accumulate across receipts; each receipt still creates its own batch and inventory log.

Batch status values: `ACTIVE`, `DEPLETED`, `EXPIRED`, `QUARANTINED`, `PULLED`.

Restock request status values: `REQUESTED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`. Whether a request needs a separate approval state is still open.

## Prescriptions

| File | Header |
| --- | --- |
| `data/prescriptions/prescriptionsDB.csv` | `prescriptionID,customerID,patientName,prescriberName,prescriberLicenseNumber,prescriptionDate,dateReceived,validUntil,sourceType,sourceReference,status` |
| `data/prescriptions/prescriptionItemsDB.csv` | `prescriptionItemID,prescriptionID,itemID,medicineName,strength,dosageForm,directions,quantityPrescribed,quantityDispensed` |

`validUntil` is required for every prescription. `sourceType` is `PAPER`, `EMAIL`, or `OTHER_ELECTRONIC`. `sourceReference` identifies the preserved submitted document; the team still needs to choose its protected storage and retention approach. `quantityRemaining` is derived as `quantityPrescribed - quantityDispensed`.

Prescription status values: `RECEIVED`, `VERIFIED`, `NEEDS_CLARIFICATION`, `PARTIALLY_DISPENSED`, `DISPENSED`, `REJECTED`, `EXPIRED`, `CANCELLED`.

Each prescription line references a catalog item. Partial dispensing is recorded on the line and in prescription logs; transaction items also retain the linked prescription-line ID and sale details.

## Transactions

| File | Header |
| --- | --- |
| `data/transactions/transactionsDB.csv` | `transactionID,customerID,pharmacistID,totalAmount,status,dateTime` |
| `data/transactions/transactionItemsDB.csv` | `transactionItemID,transactionID,itemID,batchID,prescriptionItemID,quantity,unitPrice,subtotal` |

Transaction status values remain `COMPLETED` and `CANCELLED`. A void does not erase or relabel the completed sale: it is represented by transaction log events and separately recorded adjustments.

Each sale line records the batch actually used. This preserves FEFO and lot traceability in the transaction history. `prescriptionItemID` is blank for non-prescription items.

## Audit logs

Every log row includes the actor, time, action, reason, outcome, and related record. `details` carries any additional readable context.

| File | Header |
| --- | --- |
| `data/logs/inventoryLogs.csv` | `logID,batchID,itemID,actorID,actorRole,action,quantity,dateTime,reason,outcome,relatedRecordID,details` |
| `data/logs/batchLogs.csv` | `logID,batchID,itemID,actorID,actorRole,action,dateTime,reason,outcome,relatedRecordID,details` |
| `data/logs/prescriptionLogs.csv` | `logID,prescriptionID,actorID,actorRole,action,dateTime,reason,outcome,relatedRecordID,details` |
| `data/logs/transactionLogs.csv` | `logID,transactionID,customerID,actorID,actorRole,action,dateTime,reason,outcome,relatedRecordID,details` |
| `data/logs/balanceLogs.csv` | `logID,customerID,actorID,actorRole,action,amount,balanceBefore,balanceAfter,dateTime,reason,outcome,relatedRecordID,details` |

Use `SYSTEM` as the actor for automatic events such as expiration. For a void, preserve the original transaction and log the request, approval if required, reversal, and success or failure.
