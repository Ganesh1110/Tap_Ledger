package com.tapledger.qa

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import com.tapledger.domain.calculator.SafeDailySpendCalculator
import org.junit.Test

/**
 * QA User Persona Test Suite:
 * Simulates a realistic Indian professional user portfolio:
 * - 2 Bank accounts: HDFC (Salary/Primary) + SBI (Secondary/Savings)
 * - 2 Credit cards: HDFC Millennia + SBI SimplyCLICK
 * - Cash in hand: ₹1,000
 * - Personal Loan: ₹5,00,000
 *
 * Verifies end-to-end financial workflows:
 * 1. Income receipt & envelope budgeting (50-30-20 rule)
 * 2. Multi-card spending (Credit Card 1 and Credit Card 2)
 * 3. Credit Card bill settlement via bank transfer
 * 4. Loan EMI payment (Principal repayment transfer + Interest expense)
 * 5. In-hand cash transactions
 * 6. Net worth equation: Assets - Liabilities
 * 7. Safe Daily Spend calculation & envelope integrity
 */
class RealUserPortfolioQaTest {

    private val nowEpochMs = DateTimeUtils.nowEpochMillis()

    @Test
    fun testRealUserFullLifecycle_With2Banks_2CreditCards_Cash_AndLoan() {
        // ==========================================
        // 1. SETUP USER PORTFOLIO ACCOUNTS
        // ==========================================
        val hdfcBank = AccountEntity(
            id = 1L,
            name = "HDFC Bank",
            type = AccountType.BANK,
            identifierLast4 = "6362",
            openingBalancePaise = 4442867L, // ₹44,428.67
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            isSavings = false,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val sbiBank = AccountEntity(
            id = 2L,
            name = "SBI Bank",
            type = AccountType.BANK,
            identifierLast4 = "3486",
            openingBalancePaise = 1498695L, // ₹14,986.95
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            isSavings = true,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val cashAccount = AccountEntity(
            id = 3L,
            name = "Cash in Hand",
            type = AccountType.CASH,
            identifierLast4 = null,
            openingBalancePaise = 100000L, // ₹1,000.00
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            isSavings = false,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val hdfcCreditCard = AccountEntity(
            id = 4L,
            name = "HDFC Millennia Credit Card",
            type = AccountType.CREDIT_CARD,
            identifierLast4 = "8812",
            openingBalancePaise = 0L,
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            creditLimitPaise = 15000000L, // ₹1,50,000.00 limit
            statementDay = 15,
            dueDay = 5,
            isSavings = false,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val sbiCreditCard = AccountEntity(
            id = 5L,
            name = "SBI SimplyCLICK Credit Card",
            type = AccountType.CREDIT_CARD,
            identifierLast4 = "4120",
            openingBalancePaise = 0L,
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            creditLimitPaise = 10000000L, // ₹1,00,000.00 limit
            statementDay = 20,
            dueDay = 10,
            isSavings = false,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val loanAccount = AccountEntity(
            id = 6L,
            name = "Personal Loan Account",
            type = AccountType.LOAN,
            identifierLast4 = null,
            openingBalancePaise = -50000000L, // -₹5,00,000.00 liability
            openingBalanceDate = nowEpochMs - 86400000L * 30,
            isSavings = false,
            isActive = true,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        val allAccounts = listOf(hdfcBank, sbiBank, cashAccount, hdfcCreditCard, sbiCreditCard, loanAccount)

        // ==========================================
        // 2. SETUP BUDGET PERIOD & CATEGORIES
        // ==========================================
        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 11,
            startAt = DateTimeUtils.getStartOfDayEpochMillis(nowEpochMs),
            endAt = DateTimeUtils.getStartOfDayEpochMillis(nowEpochMs) + 30L * 86400000L,
            status = BudgetPeriodStatus.OPEN,
            incomePaise = 5192100L, // ₹51,921.00
            isReviewed = false,
            createdAt = nowEpochMs,
            updatedAt = nowEpochMs
        )

        fun createCat(id: Long, name: String, bucket: BudgetBucket, order: Int): CategoryEntity {
            return CategoryEntity(
                id = id,
                name = name,
                systemKey = null,
                defaultBucket = bucket,
                defaultSortOrder = order,
                defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
                sweepTargetCategoryId = null,
                isActive = true,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
        }

        val categories = listOf(
            createCat(1L, "Groceries & Food", BudgetBucket.NEEDS, 1),
            createCat(2L, "Rent & Housing", BudgetBucket.NEEDS, 2),
            createCat(3L, "Utilities & Bills", BudgetBucket.NEEDS, 3),
            createCat(4L, "Transport & Fuel", BudgetBucket.NEEDS, 4),
            createCat(5L, "Dining Out", BudgetBucket.WANTS, 5),
            createCat(6L, "Entertainment & OTT", BudgetBucket.WANTS, 6),
            createCat(7L, "Personal Shopping", BudgetBucket.WANTS, 7),
            createCat(8L, "Emergency Fund", BudgetBucket.SAVINGS, 8),
            createCat(9L, "Investments", BudgetBucket.SAVINGS, 9),
            createCat(10L, "Loan Interest", BudgetBucket.NEEDS, 10)
        )

        val allocations = listOf(
            BudgetAllocationEntity(1L, period.id, 1L, "Groceries & Food", BudgetBucket.NEEDS, 1, AdvancePolicy.CARRY_TO_ADVANCE, 500000L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(2L, period.id, 2L, "Rent & Housing", BudgetBucket.NEEDS, 2, AdvancePolicy.CARRY_TO_ADVANCE, 1100000L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(3L, period.id, 3L, "Utilities & Bills", BudgetBucket.NEEDS, 3, AdvancePolicy.CARRY_TO_ADVANCE, 400000L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(4L, period.id, 4L, "Transport & Fuel", BudgetBucket.NEEDS, 4, AdvancePolicy.CARRY_TO_ADVANCE, 596050L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(5L, period.id, 5L, "Dining Out", BudgetBucket.WANTS, 5, AdvancePolicy.CARRY_TO_ADVANCE, 519210L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(6L, period.id, 6L, "Entertainment & OTT", BudgetBucket.WANTS, 6, AdvancePolicy.CARRY_TO_ADVANCE, 519210L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(7L, period.id, 7L, "Personal Shopping", BudgetBucket.WANTS, 7, AdvancePolicy.CARRY_TO_ADVANCE, 519210L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(8L, period.id, 8L, "Emergency Fund", BudgetBucket.SAVINGS, 8, AdvancePolicy.CARRY_TO_ADVANCE, 519210L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(9L, period.id, 9L, "Investments", BudgetBucket.SAVINGS, 9, AdvancePolicy.CARRY_TO_ADVANCE, 519210L, nowEpochMs, nowEpochMs),
            BudgetAllocationEntity(10L, period.id, 10L, "Loan Interest", BudgetBucket.NEEDS, 10, AdvancePolicy.CARRY_TO_ADVANCE, 0L, nowEpochMs, nowEpochMs)
        )

        // ==========================================
        // 3. EXECUTE REALISTIC USER TRANSACTIONS
        // ==========================================
        val transactions = mutableListOf<TransactionEntity>()
        val txAllocations = mutableListOf<TransactionAllocationEntity>()
        var txIdCounter = 1L
        var allocIdCounter = 1L

        fun recordExpense(accountId: Long, amountPaise: Long, categoryId: Long, merchant: String, note: String) {
            val tx = TransactionEntity(
                id = txIdCounter++,
                accountId = accountId,
                kind = TransactionKind.EXPENSE,
                direction = TransactionDirection.OUT,
                amountPaise = amountPaise,
                merchantRaw = merchant,
                merchantNormalized = merchant,
                source = TransactionSource.MANUAL,
                occurredAt = nowEpochMs,
                status = TransactionStatus.CATEGORIZED,
                notes = note,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
            transactions.add(tx)
            txAllocations.add(
                TransactionAllocationEntity(
                    id = allocIdCounter++,
                    transactionId = tx.id,
                    categoryId = categoryId,
                    amountPaise = amountPaise
                )
            )
        }

        fun recordTransfer(fromAccountId: Long, toAccountId: Long, amountPaise: Long, note: String) {
            val outTx = TransactionEntity(
                id = txIdCounter++,
                accountId = fromAccountId,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.OUT,
                amountPaise = amountPaise,
                source = TransactionSource.MANUAL,
                occurredAt = nowEpochMs,
                status = TransactionStatus.CONFIRMED,
                notes = note,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
            val inTx = TransactionEntity(
                id = txIdCounter++,
                accountId = toAccountId,
                kind = TransactionKind.TRANSFER,
                direction = TransactionDirection.IN,
                amountPaise = amountPaise,
                source = TransactionSource.MANUAL,
                occurredAt = nowEpochMs,
                status = TransactionStatus.CONFIRMED,
                notes = note,
                createdAt = nowEpochMs,
                updatedAt = nowEpochMs
            )
            transactions.add(outTx)
            transactions.add(inTx)
        }

        // Action A: Swipe HDFC Millennia for Groceries & Dining
        recordExpense(hdfcCreditCard.id, 245000L, 1L, "BigBasket", "Monthly groceries on HDFC CC") // ₹2,450.00
        recordExpense(hdfcCreditCard.id, 119900L, 5L, "Zomato", "Family dinner on HDFC CC")        // ₹1,199.00

        // Action B: Swipe SBI SimplyCLICK for Shopping & OTT Subscription
        recordExpense(sbiCreditCard.id, 149900L, 7L, "Amazon", "Clothing on SBI CC")               // ₹1,499.00
        recordExpense(sbiCreditCard.id, 89900L, 6L, "Netflix", "Subscription renewal on SBI CC")   // ₹899.00

        // Action C: Spend Cash on auto transport
        recordExpense(cashAccount.id, 12000L, 4L, "Auto Rickshaw", "Daily commute in cash")        // ₹120.00

        // Action D: Settle part of HDFC Millennia Credit Card bill from HDFC Bank
        recordTransfer(hdfcBank.id, hdfcCreditCard.id, 200000L, "Paid ₹2,000 to HDFC Millennia CC")

        // Action E: Pay Loan EMI from HDFC Bank
        // EMI Total = ₹10,000.00. Principal = ₹5,416.67, Interest = ₹4,583.33
        val principalPaise = 541667L
        val interestPaise = 458333L
        recordTransfer(hdfcBank.id, loanAccount.id, principalPaise, "EMI Principal reduction")
        recordExpense(hdfcBank.id, interestPaise, 10L, "Personal Loan Bank", "EMI Interest component")

        // ==========================================
        // 4. VERIFY BALANCE ACCURACY FOR ALL ACCOUNTS
        // ==========================================
        val summaries = allAccounts.map { acc ->
            AccountBalanceCalculator.calculateAccountBalance(acc, transactions)
        }
        val summaryMap = summaries.associateBy { it.account.id }

        // HDFC Bank: 44,428.67 - 2,000.00 (CC bill) - 5,416.67 (Loan Principal) - 4,583.33 (Loan Interest) = ₹32,428.67
        val hdfcSummary = summaryMap.getValue(hdfcBank.id)
        assertThat(hdfcSummary.currentBalancePaise).isEqualTo(3242867L)

        // SBI Bank: Untouched = ₹14,986.95
        val sbiSummary = summaryMap.getValue(sbiBank.id)
        assertThat(sbiSummary.currentBalancePaise).isEqualTo(1498695L)

        // Cash: 1,000.00 - 120.00 = ₹880.00
        val cashSummary = summaryMap.getValue(cashAccount.id)
        assertThat(cashSummary.currentBalancePaise).isEqualTo(88000L)

        // HDFC Millennia CC: 0 - 2,450.00 - 1,199.00 + 2,000.00 (repayment) = -₹1,649.00
        val hdfcCcSummary = summaryMap.getValue(hdfcCreditCard.id)
        assertThat(hdfcCcSummary.currentBalancePaise).isEqualTo(-164900L)
        // Utilization: 1,649 / 150,000 = 1%
        assertThat(hdfcCcSummary.creditCardUtilizationPercent).isEqualTo(1)

        // SBI SimplyCLICK CC: 0 - 1,499.00 - 899.00 = -₹2,398.00
        val sbiCcSummary = summaryMap.getValue(sbiCreditCard.id)
        assertThat(sbiCcSummary.currentBalancePaise).isEqualTo(-239800L)
        // Utilization: 2,398 / 100,000 = 2%
        assertThat(sbiCcSummary.creditCardUtilizationPercent).isEqualTo(2)

        // Loan Account: -5,00,000.00 + 5,416.67 (repayment) = -₹4,94,583.33
        val loanSummary = summaryMap.getValue(loanAccount.id)
        assertThat(loanSummary.currentBalancePaise).isEqualTo(-49458333L)

        // ==========================================
        // 5. VERIFY NET WORTH EQUATION
        // Assets = Bank1 + Bank2 + Cash = 32,428.67 + 14,986.95 + 880.00 = ₹48,295.62 (4829562 paise)
        // Liabilities = CC1 + CC2 + Loan = 1,649.00 + 2,398.00 + 4,94,583.33 = ₹4,98,630.33 (49863033 paise)
        // Net Worth = Assets - Liabilities = -₹4,50,334.71 (-45033471 paise)
        // ==========================================
        val netWorth = AccountBalanceCalculator.calculateNetWorth(summaries)
        assertThat(netWorth.totalAssetsPaise).isEqualTo(4829562L)
        assertThat(netWorth.totalLiabilitiesPaise).isEqualTo(49863033L)
        assertThat(netWorth.netWorthPaise).isEqualTo(-45033471L)

        // ==========================================
        // 6. VERIFY BUDGET PERIOD SUMMARY & ENVELOPE DEDUCTIONS
        // ==========================================
        val budgetSummary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = allocations,
            adjustments = emptyList(),
            transactions = transactions,
            allocationsForTransactions = txAllocations,
            categoryNames = categories.associate { it.id to it.name }
        )

        // Total Spent = 2,450 (Groceries) + 1,199 (Dining) + 1,499 (Shopping) + 899 (OTT) + 120 (Transport) + 4,583.33 (Interest)
        // = ₹10,750.33 (1075033 paise)
        // Notice: Credit card bill repayment (₹2,000 transfer) and Loan principal transfer (₹5,416.67) are Transfers,
        // so they NEVER leak into envelope spending!
        val catSpendMap = budgetSummary.categorySummaries.associate { it.allocation.categoryId to it.netSpentPaise }
        assertThat(catSpendMap[1L]).isEqualTo(245000L) // Groceries: ₹2,450.00
        assertThat(catSpendMap[5L]).isEqualTo(119900L) // Dining: ₹1,199.00
        assertThat(catSpendMap[7L]).isEqualTo(149900L) // Shopping: ₹1,499.00
        assertThat(catSpendMap[6L]).isEqualTo(89900L)  // OTT: ₹899.00
        assertThat(catSpendMap[4L]).isEqualTo(12000L)  // Transport: ₹120.00
        assertThat(catSpendMap[10L]).isEqualTo(458333L) // Loan Interest: ₹4,583.33

        val totalExpensesPaise = 245000L + 119900L + 149900L + 89900L + 12000L + 458333L
        assertThat(budgetSummary.totalSpentPaise).isEqualTo(totalExpensesPaise)

        // ==========================================
        // 7. SAFE DAILY SPEND CALCULATOR VERIFICATION
        // ==========================================
        val safeSpend = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = budgetSummary.dashboardHeroBudgetPaise,
            heroSpentPaise = budgetSummary.dashboardHeroSpendPaise,
            periodStartAt = period.startAt,
            periodEndAt = period.endAt,
            nowIST = nowEpochMs,
            isClosed = false,
            todaySpentPaise = totalExpensesPaise
        )
        assertThat(safeSpend.daysRemaining).isAtLeast(1)
        assertThat(safeSpend.safeDailyPaise).isAtLeast(0L)
    }
}
