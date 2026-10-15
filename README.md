# TapLedger

**TapLedger** is a personal, strictly offline Android monthly budget and expense tracking application designed for privacy-focused, single-user financial management.

It automatically captures bank and UPI payment notifications, classifies spending with minimal effort, maintains precise account balances independently from budget categories, reconciles transactions against bank/card statements (PDF & CSV), and closes each budget month with controlled advance carry-forward handling.

---

## Key Architectural Principles

1. **Transaction = Financial Fact**  
   Represents where real money actually moved (e.g., HDFC debit ₹850, SBI credit ₹10,000, card bill payment ₹5,000). Categories belong in `transaction_allocations`, not on `transactions`.
2. **Account Balance Calculation**  
   Tracks real money movements: `opening_balance + all valid incoming - all valid outgoing`. Includes `PENDING` and `CATEGORIZED`; excludes `IGNORED` and `VOIDED`.
3. **Paise-Only Money Representation**  
   All monetary values are stored strictly as `Long` in paise (₹1,234.56 = `123456L`). Floating-point arithmetic (`Double`, `Float`) is strictly forbidden for financial calculations.
4. **Asia/Kolkata Display & Budget Boundaries**  
   Budget months and boundaries are calculated in `Asia/Kolkata` time using epoch milliseconds.
5. **Credit Card Sign Convention**  
   Asset accounts (Bank, Cash): positive = money owned.  
   Credit card accounts: negative = liability owed (-₹8,000). Purchases increase liability (-₹9,000); bill payments decrease liability (-₹4,000).
6. **Monthly Category Snapshots**  
   Category configurations are snapshotted in `budget_allocations` for each budget period so edits to current categories never distort historical periods.
7. **Strictly Offline & Privacy Preserved**  
   No `INTERNET` permission is declared. Transitive manifests are validated in CI to prevent accidental network leaks.
8. **Safe Encrypted Database Backups**  
   Backups use SQLite `VACUUM INTO` to safely checkpoint WAL mode before export. Restores enforce schema checks and `PRAGMA integrity_check`.

---

## Tech Stack & Core Libraries

| Area                     | Technology                                                   |
| ------------------------ | ------------------------------------------------------------ |
| **Language**             | Kotlin 2.0                                                   |
| **UI Framework**         | Jetpack Compose + Material 3                                 |
| **Architecture**         | MVVM + Clean Repository Pattern                              |
| **Database**             | Room 2.6.1 + SQLite (KSP schema export & migration triggers) |
| **Dependency Injection** | Hilt 2.52                                                    |
| **Cryptography**         | Argon2id (BouncyCastle `bcprov-jdk18on`) + Android KeyStore  |
| **PDF Processing**       | Pure local `pdfbox-android` (Offline PDF statement parsing)  |
| **Async & Background**   | Kotlin Coroutines 1.9.0, Flow, WorkManager 2.10.0            |
| **Min / Target SDK**     | 29 / 35                                                      |
| **Timezone & Currency**  | `Asia/Kolkata` / `Long` paise arithmetic                     |

---

## Core Features & Modules

### 1. Automated Financial Notification Ingestion

- **Listener Service**: Runs `FinancialNotificationListenerService` to capture bank and UPI notification events in real time.
- **Regex Parser Engine**: Anonymized regex engines for major Indian banks and payment apps (HDFC, SBI, ICICI, Axis, GPay, PhonePe, Paytm).
- **Smart Filtering & Deduplication**: Automatically filters out OTPs, promotional messages, and non-financial alerts. Uses multi-level fingerprint deduplication.
- **Liveness Heartbeat**: Includes `NotificationListenerLivenessWorker` for OEM reliability and automatic rebind recovery (e.g., Xiaomi/HyperOS).

### 2. Pending Inbox & One-Tap Categorization

- **Triage Flow**: Quick inbox for newly captured transactions.
- **50/30/20 Bucket Chips**: One-tap assignment to Needs, Wants, or Savings buckets.
- **Split Transactions**: Allocate a single transaction across multiple categories.

### 3. Account Balances & Net Worth Tracking

- **Multi-Account Types**: Bank Accounts, Credit Cards, Cash, and Savings.
- **Assets vs. Liabilities**: Visual composition card breaking down positive asset balances vs. credit card liabilities.
- **Net Worth Trends**: Real-time calculation of overall net worth over time.

### 4. 50/30/20 Envelope Budgeting & Advance Rollover

- **Monthly Budget Allocation**: Bucket allocation progress bars (`EnvelopeBulletBarRow`) and category distribution donuts (`CategoryDonutCard`).
- **Intra-Month Transfers**: Shift budget funds between categories mid-month without breaking historical integrity.
- **Month Close Engine**: Locks past periods and carries forward unused bucket advances or negative overspend based on custom rules.

### 5. PDF & CSV Statement Import & Reconciliation

- **Offline PDF Parsing**: Extract bank and credit card statement lines locally using `pdfbox-android`.
- **Automated 1-to-1 Reconciliation**: Matches statement records against app transactions with arithmetic verification and missing-transaction detection.
- **Reconciliation Bridge**: Visual overview comparing statement ending balances with calculated app balances.

### 6. App Security & Argon2id MPIN Lock

- **Argon2id Key Derivation**: High-security password hashing using Argon2id via BouncyCastle.
- **Android KeyStore Integration**: Master keys and salts stored securely in `AndroidMpinKeystore`.
- **Configurable Auto-Lock**: Custom inactivity timeout policies and biometric/MPIN lock screen.
- **Step-Up Authentication**: Requires MPIN verification for sensitive actions like backup restores or data resets.

### 7. Custom Compose Canvas Chart Engine

- **Pure Compose Graphics**: Built with custom `Canvas` drawing routines (no external charting dependencies).
- **Chart Types**:
  - Burn-Up / Projection Line Charts with gradient fills (`DashboardBurnUpCard`).
  - Six-Period Bar Trend Charts (`DashboardSixPeriodTrendCard`).
  - Donut & Ring Charts (`DashboardBucketRingsCard`, `CategoryDonutCard`).
  - Daily Spending Heatmaps (`SpendingHeatmapCard`).
  - Sparklines and Waterfall Charts.
- **Accessibility Support**: Interactive crosshair tooltips (`ChartTooltip`) and pattern/texture overlays (`ChartPatternLegendSheet`) for colorblind accessibility.

### 8. Data Diagnostics & Backup / Restore Safety

- **Integrity Checker**: Built-in `DataIntegrityChecker` to detect relational mismatches, orphaned allocations, or unverified balances.
- **Safe Encrypted Backups**: Uses SQLite `VACUUM INTO` snapshots with `PRAGMA integrity_check`.
- **Restore Notification Buffer**: Spools incoming notifications during backup restores to prevent data loss.

---

## Directory Structure

```text
TapLedger/
├── .github/workflows/
│   └── android.yml                # CI testing and manifest INTERNET prohibition check
├── app/
│   ├── src/main/
│   │   ├── java/com/tapledger/
│   │   │   ├── core/
│   │   │   │   ├── database/     # TapLedgerDatabase, Entities, DAOs, Migrations, Triggers
│   │   │   │   ├── model/        # Enums, MoneyFormatter (paise & lakh formatting)
│   │   │   │   ├── security/     # Argon2id MPIN Hasher, KeyStore, Lock Coordinator & Policy
│   │   │   │   └── util/         # DateTimeUtils (Asia/Kolkata epoch calculation)
│   │   │   ├── data/
│   │   │   │   ├── backup/       # BackupManager (VACUUM INTO & PRAGMA check), RestoreBuffer
│   │   │   │   ├── repository/   # Account, Budget, Transaction, Diagnostics Repositories
│   │   │   │   └── statement/    # StatementPdfParser, StatementCsvParser, Reconciliation Engine
│   │   │   ├── di/               # Hilt Dependency Injection Modules
│   │   │   ├── domain/
│   │   │   │   ├── calculator/   # AccountBalanceCalculator & BudgetCalculator
│   │   │   │   ├── monthclose/   # MonthCloseEngine (advance policies, period locks)
│   │   │   │   └── validator/    # DataIntegrityChecker
│   │   │   ├── parser/           # FinancialNotificationParser & NotificationEvent
│   │   │   ├── service/          # FinancialNotificationListenerService, Heartbeat Worker
│   │   │   └── ui/
│   │   │       ├── charts/       # Canvas Chart Builders, Renders, Tooltips, Pattern Legends
│   │   │       ├── components/   # Shared UI Cards, Money Text, Status Badges, Skeletons
│   │   │       ├── navigation/   # TapLedgerNavGraph, MainScaffold, MainViewModel
│   │   │       ├── screens/      # Dashboard, Pending Inbox, Transactions, Accounts,
│   │   │       │                 # Categories, Statement Reconciliation, Month Close,
│   │   │       │                 # MPIN Lock, Backup/Restore, Data Diagnostics, Insights
│   │   │       └── theme/        # Color, Type, Spacing, Motion, Chart Palette
│   └── src/test/
│       └── java/com/tapledger/   # Unit test suite (Money, Dates, Calculators, Parsers,
│                                 # Reconciliation, MPIN Security, Backup/Restore)
├── decisions.md                  # Architectural decision log (D001-D010)
└── build.gradle.kts / settings.gradle.kts
```

---

## Milestone Status

- [x] **M0 — Project Skeleton**: Gradle / Kotlin 2.0 / Compose / Material 3 / Hilt / Room setup, hardened manifest, CI workflow.
- [x] **M1 — Database & Financial Foundation**: Room entity schema, strict `Long` paise arithmetic, Asia/Kolkata month logic, seed data.
- [x] **M2 — Manual Transactions & Integrity**: Atomic repository boundaries, split sums, transfer legs, refund links, void/restore, Data Integrity Checker.
- [x] **M3 — Dashboard & Visuals**: Monthly income/spending overview, account balances, net worth, category progress bars, and custom Compose Canvas charts.
- [x] **M3.5 — Backup/Restore Gate**: `VACUUM INTO` database snapshots, `PRAGMA integrity_check`, pre-restore safety backups, and notification spooling.
- [x] **M4 — Pure Kotlin Parser Engine**: Anonymized regex engines for HDFC, SBI, ICICI, Axis, GPay, PhonePe; OTP & promo filters; Avl Bal extraction; UPI reversals.
- [x] **M5 — Listener & Liveness Heartbeat**: `NotificationListenerService`, active notification recovery, multi-level deduplication, heartbeat worker.
- [x] **M6 — Categorization & Pending Inbox**: Quick Needs/Wants/Savings bucket chips, category selection, split transactions, pending transaction inbox.
- [x] **M7 — Accounts & Net Worth**: Account listing (Bank, CC, Cash, Savings), asset/liability split, net worth history cards.
- [x] **M8 — Statement Import & Reconciliation**: PDF & CSV statement parsing (offline PDFBox), 1-to-1 reconciliation engine matching, missing-in-app detection.
- [x] **M9 — Month Close**: Period locking, `advance_ledger` carry-forward, negative advance overspend capture.
- [x] **M10 — App Security & Lock**: Argon2id password hashing via BouncyCastle, KeyStore salt management, MPIN setup, keypad lock screen, step-up auth.
- [x] **M11 — Category & Budget Management**: Bucket allocations, advance rules, intra-month budget transfers, envelope progress bars.
- [x] **M12 — Xiaomi 15 / HyperOS Reliability**: Setup checklist, autostart/battery optimization guidance, universal settings fallbacks.

---

## Testing & Developer Commands

### 1. Running Unit Tests

To execute all local unit tests (parsers, calculators, validators, MPIN security, and reconciliation engine):

```bash
./gradlew testDebugUnitTest
```

### 2. Enabling Notification Listener Permission

To enable notification capturing on a connected device or emulator:

- Navigate to **Settings** $\rightarrow$ **Apps** $\rightarrow$ **Special App Access** $\rightarrow$ **Notification Access** $\rightarrow$ Enable **TapLedger**.

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
