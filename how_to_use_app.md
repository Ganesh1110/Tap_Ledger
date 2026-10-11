# How to Use TapLedger — Complete User Guide & Real-Life Examples

Welcome to **TapLedger**! TapLedger is a personal, 100% offline-first Android budget tracker and financial ledger designed to give you complete clarity over your money without ever compromising your privacy.

This guide explains how TapLedger works using a relatable, real-life story so you can easily master the app and take full control of your personal finances.

---

## 📖 Real-Life Example: Meet Rahul

To understand TapLedger, let’s follow **Rahul**, a working professional living in Bengaluru. Rahul uses three financial accounts:
1. **HDFC Savings Account** (Primary bank account for salary & UPI payments).
2. **SBI Savings Account** (Emergency fund).
3. **HDFC Credit Card** (For online shopping & travel).

Let's see how Rahul uses TapLedger throughout a typical month!

---

## 1. Core Financial Concepts Explained Simply

Before jumping into the steps, here is how TapLedger thinks about money:

| Concept | What It Means | Real-Life Analogy |
| :--- | :--- | :--- |
| **Transaction** | A real movement of money in or out of an account (e.g. ₹850 paid via UPI). | A receipt printed by a card swipe machine. |
| **Budget Bucket** | Where the money is assigned (*Needs*, *Wants*, or *Savings*). | Envelopes where you allocate cash for food, rent, or entertainment. |
| **Account Balance** | How much real money is currently inside a bank or credit card. | Checking your bank ATM balance receipt. |
| **Net Worth** | Total Assets (Bank $+$ Cash) minus Total Liabilities (Credit Card Debt). | What you would actually own if you paid off all card bills today. |
| **Pending Inbox** | New bank SMS alerts captured in the background waiting for category assignment. | Unread letters sitting in your physical mailbox. |
| **Month Close** | Locking the previous month's books so past records stay accurate forever. | Filing your taxes and sealing past account ledgers. |

---

## 2. Day-to-Day Walkthrough with Rahul

### ☕ Example 1: Ordering Lunch on Swiggy via UPI (Automated Capture)

1. **What Happens in Real Life**: Rahul orders food on Swiggy for **₹850.00** using HDFC UPI.
2. **Bank Notification**: HDFC Bank sends an SMS:
   > *"Sent Rs. 850.00 from HDFC Bank A/C **1234 to SWIGGY via UPI Ref 998877665511. Avl Bal: Rs 49,150.00"*
3. **TapLedger Auto-Capture**: 
   - TapLedger's background listener automatically detects the bank SMS.
   - It records the expense (**₹850.00**) on **HDFC Savings** and immediately updates HDFC's available balance to **₹49,150.00**.
   - TapLedger posts a notification: `✨ New Transaction Captured: ₹850.00 at SWIGGY`.
4. **1-Tap Categorization**:
   - Rahul taps the notification (or opens **Pending Inbox**).
   - TapLedger remembers that Swiggy belongs to **Wants $\rightarrow$ Dining Out** and displays a **`✨ Suggested: Dining Out (1-Tap Apply)`** chip.
   - Rahul taps the chip once. The transaction is instantly categorized!

---

### 💰 Example 2: Monthly Salary Credit

1. **What Happens in Real Life**: On the 1st of the month, Rahul receives his salary of **₹75,000.00** credited to his HDFC Savings account.
2. **Recording in TapLedger**:
   - Rahul opens TapLedger $\rightarrow$ Taps **`+ Add`** (bottom FAB or Quick Action).
   - Selects **Type: Income**.
   - Enters **Amount: ₹75,000**.
   - Selects **Account: HDFC Savings** $\rightarrow$ Merchant: `"Company Salary"` $\rightarrow$ Taps **Save**.
3. **Visual Result**:
   - Total Net Worth and HDFC Available Balance increase instantly by **₹75,000.00**.
   - The Dashboard **Monthly Overview** donut chart updates your monthly income and unallocated budget pool.

---

### 💳 Example 3: Buying Headphones on Amazon using Credit Card

1. **What Happens in Real Life**: Rahul buys wireless headphones on Amazon for **₹2,000.00** using his HDFC Credit Card.
2. **Sign Convention in TapLedger**:
   - Credit cards represent **Liabilities** (money you owe).
   - Purchasing an item increases your credit card liability from **₹8,000.00** to **₹10,000.00**.
3. **How It Appears on Screen**:
   - On the **Accounts** screen, HDFC Credit Card clearly shows:
     > **Outstanding Liability: ₹10,000.00**
   - TapLedger automatically subtracts this **₹10,000.00** liability from Rahul's bank balances when computing his **Total Net Worth** (`Net Worth = Assets - Liabilities`).

---

### 📊 Example 4: Bank Statement Reconciliation (End of Month)

At the end of the month, Rahul wants to ensure he didn't miss any cash transactions or bank interest credits.

1. **Importing Statement**:
   - Rahul goes to **Statement Reconciliation** (via Quick Action chip or `⋮` menu).
   - Selects **HDFC Savings**, enters Opening Balance (`₹50,000.00`), Closing Balance (`₹1,24,150.00`), and pastes or uploads his bank CSV lines.
2. **Automated Matching**:
   - TapLedger's engine compares bank statement lines against recorded app transactions.
   - Matched lines display a **Green Check Mark (`MATCHED`)**.
3. **Handling Missing Items**:
   - An unrecorded bank charge of **₹118.00** shows an amber **`MISSING_IN_APP`** badge.
   - Rahul taps **`Add to App`**, selects Category **`Utilities & Fees`**, and TapLedger creates the transaction and reconciles the line in 1 tap!

---

### 🔒 Example 5: Closing the Month (Month-End Process)

When October ends and November begins, Rahul wants to lock his October accounts.

1. **Opening Month Close**:
   - Rahul taps **`🔒 Month Close`** on the Dashboard.
2. **Pre-Close Review Checklist**:
   - TapLedger verifies:
     - [x] All pending transactions categorized.
     - [x] Bank statement reconciled.
     - [x] Zero data integrity errors.
3. **Advance Carry-Forward Impact**:
   - Rahul spent **₹12,000** out of **₹16,000** allocated for Groceries.
   - Since Groceries has the rule **`Rollover Surplus`**, the remaining **₹4,000.00** surplus is automatically rolled over to next month's advance ledger!
4. **Locking the Books**:
   - Rahul taps **Lock Books & Close Month**. October becomes read-only, preventing accidental changes to historical data.

---

## 🛠️ App Navigation Cheat Sheet

| Destination | Icon | Primary Use Case |
| :--- | :--- | :--- |
| **Home** | `account_balance_wallet` | High-level Dashboard, Donut charts, Net Worth, Budget buckets. |
| **Transactions** | `receipt_long` | Full chronological ledger history grouped by date with timeline connectors (`● ────────`). |
| **`+` FAB** | `add` | Quick manual entry for Expense, Income, Transfer, or Refund. |
| **Budget** | `pie_chart` | Manage bucket allocations (*Needs*, *Wants*, *Savings*), advance rules, and intra-month budget transfers. |
| **Accounts** | `account_balance` | Real-time balances for Bank Accounts, Cash Wallets, and Credit Card Liabilities. |
| **Pending Inbox** | `schedule` | Review and classify newly captured bank notifications with 1-tap suggestions. |
| **Diagnostics** | `health_and_safety` | Run live 6-invariant database integrity scans and inspect system audit logs. |
| **Backup** | `backup` | Export offline database snapshots (`SQLite VACUUM INTO`) and verify database integrity. |

---

## 🔒 100% Offline & Private Promise

TapLedger **never** requests the `INTERNET` permission. Your financial data, bank account numbers, SMS alerts, and budget records **never leave your phone**. Backups are saved locally on your device and can be exported at any time.

*Enjoy full financial control and peace of mind with TapLedger!*
