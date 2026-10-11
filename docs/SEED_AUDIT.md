# TapLedger Seed & Demo Data Audit

This document inventories all occurrences of seed, demo, and sample data across the TapLedger codebase in accordance with Flow.md and prompt requirements.

## Classification Policy
- **MONETARY (remove)**: Any code, initial data loader, hardcoded opening balance, income, account, budget period, transaction, allocation, or seed method present in production execution paths (`src/main`). These must be completely removed from release builds.
- **REFERENCE (keep)**: System reference categories (e.g. system categories, default category names/buckets), SQLite trigger lifecycle callbacks (`RoomDatabase.Callback`, `onCreate`), parsing dialect strings/supported bank names in docstrings, regex test fixtures in `src/test`, and property/unit test datasets in `src/test`.

---

## Complete Repository Inventory

| File : Line | Pattern / Value | Classification | Action / Notes |
| :--- | :--- | :---: | :--- |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:22-140` | `seedInitialDataIfEmpty()`, demo accounts, income ₹52,000, allocations | **MONETARY** | **REMOVE**. Eliminates hardcoded HDFC, SBI, Credit Card (-₹8k), Cash (₹2k), and initial allocations. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:41` | `name = "HDFC Savings"` (₹50,000) | **MONETARY** | **REMOVE**. Hardcoded bank account. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:44` | `openingBalancePaise = 50_000_00L` | **MONETARY** | **REMOVE**. Hardcoded opening balance. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:50` | `name = "SBI Savings"` (₹10,000) | **MONETARY** | **REMOVE**. Hardcoded bank account. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:53` | `openingBalancePaise = 10_000_00L` | **MONETARY** | **REMOVE**. Hardcoded opening balance. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:59` | `name = "HDFC Credit Card"` (-₹8,000) | **MONETARY** | **REMOVE**. Hardcoded credit card account. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:62` | `openingBalancePaise = -8_000_00L` | **MONETARY** | **REMOVE**. Hardcoded credit card opening balance. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:68` | `name = "Cash"` (₹2,000) | **MONETARY** | **REMOVE**. Hardcoded cash account. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:71` | `openingBalancePaise = 2_000_00L` | **MONETARY** | **REMOVE**. Hardcoded cash opening balance. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:81-96` | Initial category budget amounts (₹36k, ₹8k, ₹6k) | **MONETARY** | **REMOVE**. Hardcoded budget amounts. Category metadata (names & buckets) is preserved as reference data for O4 distribution. |
| `app/src/main/java/com/tapledger/core/database/DatabaseSeeder.kt:116` | `incomePaise = 52_000_00L` | **MONETARY** | **REMOVE**. Hardcoded period income. |
| `app/src/main/java/com/tapledger/ui/screens/dashboard/DashboardViewModel.kt:56` | `private val seeder: DatabaseSeeder` | **MONETARY** | **REMOVE**. Seeder injection in production ViewModel. |
| `app/src/main/java/com/tapledger/ui/screens/dashboard/DashboardViewModel.kt:71-72` | `seeder.seedInitialDataIfEmpty()` in `init` | **MONETARY** | **REMOVE**. Execution of monetary demo seed on first launch. |
| `app/src/main/java/com/tapledger/data/repository/BudgetRepository.kt:39` | `defaultIncomePaise: Long = 52_000_00L` | **MONETARY** | **REMOVE**. Default income parameter must be `0L` if uninitialized. |
| `app/src/main/java/com/tapledger/core/database/DatabaseTriggers.kt:292-301` | `RoomDatabase.Callback`, `onCreate(db)` | **REFERENCE** | **KEEP**. SQLite trigger creation lifecycle callback. Does not seed any data. |
| `app/src/main/java/com/tapledger/di/DatabaseModule.kt:34-42` | `Room.databaseBuilder.addCallback(DatabaseTriggers.Callback)` | **REFERENCE** | **KEEP**. Room database configuration. Does not seed data. |
| `app/src/main/java/com/tapledger/ui/MainActivity.kt:18-19` | `onCreate(savedInstanceState: Bundle?)` | **REFERENCE** | **KEEP**. Android Activity lifecycle. |
| `app/src/main/java/com/tapledger/data/statement/StatementCsvParser.kt:14` | Docstring: `Supports HDFC, ICICI, SBI, Axis...` | **REFERENCE** | **KEEP**. Documentation comment of supported bank statement formats. |
| `app/src/main/java/com/tapledger/core/model/MoneyFormatter.kt:87, 118` | Docstring examples: `15000000L paise -> "₹1,50,000.00"` | **REFERENCE** | **KEEP**. KDoc formatting documentation. |
| `app/src/test/**` | Unit test fixtures in `src/test` (e.g. `MigrationTest`, `IngestionPipelineTest`, `TransferPairingTest`) | **REFERENCE** | **KEEP**. Test fixtures verifying parser, attribution, and migration behavior. Absent from release variant. |

---

## Summary of Removals for Phase 1
1. Remove `DatabaseSeeder.kt` from production (`app/src/main`).
2. Remove `seeder.seedInitialDataIfEmpty()` and `DatabaseSeeder` injection from `DashboardViewModel.kt`.
3. Change default income in `BudgetRepository.getOrCreateBudgetPeriod` from `52_000_00L` to `0L`.
4. Ensure a fresh installation leaves zero accounts, zero opening balances, zero transactions, zero budget periods, zero budget allocations, zero advance ledger records, and zero month closes.
