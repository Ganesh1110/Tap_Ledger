package com.tapledger.domain

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.calculator.BudgetCalculator
import org.junit.Test

/**
 * Validates Flow.md §4.2: Comprehensive Status Impact Matrix
 *
 * Status        | Account Balance | Budget Spending | Category Envelopes
 * PENDING       | YES (acct!=null)| NO              | NO
 * CONFIRMED     | YES             | NO              | NO
 * CATEGORIZED   | YES             | YES (N & W)     | YES
 * IGNORED       | NO              | NO              | NO
 * VOIDED        | NO              | NO              | NO
 */
class StatusImpactMatrixTest {

    private val testAccount = AccountEntity(
        id = 1L,
        name = "Checking Account",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 100000L, // ₹1,000.00
        openingBalanceDate = 1700000000000L, // Nov 14, 2023
        isActive = true,
        createdAt = 1700000000000L,
        updatedAt = 1700000000000L
    )

    @Test
    fun testImpactMatrix_accountBalance() {
        val baseTime = 1700050000000L // after opening balance date

        // 1. PENDING expense (account != null) -> Deducts balance immediately
        val pendingExpense = TransactionEntity(
            id = 101L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 15000L, // ₹150.00
            occurredAt = baseTime + 1000L,
            createdAt = baseTime + 1000L,
            updatedAt = baseTime + 1000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        val bal1 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense))
        // 100000 - 15000 = 85000
        assertThat(bal1.currentBalancePaise).isEqualTo(85000L)
        assertThat(bal1.totalOutgoingPaise).isEqualTo(15000L)

        // 2. CONFIRMED income -> Adds to balance
        val confirmedIncome = TransactionEntity(
            id = 102L,
            accountId = 1L,
            kind = TransactionKind.INCOME,
            direction = TransactionDirection.IN,
            amountPaise = 50000L, // ₹500.00
            occurredAt = baseTime + 2000L,
            createdAt = baseTime + 2000L,
            updatedAt = baseTime + 2000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL
        )
        val bal2 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense, confirmedIncome))
        // 100000 - 15000 + 50000 = 135000
        assertThat(bal2.currentBalancePaise).isEqualTo(135000L)
        assertThat(bal2.totalIncomingPaise).isEqualTo(50000L)

        // 3. CATEGORIZED expense -> Deducts balance
        val categorizedExpense = TransactionEntity(
            id = 103L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 20000L, // ₹200.00
            occurredAt = baseTime + 3000L,
            createdAt = baseTime + 3000L,
            updatedAt = baseTime + 3000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val bal3 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense, confirmedIncome, categorizedExpense))
        // 135000 - 20000 = 115000
        assertThat(bal3.currentBalancePaise).isEqualTo(115000L)

        // 4. IGNORED expense -> Strictly EXCLUDED from balance
        val ignoredExpense = TransactionEntity(
            id = 104L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 30000L,
            occurredAt = baseTime + 4000L,
            createdAt = baseTime + 4000L,
            updatedAt = baseTime + 4000L,
            status = TransactionStatus.IGNORED,
            source = TransactionSource.NOTIFICATION
        )
        val bal4 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense, confirmedIncome, categorizedExpense, ignoredExpense))
        assertThat(bal4.currentBalancePaise).isEqualTo(115000L)

        // 5. VOIDED expense -> Strictly EXCLUDED from balance
        val voidedExpense = TransactionEntity(
            id = 105L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 40000L,
            occurredAt = baseTime + 5000L,
            createdAt = baseTime + 5000L,
            updatedAt = baseTime + 5000L,
            status = TransactionStatus.VOIDED,
            source = TransactionSource.MANUAL
        )
        val bal5 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense, confirmedIncome, categorizedExpense, ignoredExpense, voidedExpense))
        assertThat(bal5.currentBalancePaise).isEqualTo(115000L)

        // 6. Unassigned transaction (account_id = null) -> Does NOT hit this account balance
        val unassignedTx = TransactionEntity(
            id = 106L,
            accountId = null,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50000L,
            occurredAt = baseTime + 6000L,
            createdAt = baseTime + 6000L,
            updatedAt = baseTime + 6000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )
        val bal6 = AccountBalanceCalculator.calculateAccountBalance(testAccount, listOf(pendingExpense, confirmedIncome, categorizedExpense, unassignedTx))
        assertThat(bal6.currentBalancePaise).isEqualTo(115000L)
    }

    @Test
    fun testImpactMatrix_budgetSpendingAndEnvelopes() {
        val period = BudgetPeriodEntity(
            id = 1L,
            year = 2026,
            month = 10,
            startAt = 1000000L,
            endAt = 2000000L,
            incomePaise = 5000000L,
            status = BudgetPeriodStatus.OPEN,
            createdAt = 1000000L,
            updatedAt = 1000000L
        )

        val allocations = listOf(
            BudgetAllocationEntity(
                id = 1L,
                budgetPeriodId = 1L,
                categoryId = 10L,
                categoryNameSnapshot = "Dining",
                bucketSnapshot = BudgetBucket.WANTS,
                sortOrderSnapshot = 1,
                advancePolicySnapshot = com.tapledger.core.model.AdvancePolicy.CARRY_TO_ADVANCE,
                amountPaise = 100000L, // ₹1,000.00
                createdAt = 1000000L,
                updatedAt = 1000000L
            )
        )

        // Create transactions in various states
        val txPending = TransactionEntity(
            id = 1L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 15000L,
            occurredAt = 1500000L,
            createdAt = 1500000L,
            updatedAt = 1500000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )

        val txConfirmed = TransactionEntity(
            id = 2L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 25000L,
            occurredAt = 1500000L,
            createdAt = 1500000L,
            updatedAt = 1500000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL
        )

        val txCategorized = TransactionEntity(
            id = 3L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 30000L,
            occurredAt = 1500000L,
            createdAt = 1500000L,
            updatedAt = 1500000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val txIgnored = TransactionEntity(
            id = 4L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 40000L,
            occurredAt = 1500000L,
            createdAt = 1500000L,
            updatedAt = 1500000L,
            status = TransactionStatus.IGNORED,
            source = TransactionSource.NOTIFICATION
        )

        val txVoided = TransactionEntity(
            id = 5L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50000L,
            occurredAt = 1500000L,
            createdAt = 1500000L,
            updatedAt = 1500000L,
            status = TransactionStatus.VOIDED,
            source = TransactionSource.MANUAL
        )

        // Split allocations for each of the above transactions targeting category 10
        val txAllocations = listOf(
            TransactionAllocationEntity(id = 1L, transactionId = 1L, categoryId = 10L, amountPaise = 15000L),
            TransactionAllocationEntity(id = 2L, transactionId = 2L, categoryId = 10L, amountPaise = 25000L),
            TransactionAllocationEntity(id = 3L, transactionId = 3L, categoryId = 10L, amountPaise = 30000L),
            TransactionAllocationEntity(id = 4L, transactionId = 4L, categoryId = 10L, amountPaise = 40000L),
            TransactionAllocationEntity(id = 5L, transactionId = 5L, categoryId = 10L, amountPaise = 50000L)
        )

        val summary = BudgetCalculator.calculateBudgetPeriodSummary(
            period = period,
            allocations = allocations,
            adjustments = emptyList(),
            transactions = listOf(txPending, txConfirmed, txCategorized, txIgnored, txVoided),
            allocationsForTransactions = txAllocations
        )

        // Flow.md §4.2: ONLY txCategorized (30000L) hits Budget Spending & Category Envelope!
        // PENDING, CONFIRMED, IGNORED, VOIDED must NOT affect category spending!
        assertThat(summary.totalSpentPaise).isEqualTo(30000L)
        assertThat(summary.categorySummaries).hasSize(1)
        val catSummary = summary.categorySummaries[0]
        assertThat(catSummary.netSpentPaise).isEqualTo(30000L)
        assertThat(catSummary.remainingPaise).isEqualTo(70000L) // 100000 - 30000
    }

    @Test
    fun testAsOfBalanceBoundary() {
        val baseDate = testAccount.openingBalanceDate // Nov 14, 2023
        val startOfDay = DateTimeUtils.getStartOfDayEpochMillis(baseDate)

        // Transaction before start of opening balance date -> excluded
        val beforeOpening = TransactionEntity(
            id = 201L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 10000L,
            occurredAt = startOfDay - 1000L,
            createdAt = startOfDay,
            updatedAt = startOfDay,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        // Transaction 1: day 1
        val t1 = TransactionEntity(
            id = 202L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 20000L,
            occurredAt = startOfDay + 1000L,
            createdAt = startOfDay + 1000L,
            updatedAt = startOfDay + 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        // Transaction 2: day 2
        val t2 = TransactionEntity(
            id = 203L,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 30000L,
            occurredAt = startOfDay + 86400000L + 1000L,
            createdAt = startOfDay + 86400000L + 1000L,
            updatedAt = startOfDay + 86400000L + 1000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val txList = listOf(beforeOpening, t1, t2)

        // As of day 1: only t1 included (beforeOpening excluded by opening date, t2 excluded by as-of bound)
        val asOfDay1 = AccountBalanceCalculator.calculateAsOfBalance(
            account = testAccount,
            transactions = txList,
            asOfEpochMillis = startOfDay + 5000L
        )
        // 100000 - 20000 = 80000
        assertThat(asOfDay1).isEqualTo(80000L)

        // As of day 2: t1 and t2 included
        val asOfDay2 = AccountBalanceCalculator.calculateAsOfBalance(
            account = testAccount,
            transactions = txList,
            asOfEpochMillis = startOfDay + 86400000L + 5000L
        )
        // 100000 - 20000 - 30000 = 50000
        assertThat(asOfDay2).isEqualTo(50000L)
    }
}
