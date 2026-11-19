# Demo inventory and price references

The files under `data/sample/inventory/` provide synthetic catalog and batch records for development. On startup, the app copies these sample records into `data/runtime/inventory/` only when the corresponding runtime file is missing. Existing runtime records are preserved. Runtime files are ignored by Git; the sample seed files are tracked so teammates get the same starter dataset.

The `price` values are PHP per tablet or capsule and use the **median listed retail price** for the matching generic ingredient, strength, and form. The comparison index says its latest snapshot covers **October 15–November 15, 2026** and that prices come from participating store sites; Mercury Drug values are reported by branches to the DOH Drug Price Watch. These figures are reference medians, not official SRPs, quotes from one specific store, or an endorsement. Confirm actual prices with the pharmacy before using them for real sales.

| Demo item | Price (PHP/unit) | Market reference |
| --- | ---: | --- |
| Paracetamol 500 mg tablet | 4.00 | Median of 19 store listings |
| Cetirizine 10 mg tablet | 16.00 | Median of 18 store listings |
| Ibuprofen 400 mg tablet | 4.00 | Median of 11 store listings |
| Ascorbic acid 500 mg tablet | 3.00 | Median of 16 store listings |
| Loperamide 2 mg capsule | 8.71 | Median of 19 store listings |
| Amoxicillin 500 mg capsule | 7.63 | Median of 18 store listings |
| Metformin 500 mg tablet | 5.75 | Median of 18 store listings |
| Losartan 50 mg tablet | 13.00 | Median of 19 store listings |

The item names use generic ingredient names, but the index medians combine store listings across brands for the matching ingredient, strength, and form. The item and lot identifiers, quantities, lot numbers, and expiry dates in these CSVs are fabricated for software demonstration. Prescription flags are included to exercise the POS workflow and do not substitute for checking local product registration or professional dispensing requirements.

## Sources

- [Philippine medicine price index](https://hanapmed.ph/price-index/) — per-unit low, median, and high prices; latest snapshot date stated above.
- [How the price comparisons are sourced](https://hanapmed.ph/about/) — explains store website inputs and the source of Mercury Drug branch prices.
- [DOH Drug Price Watch](https://dpw.doh.gov.ph/) — Department of Health price-checking service.
- [DOH response directing users to Drug Price Watch for medicine prices](https://www.foi.gov.ph/agencies/doh/pharmaceutical-market/) — official government reference for the public price service.
