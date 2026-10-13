# tapLedger — Full Implementation Plan

## 1. Project Overview

**tapLedger** is a personal, single-user, local-first Android expense tracker built with native Kotlin and Jetpack Compose.

The application captures bank/UPI payment notifications, converts them into pending transactions, and allows the user to categorize each transaction with minimal interaction.

### Primary goals

1. Categorize a payment in under 5 seconds.
2. Always show how much money remains in every budget category.
3. Track actual account balances separately from budget balances.
4. Reconcile the application against monthly bank statements.
5. Work completely offline.
6. Keep all financial data on the device.
7. Never silently lose or discard a payment.
8. Make financial data recoverable through backup/restore.
9. Work reliably on Xiaomi 15 / HyperOS.

### Non-goals

- Multi-user support
- Cloud synchronization
- iOS
- Automatic expense categorization without confirmation
- Online banking APIs
- Automatic bank transactions through internet APIs
- Play Store distribution for MVP

The application is intended to be sideloaded as an APK.

---

# 2. Technology Stack

## Android

- Kotlin
- Jetpack Compose
- Material 3
- Android minSdk 29
- targetSdk 35
- compileSdk matching target SDK
- Single-activity architecture
- One translucent activity for the category bottom-sheet flow

## Architecture

MVVM + Repository.

Recommended packages:

```text
data/
domain/
parser/
listener/
notify/
statement/
ui/
backup/
settings/
```

## Storage

- Room
- SQLite
- Kotlin Coroutines
- Kotlin Flow
- Hilt

## Important rules

### Money

All monetary values must be stored as:

```text
Long
```

in paise.

Example:

```text
₹1,500.00 = 150000
```

Never use:

```text
Float
Double
```

for financial calculations.

Formatting should happen only at the UI layer.

### Date/time

Store timestamps as epoch milliseconds.

Budget-month calculations must always use:

```text
Asia/Kolkata
```

The device's current timezone must not determine which budget month a transaction belongs to.

### Offline

The application must not require internet access.

Do not add:

```xml
android.permission.INTERNET
```

No analytics or telemetry.

---

# 3. Financial Model

tapLedger has two independent concepts:

## A. Budget position

Answers:

> "How much can I still spend in this category?"

Example:

```text
Groceries
Budget:    ₹5,000
Spent:     ₹3,200
Remaining: ₹1,800
```

## B. Account position

Answers:

> "How much money should actually be in my bank/cash account?"

Example:

```text
HDFC Savings

Opening:      ₹50,000
Income:       ₹52,000
Expenses:     ₹35,000
Transfers:     ₹5,000

Expected:     ₹62,000
Actual bank:  ₹61,750

Difference:     -₹250
```

These two calculations must remain separate.

---

# 4. Budget

Budget cycle starts on the first day of each month.

Initial take-home:

```text
₹52,000
```

Initial allocations:

| Category                     | Bucket  | Monthly |
| ---------------------------- | ------- | ------: |
| House Rent                   | NEEDS   | ₹15,000 |
| Groceries & Rice             | NEEDS   |  ₹5,000 |
| Vegetables & Fruits          | NEEDS   |  ₹2,000 |
| Milk                         | NEEDS   |  ₹1,000 |
| Electricity Bill             | NEEDS   |  ₹2,000 |
| Gas Cylinder                 | NEEDS   |  ₹1,000 |
| Mobile & Internet            | NEEDS   |  ₹1,500 |
| Transport / Fuel / Metro     | NEEDS   |  ₹5,000 |
| House Help / Laundry         | NEEDS   |  ₹2,000 |
| Medicines / Emergency        | NEEDS   |  ₹1,500 |
| Eating Out / Cafés           | WANTS   |  ₹4,000 |
| Shopping / Personal Care     | WANTS   |  ₹3,000 |
| Entertainment / OTT / Movies | WANTS   |  ₹1,000 |
| Savings & Investments        | SAVINGS |  ₹6,000 |

Totals:

```text
Needs:      ₹36,000
Wants:       ₹8,000
Savings:     ₹6,000
-------------------
Allocated:  ₹50,000

Take-home:  ₹52,000
Unallocated: ₹2,000
```

The user must be able to edit:

- Take-home
- Category names
- Category bucket
- Category allocation
- Category ordering
- Advance policy

Changes must use `effective_from`.

Past months must never change when the user changes a future budget.

---

# 5. Accounts

Initial accounts:

| Account          | Type        |
| ---------------- | ----------- |
| HDFC Savings     | BANK        |
| SBI Savings      | BANK        |
| HDFC Credit Card | CREDIT_CARD |
| Cash             | CASH        |

All accounts are editable.

Account balance must be derived from:

```text
opening balance
+ income
+ incoming transfers
- expenses
- outgoing transfers
+ refunds
```

Credit card balances must correctly represent outstanding dues.

---

# 6. Database Design

## accounts

```text
id
name
type
opening_balance_paise
active
created_at
updated_at
```

## categories

```text
id
bucket
name
sort_order
active
advance_policy
created_at
updated_at
```

`advance_policy`:

```text
CARRY_TO_ADVANCE
SWEEP_TO_SAVINGS
EXPIRE
```

## budget_periods

```text
id
effective_from
total_take_home_paise
created_at
```

## budget_allocations

```text
id
budget_period_id
category_id
amount_paise
```

The allocation for a month is the latest budget period whose `effective_from` is on or before that month.

---

# 7. Transaction Architecture

The actual transaction and its budget allocations must be separate.

## transactions

```text
id
amount_paise
kind
merchant_raw
merchant_key
upi_ref
account_id
status
source
note
occurred_at
created_at
statement_line_id
created_by
deleted_at
```

`kind`:

```text
EXPENSE
INCOME
TRANSFER
REFUND
```

`status`:

```text
PENDING
CATEGORIZED
IGNORED
```

`source`:

```text
SMS_HDFC
SMS_SBI
SMS_HDFC_CC
GPAY
MANUAL
STATEMENT
```

## transaction_allocations

Create a separate table for category allocation:

```text
id
transaction_id
category_id
amount_paise
created_at
```

### Example

A ₹1,500 supermarket payment:

```text
Transaction
-------------------------
Amazon / Supermarket
₹1,500
HDFC
        |
        +---- Groceries      ₹1,000
        |
        +---- Shopping         ₹500
```

The allocation amounts must always equal the transaction amount.

This architecture is preferred over storing split child transactions.

### Rules

```text
SUM(transaction_allocations.amount)
=
transactions.amount
```

for categorized expenses.

A transaction with no allocations remains pending.

---

# 8. Transfers

Transfers are never expenses.

Examples:

### ATM withdrawal

```text
HDFC Savings
      |
      | TRANSFER ₹5,000
      v
Cash
```

### Credit card bill payment

```text
HDFC Savings
      |
      | TRANSFER ₹10,000
      v
HDFC Credit Card
```

### Credit card purchase

```text
HDFC Credit Card
      |
      | EXPENSE ₹2,000
      v
Shopping
```

The later credit card bill payment must not create another expense.

Every transfer should have a linked counterpart.

Recommended transfer fields:

```text
transfer_group_id
```

or equivalent relationship between both transaction records.

Integrity validation must detect transfers with missing counterparts.

---

# 9. Transaction Rules

## Expense

Reduces a category allocation.

## Refund

Reduces spending in the original category when categorized.

## Income

Increases account balance but does not increase category budgets.

## Transfer

Changes account balances but does not affect category spending.

## Ignored

Does not affect balances.

## Pending

Does not affect category spending until categorized.

---

# 10. Deduplication

The same transaction may arrive through:

```text
Bank SMS
GPay notification
Statement
```

Deduplication priority:

### Level 1

Match by:

```text
upi_ref
```

### Level 2

If no reference:

```text
same amount
+
same account
+
within 3 minutes
```

### Level 3

Statement import:

Match using:

```text
reference
```

then:

```text
amount + date ±1 day
```

### Manual entry

Warn when:

```text
similar amount
+
same account
+
same/similar date
+
similar merchant
```

The warning must not prevent the user from intentionally creating the transaction.

---

# 11. Budget Calculations

## Category remaining

```text
allocation
- categorized expense allocations
+ refunds
+/- month-scoped money movements
```

## Bucket remaining

```text
SUM(category remaining)
```

## Unallocated

```text
take-home
- SUM(category allocations)
```

## Advance pool

```text
SUM(advance_ledger.amount_paise)
```

Remaining values must never be stored as database columns.

---

# 12. Overspending

Warnings:

```text
80% → Amber
100% → Red
```

Example:

```text
Groceries
₹4,100 / ₹5,000

82%
⚠ Near limit
```

When over budget:

```text
Groceries
Budget: ₹5,000
Spent:  ₹5,700
Over:   ₹700
```

Show categories that still have available money.

The user may optionally move money.

Important:

Moving money must create a month-scoped adjustment.

It must not modify the original budget allocation.

---

# 13. Merchant Memory

Table:

```text
merchant_map
```

Fields:

```text
merchant_key
category_id
use_count
last_used_at
```

Known merchant flow:

```text
₹750 paid to BESCOM

Confirm: Electricity Bill
```

The user must still explicitly confirm.

The app must never silently categorize a transaction solely because a merchant is known.

---

# 14. Small Payments

Default threshold:

```text
₹100
```

The threshold must be configurable.

Small payments:

- Still create Pending transactions.
- Must not be automatically categorized.
- Are grouped into a single notification.

Example:

```text
5 small payments pending
Total: ₹340

[Review]
```

---

# 15. Parser

The parser must remain a pure Kotlin module with no Android dependencies.

Interface:

```text
parse(source, rawText, timestamp): ParseResult
```

Possible results:

```text
Parsed
Ignored
Unknown
```

Parsers:

```text
HdfcBankParser
SbiParser
HdfcCreditCardParser
GPayParser
```

Support:

```text
Rs.1500.00
Rs 1,500
INR 1,500.00
₹1,500
```

Recognize:

- Debits
- Credits
- ATM withdrawals
- Credit card purchases
- Credit card bill payments
- Refunds
- Auto-debits
- Mandates

Ignore:

- OTP
- Promotions
- Failed payments
- Declined payments
- Balance-only notifications

Unknown transaction-looking messages must never be silently discarded.

They should create:

```text
PENDING transaction
+
unparsed_alert
```

when an amount can be detected.

Real anonymized samples must be added before finalizing parser patterns.

---

# 16. Notification Listener

Use:

```text
NotificationListenerService
```

Do not request `READ_SMS`.

Allowed sources:

```text
Google Messages
Xiaomi Messaging
GPay
HDFC
SBI
```

SMS packages must be configurable.

Ignore all other packages immediately.

---

# 17. Notification Flow

## Tier 1

Example:

```text
₹1,500 paid to BESCOM

Categorize this payment

[Needs] [Wants] [Savings]
```

Use:

- High-importance notification channel
- Stable notification ID
- Grouping per transaction

Notification actions must directly launch activities using:

```text
PendingIntent.getActivity(...)
```

Do not use a BroadcastReceiver/Service trampoline.

## Tier 2

Open:

```text
CategorySheetActivity
```

The activity should:

- Be translucent
- Look like a Compose bottom sheet
- Be excluded from recents
- Show merchant
- Show amount
- Show category remaining balance

Actions:

```text
Select category
Split
Skip
Not an expense
```

---

# 18. Xiaomi 15 / HyperOS Reliability

This is a first-class feature.

Setup checklist:

```text
[ ] Restricted settings allowed
[ ] Notification access
[ ] POST_NOTIFICATIONS
[ ] Battery → No restrictions
[ ] Autostart
[ ] Locked in recents
```

The setup screen must show live status.

Each item should provide a deep link where Android allows it.

Use:

```text
NotificationListenerService.requestRebind(...)
```

when appropriate.

Store:

```text
last_alert_received
```

If no alert has been received for 48 hours:

```text
⚠ Listener may be stopped
```

Settings must include:

```text
Send test alert
```

The test alert should exercise:

```text
notification
→ tap
→ category selection
→ database
→ dashboard
```

---

# 19. Pending Inbox

Every uncategorized transaction appears in Pending.

Display:

```text
Pending
₹2,450
3 transactions
```

Actions:

```text
Categorize
Ignore
Bulk categorize
```

Daily reminder:

```text
9:00 PM
```

must be configurable.

Use WorkManager.

---

# 20. Dashboard

Dashboard should display:

```text
Current month
Take-home
Unallocated

Needs
Wants
Savings

Category remaining

Pending transactions

Advance pool

Account balances

Listener health
Last alert received
```

Example:

```text
October 2026

Take-home                 ₹52,000
Unallocated                ₹2,000

NEEDS
₹28,500 remaining

WANTS
₹6,700 remaining

SAVINGS
₹6,000 remaining

⚠ 3 payments pending
₹1,250

Advance
₹4,300

HDFC Savings
₹42,350

Listener
✓ Active
Last alert: 4 min ago
```

---

# 21. Manual Transactions

Support:

```text
Expense
Income
Cash expense
Transfer
Refund
```

Manual expense fields:

```text
Amount
Category
Account
Merchant
Note
Date
```

Quick actions:

```text
Cash expense
ATM withdrawal
Credit card payment
```

Every save should provide an Undo snackbar.

---

# 22. Transaction History

Transaction screen must support:

```text
Search
Month filter
Account filter
Category filter
Source filter
Status filter
```

Actions:

```text
Edit
Undo
Void/delete
View details
```

For financial data, prefer a reversible delete/void model rather than permanently destroying historical records.

Important financial changes should remain traceable.

---

# 23. Data Integrity

Add:

```text
Settings
→ Data
→ Check Data Integrity
```

Checks should detect:

```text
Invalid transaction amounts
Invalid account references
Invalid category references
Split totals != transaction amount
Duplicate transaction references
Missing transfer counterpart
Invalid budget allocations
Unexpected closed-month modifications
Statement balance mismatch
```

The user should receive:

```text
✓ No problems found
```

or a list of issues requiring review.

---

# 24. Statement Import

Use:

```text
ACTION_OPEN_DOCUMENT
```

No storage permission required.

## Phase A

CSV / Excel.

Start with HDFC.

Then SBI.

## Phase B

PDF.

PDF parsing must use:

```text
StatementParser
```

interface so additional formats can be added later.

Password-protected statements:

- Ask for password every time.
- Never store password.

---

# 25. Statement Matching

Matching order:

```text
1. Reference number
2. Amount + date ±1 day
3. Merchant/narration similarity
```

Results:

```text
MATCHED
MISSING_IN_APP
AMOUNT_MISMATCH
RESOLVED
```

### Statement but not app

Create:

```text
PENDING
source = STATEMENT
```

### App but not statement

Flag for investigation.

### Amount mismatch

Let user choose the correct amount.

---

# 26. Statement Balance Proof

For every imported statement:

```text
Opening balance
+ credits
- debits
=
closing balance
```

If correct:

```text
✓ Statement verified
```

If incorrect:

```text
Balance mismatch
Expected: ₹50,000
Statement: ₹49,750
Difference: -₹250
```

Cash transactions are excluded.

---

# 27. Account Reconciliation

Add a dedicated account reconciliation feature.

Example:

```text
HDFC Savings

Expected balance
₹42,350

Actual bank balance
₹42,100

Difference
-₹250
```

The user can mark the difference for investigation.

This is separate from budget reconciliation.

---

# 28. Month Close

Month cannot be closed until statement reconciliation is completed.

When user selects:

```text
Close month
```

calculate:

```text
allocation - spending
```

for every category.

Apply:

```text
CARRY_TO_ADVANCE
SWEEP_TO_SAVINGS
EXPIRE
```

Overspent categories create negative advance entries.

The advance pool remains separate.

It must never automatically change next month's allocations.

The user explicitly chooses:

```text
Use advance
```

when required.

---

# 29. Backup and Restore

This is a required reliability feature.

## Backup

User can export complete database through SAF.

Example:

```text
tapLedger_backup_2026-10-31.db
```

## Restore

User can select a previous backup.

Before restore:

```text
1. Create automatic safety backup
2. Validate backup
3. Check schema version
4. Ask for confirmation
5. Restore
6. Verify database integrity
```

Never silently overwrite current financial data.

## CSV export

Provide transaction CSV export separately for portability.

---

# 30. Database Migration Strategy

Room database must use explicit schema versions.

Rules:

- Every schema change requires migration.
- No destructive migration for production data.
- Migration tests are required.
- Existing user data must survive application upgrades.

Example:

```text
DB v1
 ↓
DB v2
 ↓
DB v3
```

Each migration must be tested.

---

# 31. Privacy and Security

All financial data remains local.

Do not store raw SMS.

For unknown alerts:

```text
redacted_preview
```

Mask sensitive numbers.

Keep at most the last four digits of account numbers.

Disable cloud backup by default.

Optional:

```text
MPIN app lock
```

is Phase 2.

---

# 32. Expected / Recurring Payments — Phase 2

Do not automatically create expenses.

Instead provide expected-payment tracking.

Example:

```text
Expected this month

✓ Rent                 ₹15,000
✓ Mobile                ₹1,500
? Netflix                 ₹649
? Electricity            ₹1,850
```

This helps identify missing transactions.

---

# 33. Recommended Screen Structure

```text
Setup
│
├── Dashboard
│
├── Pending
│
├── Transactions
│
├── Add Transaction
│
├── Statement Import
│
├── Reconciliation
│
├── Month Close
│
├── Accounts
│
└── Settings
     ├── Budget
     ├── Notifications
     ├── SMS packages
     ├── Backup / Restore
     ├── Data Integrity
     ├── Test Alert
     └── Security
```

---

# 34. Implementation Milestones

## Milestone 0 — Project Skeleton

### Implement

- Gradle project
- Kotlin
- Compose
- Material 3
- Hilt
- Room
- Navigation
- Package structure
- Basic theme
- Empty dashboard

### Tests

Verify:

```text
./gradlew assembleDebug
./gradlew test
```

### Done when

Application launches successfully to Dashboard.

---

# Milestone 1 — Database Foundation

### Implement

- Room database
- Entities
- DAOs
- Repositories
- Database versioning
- Migration framework
- Seed data

Seed:

```text
14 categories
4 accounts
₹52,000 take-home
₹50,000 allocations
```

### Tests

- Insert transaction
- Category remaining
- Bucket remaining
- Unallocated amount
- Effective budget lookup
- Account balance

### Done when

Database calculations match manual calculations.

---

# Milestone 2 — Transaction Architecture

### Implement

- Transaction entity
- Transaction allocation entity
- Expense
- Income
- Refund
- Transfer
- Transfer relationships
- Split allocation

### Tests

Test:

```text
₹1,500
→ ₹1,000 groceries
→ ₹500 shopping
```

Ensure:

```text
1000 + 500 = 1500
```

Also test:

- ATM transfer
- Credit card purchase
- Credit card bill payment
- Refund
- Income

### Done when

Financial calculations remain correct for all transaction types.

---

# Milestone 3 — Manual Entry

### Implement

- Add transaction UI
- Edit
- Void/delete
- Undo
- Cash expense
- Transfer
- Refund
- Transaction details

### Done when

A complete month can be entered manually.

---

# Milestone 4 — Dashboard

### Implement

- Take-home
- Unallocated
- Needs
- Wants
- Savings
- Category progress
- 80% warning
- 100% warning
- Overspending
- Move-money
- Pending count
- Account balances
- Advance pool

### Done when

Dashboard matches hand calculations.

---

# Milestone 5 — Parser Engine

### Implement

Pure Kotlin parser module.

Implement:

```text
HDFC
SBI
HDFC Credit Card
GPay
```

### Tests

Create:

```text
src/test/resources/sms_samples/
```

Test:

- Normal payment
- Refund
- ATM
- Credit card
- Bill payment
- Failed payment
- OTP
- Promotional message
- Unknown payment

### Required before completion

Collect 20–30 anonymized real examples from:

```text
HDFC
SBI
HDFC Credit Card
GPay
```

### Done when

All known samples parse correctly and unknown transaction-looking messages become Unknown/Pending.

---

# Milestone 6 — Notification Listener

### Implement

- NotificationListenerService
- Package filtering
- Notification extraction
- Parser integration
- Deduplication
- Pending transaction creation
- Unknown alert storage

### Done when

Real bank notification creates a Pending transaction.

GPay + bank notification must create only one transaction.

---

# Milestone 7 — Xiaomi Reliability

### Implement

- Setup checklist
- Notification access status
- Notification permission status
- Battery restriction guidance
- Autostart guidance
- Recents lock guidance
- Rebind logic
- Last-alert timestamp
- 48-hour warning
- Test alert

### Done when

The listener survives normal Xiaomi 15 usage and the complete notification pipeline can be tested.

---

# Milestone 8 — Notification Tap Flow

### Implement

Tier 1:

```text
Needs
Wants
Savings
```

Tier 2:

```text
CategorySheetActivity
```

Implement:

- Category selection
- Remaining balance
- Split
- Skip
- Ignore
- Known merchant confirmation

### Performance target

Payment should be categorizable in:

```text
< 5 seconds
```

---

# Milestone 9 — Pending Inbox

### Implement

- Pending list
- Swipe actions
- Bulk categorization
- Ignore
- Small-payment batching
- Daily reminder
- WorkManager

### Done when

Pending transactions can be cleared efficiently.

---

# Milestone 10 — Statement Import

### Implement

First:

```text
HDFC CSV/Excel
```

Then:

```text
SBI CSV/Excel
```

Implement:

- SAF picker
- Import parser
- Duplicate detection
- Matching
- Review screen
- Balance proof

### Done when

A complete bank statement can verify the app ledger.

---

# Milestone 11 — Account Reconciliation

### Implement

- Expected balance
- Actual balance entry
- Difference calculation
- Investigation status
- Reconciliation history

### Done when

The user can confirm:

```text
App balance == actual bank balance
```

or identify the difference.

---

# Milestone 12 — Month Close / Advance

### Implement

- Reconciliation requirement
- Close month
- Category leftovers
- Advance ledger
- Negative advance
- Sweep to savings
- Expire
- Use advance

### Done when

A complete month can be closed without changing historical data.

---

# Milestone 13 — Backup and Restore

### Implement

- Full database backup
- SAF destination
- Restore
- Automatic pre-restore backup
- Schema validation
- Integrity validation
- CSV export

### Done when

The application can be deleted/reinstalled and restored from a valid backup.

---

# Milestone 14 — Data Integrity & Recovery

### Implement

Integrity checker.

Test:

- Invalid references
- Split mismatch
- Duplicate references
- Missing transfer counterpart
- Invalid budget data
- Corrupted/inconsistent state

### Done when

The app can detect and report inconsistent financial data.

---

# Milestone 15 — Merchant Memory

### Implement

- Merchant normalization
- Merchant history
- One-tap confirmation
- Usage count
- Last-used date

### Done when

Repeated merchants can be categorized in one tap.

---

# Milestone 16 — Charts / Net Worth

### Implement

- Monthly spending chart
- Category spending
- Account balances
- Net worth trend
- Month-over-month comparison

This is not required for the core MVP.

---

# 35. MVP Definition

The recommended MVP is:

```text
Milestone 0
Milestone 1
Milestone 2
Milestone 3
Milestone 4
Milestone 5
Milestone 6
Milestone 7
Milestone 8
Milestone 9
```

However, before calling the app financially trustworthy, complete:

```text
Milestone 10
Milestone 11
Milestone 13
Milestone 14
```

The statement/reconciliation layer is what turns the application from a simple expense tracker into a reliable ledger.

---

# 36. Testing Strategy

## Unit tests

Required for:

```text
Money calculations
Budget calculations
Effective-from budget lookup
Account balances
Transaction allocations
Split validation
Deduplication
Parser patterns
Refunds
Transfers
Month close
Advance calculations
Statement matching
```

## DAO tests

Use:

```text
In-memory Room
```

## Migration tests

Every database version must have migration tests.

## Parser tests

Use anonymized real samples.

## Integration tests

Test:

```text
Notification
→ Parser
→ Deduplication
→ Transaction
→ Pending
→ Category
→ Allocation
→ Dashboard
```

## Manual testing

Maintain:

```text
docs/manual-tests.md
```

Each milestone must have a repeatable test procedure.

---

# 37. Decision Log

Maintain:

```text
docs/decisions.md
```

Record important decisions such as:

```text
Why transfers are separate
Why allocations use a separate table
How refunds work
How duplicate transactions are detected
How month boundaries work
How advance is calculated
How statement matching works
```

Never silently change financial logic.

---

# 38. Development Rules

1. Never guess financial logic.
2. Keep financial calculations deterministic.
3. Do not use Float/Double for money.
4. Do not add internet connectivity.
5. Do not add dependencies without justification.
6. Prefer readable Kotlin over excessive abstraction.
7. Keep database migrations safe.
8. Never silently delete financial history.
9. Never silently categorize an expense.
10. Every milestone must build and test successfully.
11. Test money calculations before UI polish.
12. Test real Xiaomi notification behavior before declaring listener work complete.
13. Do not move to the next milestone until the current milestone is verified.

---

# 39. Phase 2 Features

After the core system is stable:

```text
MPIN app lock
Expected/recurring payments
Advanced charts
More bank parsers
More statement formats
Automatic merchant suggestions
Enhanced reconciliation
Custom reports
```

These should not delay the core MVP.

---

# 40. Final Implementation Order

The complete implementation order is:

```text
1.  Project skeleton
2.  Database
3.  Transaction + allocation architecture
4.  Manual transactions
5.  Dashboard
6.  Parser
7.  Notification listener
8.  Xiaomi reliability
9.  Notification tap flow
10. Pending inbox
11. HDFC statement import
12. SBI statement import
13. Account reconciliation
14. Month close
15. Backup / restore
16. Data integrity checker
17. Merchant memory
18. Charts / net worth
19. Phase 2 features
```

The most important architectural principle is:

```text
                         ┌── Budget
                         │
Transaction ─────────────┼── Account balance
                         │
                         ├── Statement reconciliation
                         │
                         └── Category allocations
```

The **transaction is the financial fact**.

Budget allocation determines **where the spending belongs**.

Account balance determines **where the money moved**.

Statement reconciliation determines **whether the ledger matches reality**.

Keeping those three concepts separate will make tapLedger much easier to maintain and much safer as the application grows.
