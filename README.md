# Tap Ledger

**Tap Ledger** is a personal, offline-first Android monthly budget and expense tracking application designed for one user.

Its primary purpose is to capture bank/UPI payment notifications quickly, classify spending with minimal interaction, maintain accurate account balances independently from budget categories, reconcile the app against bank statements, and close each month with controlled carry-forward/advance handling.

---

## Key Architectural Principles

1. **Transaction = Financial Fact**  
   Represents where money actually moved (e.g. HDFC debit ₹850, SBI credit ₹10,000, card bill payment ₹5,000). Categories belong in `transaction_allocations`, not on `transactions`.
2. **Account Balance**  
   Tracks real money movements: `opening_balance + all valid incoming - all valid outgoing`. Includes `PENDING` and `CATEGORIZED`; excludes `IGNORED` and `VOIDED`.
3. **Paise-Only Money Representation**  
   All monetary values are stored strictly as `Long` in paise (₹1,234.56 = `123456L`). Floating-point arithmetic (`Double`, `Float`) is strictly forbidden for financial calculations.
4. **Asia/Kolkata Display & Budget Boundaries**  
   Budget months and boundaries are calculated in `Asia/Kolkata` using epoch milliseconds.
5. **Credit Card Sign Convention**  
   Asset accounts (Bank, Cash): positive = money owned.  
   Credit-card accounts: negative = liability owed (-₹8,000). Purchases increase liability (-₹9,000); bill payments decrease liability (-₹4,000).
6. **Monthly Category Snapshots**  
   Category configurations are snapshotted in `budget_allocations` for each budget period so edits to current categories never distort historical periods.
7. **Strictly Offline & Privacy Preserved**  
   No `INTERNET` permission is declared. Transitive manifests are checked in CI.
8. **Safe Database Backups**  
   Backups use SQLite `VACUUM INTO` to safely checkpoint WAL mode before export. Restores enforce schema checks and `PRAGMA integrity_check`.

---

## Tech Stack

| Area                  | Technology                                  |
| --------------------- | ------------------------------------------- |
| Language              | Kotlin 2.0                                  |
| UI                    | Jetpack Compose + Material 3                |
| Architecture          | MVVM + Repository                           |
| Database              | Room 2.6.1 + SQLite (Schema Export Enabled) |
| Dependency Injection  | Hilt 2.52                                   |
| Annotation Processing | KSP                                         |
| Min / Target SDK      | 29 / 35                                     |
| Async                 | Coroutines & Flow                           |
| Background Jobs       | WorkManager                                 |
| Timezone              | `Asia/Kolkata`                              |
| Monies                | `Long` paise                                |

---

## Directory Structure

```text
Tap Ledger/
├── .github/workflows/
│   └── android.yml                # CI testing and manifest INTERNET check
├── app/
│   ├── src/main/
│   │   ├── java/com/Tap Ledger/
│   │   │   ├── core/
│   │   │   │   ├── database/     # Tap LedgerDatabase, Entities, DAOs, Converters, Seeder
│   │   │   │   ├── model/        # Enums, MoneyFormatter (paise & lakh formatting)
│   │   │   │   └── util/         # DateTimeUtils (Asia/Kolkata epoch calculation)
│   │   │   ├── data/
│   │   │   │   ├── backup/       # BackupManager (VACUUM INTO & PRAGMA check)
│   │   │   │   ├── repository/   # Account, Budget, Transaction Repositories
│   │   │   │   └── statement/    # StatementReconciliationEngine (1-to-1 matching)
│   │   │   ├── domain/
│   │   │   │   ├── calculator/   # AccountBalanceCalculator & BudgetCalculator
│   │   │   │   ├── model/        # Summaries & Net Worth models
│   │   │   │   ├── monthclose/   # MonthCloseEngine (advance policies, locks)
│   │   │   │   └── validator/    # DataIntegrityChecker
│   │   │   ├── parser/           # Pure Kotlin FinancialNotificationParser
│   │   │   ├── service/          # FinancialNotificationListenerService
│   │   │   └── ui/               # Compose Material 3 screens, theme, navigation
│   └── src/test/
│       └── java/com/Tap Ledger/   # Unit test suite (Money, Dates, Calculators, Parser, Reconciliation)
├── decisions.md                  # Architectural decision log (D001-D010)
└── build.gradle.kts / settings.gradle.kts
```

---

## Milestone Status

- [x] **M0 — Project Skeleton**: Gradle 8.7 / Kotlin 2.0 / Compose / Material 3 / Hilt / Room setup, hardened manifest, CI workflow.
- [x] **M1 — Database & Financial Foundation**: Room 13-entity schema, strict `Long` paise arithmetic, Asia/Kolkata month logic, seed data (Needs ₹36k, Wants ₹8k, Savings ₹6k, Unallocated ₹2k).
- [x] **M2 — Manual Transactions & Integrity**: Atomic repository boundaries, split sums, transfer legs, refund links, void/restore, Data Integrity Checker.
- [x] **M3 — Dashboard**: Monthly income/spending overview, account balances, net worth, category progress bars.
- [x] **M3.5 — Backup/Restore Gate**: `VACUUM INTO` database snapshots, `PRAGMA integrity_check`, pre-restore safety backups.
- [x] **M4 — Pure Kotlin Parser**: Anonymized regex engines for HDFC, SBI, HDFC CC, GPay; OTP & promo filters; Avl Bal extraction; UPI reversals.
- [x] **M5 — Listener & Recovery**: `NotificationListenerService`, active notification recovery, Level 0 & Level 1 deduplication, listener heartbeat.
- [x] **M6 — Categorization Flow**: Quick Needs/Wants/Savings bucket chips, category selection, split transactions.
- [x] **M7 — Pending Inbox**: Pending transactions list affecting account balances, non-destructive dismissal.
- [x] **M8 — Statement Import & Reconciliation**: 1-to-1 reconciliation matching, arithmetic verification, missing-in-app detection.
- [x] **M9 — Month Close**: Period locking, `advance_ledger` carry-forward, negative advance overspend capture.
- [x] **M10 — Data Integrity**: Relational checks, transfer balance validation, audit log.
- [x] **M11 — Category & Budget Management**: Bucket allocations, advance rules, intra-month budget transfers, and category management UI.
- [x] **M12 — Xiaomi 15 / HyperOS Reliability**: Setup checklist, autostart/battery optimization guidance, universal settings fallbacks.

---

## Testing & Notification Simulation

### 1. Running Unit Tests

To execute all local unit tests (parsers, calculators, validators, and reconciliation engine):

```bash
./gradlew testDebugUnitTest
```

### 2. Enabling Notification Listener Permission

To enable notification capturing on a connected device or emulator:

- Navigate to **Settings** $\rightarrow$ **Apps** $\rightarrow$ **Special App Access** $\rightarrow$ **Notification Access** $\rightarrow$ Enable **Tap Ledger**.

### 3. Simulating Financial Notifications via ADB

You can push simulated bank notifications directly to an emulator or connected device using `adb shell cmd notification post`:

#### HDFC Bank UPI Debit

```bash
adb shell 'cmd notification post -t "HDFC Bank" tag "Sent Rs. 850.00 from HDFC Bank A/C **1234 to SWIGGY on 08-10-26 via UPI Ref 428192837482. Avl Bal: Rs 49,150.00"'
```

#### SBI UPI Debit

```bash
adb shell 'cmd notification post -t "SBI" tag "Dear UPI user A/C 5678 debited by 1,200.00 on 08Oct26 tran id 429182374928"'
```

#### HDFC Credit Card Purchase

```bash
adb shell 'cmd notification post -t "HDFC Bank Alert" tag "Alert: Rs. 2,000.00 spent on your HDFC Bank Card ending 9012 at AMAZON on 08-OCT-26"'
```

#### Google Pay Payment

```bash
adb shell 'cmd notification post -t "Google Pay" tag "Paid ₹850 to Swiggy"'
```

#### UPI Transaction Reversal / Refund

```bash
adb shell 'cmd notification post -t "HDFC Bank" tag "Rs. 500.00 credited back to your A/C 1234. UPI transaction failed/reversed on 08-10-26"'
```

#### OTP / Non-Financial Notification (Filtered Out)

```bash
adb shell 'cmd notification post -t "HDFC Bank" tag "123456 is your secret OTP for transaction of Rs. 500.00. Do not share."'
```
