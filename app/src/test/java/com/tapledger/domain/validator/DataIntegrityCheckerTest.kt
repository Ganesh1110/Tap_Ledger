package com.tapledger.domain.validator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.MonthCloseEntity
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.domain.model.IntegritySeverity
import org.junit.Test

class DataIntegrityCheckerTest {

    private val checker = DataIntegrityChecker()

    private val account1 = AccountEntity(
        id = 1,
        name = "Checking",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 50_000_00L,
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val account2 = AccountEntity(
        id = 2,
        name = "Savings",
        type = AccountType.BANK,
        identifierLast4 = "5678",
        openingBalancePaise = 10_000_00L,
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val category1 = CategoryEntity(
        id = 10,
        name = "Groceries",
        defaultBucket = BudgetBucket.NEEDS,
        defaultSortOrder = 1,
        defaultAdvancePolicy = AdvancePolicy.EXPIRE,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val category2 = CategoryEntity(
        id = 20,
        name = "Dining",
        defaultBucket = BudgetBucket.WANTS,
        defaultSortOrder = 2,
        defaultAdvancePolicy = AdvancePolicy.EXPIRE,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val period1 = BudgetPeriodEntity(
        id = 100,
        year = 2024,
        month = 1,
        startAt = 10000L,
        endAt = 20000L,
        incomePaise = 100_000_00L,
        isReviewed = true,
        status = BudgetPeriodStatus.OPEN,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun testCleanDatasetProducesZeroIssues() {
        val issues = checker.verifyIntegrity(
            accounts = listOf(account1, account2),
            categories = listOf(category1, category2),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        assertThat(issues).isEmpty()
    }

    @Test
    fun testAll19FormalInvariants() {
        // -------------------------------------------------------------
        // Rule 1: T1_INVALID_ACCOUNT (CRITICAL)
        // -------------------------------------------------------------
        val txT1 = TransactionEntity(
            id = 1,
            accountId = 999L, // Non-existent account
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50_00L,
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CONFIRMED, // Not PENDING, IGNORED, or VOIDED
            source = TransactionSource.MANUAL
        )
        val issuesT1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txT1),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueT1 = issuesT1.find { it.ruleId == "T1_INVALID_ACCOUNT" }
        assertThat(issueT1).isNotNull()
        assertThat(issueT1!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 2: T2_NON_POSITIVE_AMOUNT (ERROR)
        // -------------------------------------------------------------
        val txT2 = TransactionEntity(
            id = 2,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 0L, // Non-positive amount!
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.MANUAL
        )
        val issuesT2 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txT2),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueT2 = issuesT2.find { it.ruleId == "T2_NON_POSITIVE_AMOUNT" }
        assertThat(issueT2).isNotNull()
        assertThat(issueT2!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 3: T3_UNPAIRED_TRANSFER (CRITICAL)
        // -------------------------------------------------------------
        val txT3Leg1 = TransactionEntity(
            id = 31,
            accountId = 1L,
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.OUT,
            amountPaise = 100_00L,
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            transferGroupId = "GRP_1",
            counterpartyAccountId = 2L
        )
        val txT3Leg2Mismatched = TransactionEntity(
            id = 32,
            accountId = 2L,
            kind = TransactionKind.TRANSFER,
            direction = TransactionDirection.IN,
            amountPaise = 90_00L, // Mismatched amount!
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            transferGroupId = "GRP_1",
            counterpartyAccountId = 1L
        )
        val issuesT3 = checker.verifyIntegrity(
            accounts = listOf(account1, account2),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txT3Leg1, txT3Leg2Mismatched),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueT3 = issuesT3.find { it.ruleId == "T3_UNPAIRED_TRANSFER" }
        assertThat(issueT3).isNotNull()
        assertThat(issueT3!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 4: T4_DUPLICATE_UPI_REF (ERROR)
        // -------------------------------------------------------------
        val txT4A = TransactionEntity(
            id = 41,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50_00L,
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION,
            upiReference = "UPI_DUP_123"
        )
        val txT4B = TransactionEntity(
            id = 42,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50_00L,
            occurredAt = 12500L,
            createdAt = 12500L,
            updatedAt = 12500L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION,
            upiReference = "UPI_DUP_123" // Duplicate!
        )
        val issuesT4 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txT4A, txT4B),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueT4 = issuesT4.find { it.ruleId == "T4_DUPLICATE_UPI_REF" }
        assertThat(issueT4).isNotNull()
        assertThat(issueT4!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 5: A1_SPLIT_SUM_MISMATCH (CRITICAL)
        // -------------------------------------------------------------
        val txA1 = TransactionEntity(
            id = 51,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 100_00L,
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val allocA1 = TransactionAllocationEntity(
            id = 501,
            transactionId = 51,
            categoryId = 10,
            amountPaise = 80_00L // Split is ₹80 vs Tx ₹100
        )
        val issuesA1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txA1),
            transactionAllocations = listOf(allocA1),
            monthCloses = emptyList()
        )
        val issueA1 = issuesA1.find { it.ruleId == "A1_SPLIT_SUM_MISMATCH" }
        assertThat(issueA1).isNotNull()
        assertThat(issueA1!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 6: A2_INVALID_CATEGORY (ERROR)
        // -------------------------------------------------------------
        val allocA2 = TransactionAllocationEntity(
            id = 601,
            transactionId = 51,
            categoryId = 9999L, // Non-existent category!
            amountPaise = 100_00L
        )
        val issuesA2 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txA1),
            transactionAllocations = listOf(allocA2),
            monthCloses = emptyList()
        )
        val issueA2 = issuesA2.find { it.ruleId == "A2_INVALID_CATEGORY" }
        assertThat(issueA2).isNotNull()
        assertThat(issueA2!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 7: A3_NON_POSITIVE_ALLOCATION (ERROR)
        // -------------------------------------------------------------
        val allocA3 = TransactionAllocationEntity(
            id = 701,
            transactionId = 51,
            categoryId = 10,
            amountPaise = 0L // Non-positive!
        )
        val issuesA3 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txA1),
            transactionAllocations = listOf(allocA3),
            monthCloses = emptyList()
        )
        val issueA3 = issuesA3.find { it.ruleId == "A3_NON_POSITIVE_ALLOCATION" }
        assertThat(issueA3).isNotNull()
        assertThat(issueA3!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 8: A4_DUPLICATE_CATEGORY_IN_SPLIT (ERROR)
        // -------------------------------------------------------------
        val allocA4A = TransactionAllocationEntity(id = 801, transactionId = 51, categoryId = 10, amountPaise = 50_00L)
        val allocA4B = TransactionAllocationEntity(id = 802, transactionId = 51, categoryId = 10, amountPaise = 50_00L)
        val issuesA4 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(txA1),
            transactionAllocations = listOf(allocA4A, allocA4B),
            monthCloses = emptyList()
        )
        val issueA4 = issuesA4.find { it.ruleId == "A4_DUPLICATE_CATEGORY_IN_SPLIT" }
        assertThat(issueA4).isNotNull()
        assertThat(issueA4!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 9: B1_INVALID_ADJUSTMENT (ERROR)
        // -------------------------------------------------------------
        val adjB1 = BudgetAdjustmentEntity(
            id = 901,
            budgetPeriodId = 100,
            fromCategoryId = 10,
            toCategoryId = 9999L, // Non-existent category!
            amountPaise = 10_00L,
            reason = "Test",
            createdAt = 1000L
        )
        val issuesB1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList(),
            adjustments = listOf(adjB1)
        )
        val issueB1 = issuesB1.find { it.ruleId == "B1_INVALID_ADJUSTMENT" }
        assertThat(issueB1).isNotNull()
        assertThat(issueB1!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 10: B2_NEGATIVE_BUDGET (CRITICAL)
        // -------------------------------------------------------------
        val allocB2 = BudgetAllocationEntity(
            id = 1001,
            budgetPeriodId = 100,
            categoryId = 10,
            categoryNameSnapshot = "Groceries",
            bucketSnapshot = BudgetBucket.NEEDS,
            sortOrderSnapshot = 1,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 10_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        // Negative advance of ₹20 against base of ₹10 -> Adjusted = -₹10
        val advB2 = AdvanceLedgerEntity(
            id = 1002,
            sourcePeriodId = 99,
            appliedToPeriodId = 100,
            categoryId = 10,
            amountPaise = 20_00L,
            direction = AdvanceDirection.NEGATIVE,
            createdAt = 1000L
        )
        val issuesB2 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = listOf(allocB2),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList(),
            advances = listOf(advB2)
        )
        val issueB2 = issuesB2.find { it.ruleId == "B2_NEGATIVE_BUDGET" }
        assertThat(issueB2).isNotNull()
        assertThat(issueB2!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 11: B3_BUCKET_OVERALLOCATION (WARNING)
        // -------------------------------------------------------------
        // Period income is ₹1,00,000 -> Needs cap (50%) is ₹50,000. Allocate ₹60,000.
        val allocB3 = BudgetAllocationEntity(
            id = 1101,
            budgetPeriodId = 100,
            categoryId = 10,
            categoryNameSnapshot = "Groceries",
            bucketSnapshot = BudgetBucket.NEEDS,
            sortOrderSnapshot = 1,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 60_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val issuesB3 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = listOf(allocB3),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueB3 = issuesB3.find { it.ruleId == "B3_BUCKET_OVERALLOCATION" }
        assertThat(issueB3).isNotNull()
        assertThat(issueB3!!.severity).isEqualTo(IntegritySeverity.WARNING)

        // -------------------------------------------------------------
        // Rule 12: B4_BUDGET_EQUILIBRIUM (WARNING)
        // -------------------------------------------------------------
        // Period income ₹1,00,000. Total allocated ₹1,20,000.
        val allocB4 = BudgetAllocationEntity(
            id = 1201,
            budgetPeriodId = 100,
            categoryId = 10,
            categoryNameSnapshot = "Groceries",
            bucketSnapshot = BudgetBucket.NEEDS,
            sortOrderSnapshot = 1,
            advancePolicySnapshot = AdvancePolicy.EXPIRE,
            amountPaise = 120_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val issuesB4 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = listOf(allocB4),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueB4 = issuesB4.find { it.ruleId == "B4_BUDGET_EQUILIBRIUM" }
        assertThat(issueB4).isNotNull()
        assertThat(issueB4!!.severity).isEqualTo(IntegritySeverity.WARNING)

        // -------------------------------------------------------------
        // Rule 13: C1_DUPLICATE_CATEGORY (WARNING)
        // -------------------------------------------------------------
        val catC1A = CategoryEntity(id = 131, name = "Rent", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1, isActive = true)
        val catC1B = CategoryEntity(id = 132, name = "rent ", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 2, isActive = true)
        val issuesC1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(catC1A, catC1B),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueC1 = issuesC1.find { it.ruleId == "C1_DUPLICATE_CATEGORY" }
        assertThat(issueC1).isNotNull()
        assertThat(issueC1!!.severity).isEqualTo(IntegritySeverity.WARNING)

        // -------------------------------------------------------------
        // Rule 14: P1_PERIOD_OVERLAP (CRITICAL)
        // -------------------------------------------------------------
        val p14A = BudgetPeriodEntity(id = 141, year = 2024, month = 1, startAt = 1000L, endAt = 2500L, incomePaise = 50_00L, createdAt = 1000L, updatedAt = 1000L)
        val p14B = BudgetPeriodEntity(id = 142, year = 2024, month = 2, startAt = 2000L, endAt = 3000L, incomePaise = 50_00L, createdAt = 1000L, updatedAt = 1000L) // Overlaps [2000, 2500)
        val issuesP1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(p14A, p14B),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueP1 = issuesP1.find { it.ruleId == "P1_PERIOD_OVERLAP" }
        assertThat(issueP1).isNotNull()
        assertThat(issueP1!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 15: M1_LOCKED_MODIFICATION (CRITICAL)
        // -------------------------------------------------------------
        val closedPeriod = BudgetPeriodEntity(
            id = 150,
            year = 2024,
            month = 1,
            startAt = 1000L,
            endAt = 2000L,
            incomePaise = 50_00L,
            status = BudgetPeriodStatus.CLOSED,
            closedAt = 2000L,
            createdAt = 1000L,
            updatedAt = 2000L
        )
        val txM1 = TransactionEntity(
            id = 151,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 10_00L,
            occurredAt = 1500L, // in [1000, 2000)
            createdAt = 2500L,  // created AFTER closedAt (2000L)!
            updatedAt = 2500L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val issuesM1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(closedPeriod),
            allocations = emptyList(),
            transactions = listOf(txM1),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueM1 = issuesM1.find { it.ruleId == "M1_LOCKED_MODIFICATION" }
        assertThat(issueM1).isNotNull()
        assertThat(issueM1!!.severity).isEqualTo(IntegritySeverity.CRITICAL)

        // -------------------------------------------------------------
        // Rule 16: M2_CLOSE_TOTALS_MISMATCH (ERROR)
        // -------------------------------------------------------------
        val monthCloseM2 = MonthCloseEntity(
            id = 161,
            budgetPeriodId = 150,
            closedAt = 2000L,
            plannedIncomePaise = 999_99L, // Does not match period income (50_00L)!
            actualIncomePaise = 50_00L,
            totalBudgetedPaise = 50_00L,
            totalSpentPaise = 0L,
            totalSavedPaise = 0L,
            reconciliationStatus = MonthCloseReconciliationStatus.UNRECONCILED,
            closingBalancesJson = "{}"
        )
        val issuesM2 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(closedPeriod),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = listOf(monthCloseM2)
        )
        val issueM2 = issuesM2.find { it.ruleId == "M2_CLOSE_TOTALS_MISMATCH" }
        assertThat(issueM2).isNotNull()
        assertThat(issueM2!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 17: S1_STATEMENT_MISMATCH (WARNING)
        // -------------------------------------------------------------
        val stmtS1 = StatementImportEntity(
            id = 171,
            accountId = 1L,
            periodStart = 1000L,
            periodEnd = 2000L,
            openingBalancePaise = 100_00L,
            closingBalancePaise = 150_00L, // Reported ₹150
            totalDebitsPaise = 0L,
            totalCreditsPaise = 40_00L,    // Credits ₹40 -> Expected ₹140 != ₹150!
            lineCount = 1,
            createdAt = 1000L
        )
        val stmtLineS1 = StatementLineEntity(
            id = 1701,
            importId = 171,
            transactionDate = 1500L,
            description = "Credit",
            creditPaise = 40_00L,
            debitPaise = 0L
        )
        val issuesS1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList(),
            statementImports = listOf(stmtS1),
            statementLines = listOf(stmtLineS1)
        )
        val issueS1 = issuesS1.find { it.ruleId == "S1_STATEMENT_MISMATCH" }
        assertThat(issueS1).isNotNull()
        assertThat(issueS1!!.severity).isEqualTo(IntegritySeverity.WARNING)

        // -------------------------------------------------------------
        // Rule 18: R1_CUMULATIVE_REFUND_EXCEEDED (ERROR)
        // -------------------------------------------------------------
        val origTxR1 = TransactionEntity(
            id = 181,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 50_00L, // Original charge ₹50
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        val refundTxR1 = TransactionEntity(
            id = 182,
            accountId = 1L,
            kind = TransactionKind.REFUND,
            direction = TransactionDirection.IN,
            amountPaise = 60_00L, // Refund ₹60 > ₹50!
            occurredAt = 13000L,
            createdAt = 13000L,
            updatedAt = 13000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL,
            refundOfTransactionId = 181
        )
        val issuesR1 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(origTxR1, refundTxR1),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        val issueR1 = issuesR1.find { it.ruleId == "R1_CUMULATIVE_REFUND_EXCEEDED" }
        assertThat(issueR1).isNotNull()
        assertThat(issueR1!!.severity).isEqualTo(IntegritySeverity.ERROR)

        // -------------------------------------------------------------
        // Rule 19: R2_REFUND_EXCEEDS_CATEGORY_CAP (ERROR)
        // -------------------------------------------------------------
        val origTxR2 = TransactionEntity(
            id = 191,
            accountId = 1L,
            kind = TransactionKind.EXPENSE,
            direction = TransactionDirection.OUT,
            amountPaise = 100_00L,
            occurredAt = 12000L,
            createdAt = 12000L,
            updatedAt = 12000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )
        // Split: Cat 10 gets ₹40, Cat 20 gets ₹60
        val origAllocR2A = TransactionAllocationEntity(id = 1901, transactionId = 191, categoryId = 10, amountPaise = 40_00L)
        val origAllocR2B = TransactionAllocationEntity(id = 1902, transactionId = 191, categoryId = 20, amountPaise = 60_00L)

        val refundTxR2 = TransactionEntity(
            id = 192,
            accountId = 1L,
            kind = TransactionKind.REFUND,
            direction = TransactionDirection.IN,
            amountPaise = 50_00L, // Total refund ₹50 <= ₹100
            occurredAt = 13000L,
            createdAt = 13000L,
            updatedAt = 13000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL,
            refundOfTransactionId = 191
        )
        // But Cat 10 is refunded ₹50, which exceeds Cat 10's original cap of ₹40!
        val refundAllocR2 = TransactionAllocationEntity(id = 1903, transactionId = 192, categoryId = 10, amountPaise = 50_00L)

        val issuesR2 = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1, category2),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = listOf(origTxR2, refundTxR2),
            transactionAllocations = listOf(origAllocR2A, origAllocR2B, refundAllocR2),
            monthCloses = emptyList()
        )
        val issueR2 = issuesR2.find { it.ruleId == "R2_REFUND_EXCEEDS_CATEGORY_CAP" }
        assertThat(issueR2).isNotNull()
        assertThat(issueR2!!.severity).isEqualTo(IntegritySeverity.ERROR)
    }

    @Test
    fun testSavingsIntegrity_catchesInvalidTarget_negativeBalance_andOrphanedContributions() {
        val validGoal = com.tapledger.core.database.entity.SavingsGoalEntity(
            id = 1,
            name = "Bike",
            targetPaise = 50_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val invalidTargetGoal = com.tapledger.core.database.entity.SavingsGoalEntity(
            id = 2,
            name = "Zero Target",
            targetPaise = 0L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val negativeBalanceGoal = com.tapledger.core.database.entity.SavingsGoalEntity(
            id = 3,
            name = "Negative Balance",
            targetPaise = 10_000_00L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val contribValid = com.tapledger.core.database.entity.SavingsContributionEntity(
            id = 10,
            goalId = 1,
            amountPaise = 20_000_00L,
            source = com.tapledger.core.model.SavingsContributionSource.MANUAL,
            createdAt = 1000L
        )
        val contribNegative = com.tapledger.core.database.entity.SavingsContributionEntity(
            id = 11,
            goalId = 3,
            amountPaise = -5_000_00L,
            source = com.tapledger.core.model.SavingsContributionSource.MANUAL,
            createdAt = 1000L
        )
        val contribOrphan = com.tapledger.core.database.entity.SavingsContributionEntity(
            id = 12,
            goalId = 999, // Non-existent goal
            amountPaise = 10_000_00L,
            source = com.tapledger.core.model.SavingsContributionSource.MANUAL,
            createdAt = 1000L
        )

        val issues = checker.verifyIntegrity(
            accounts = listOf(account1),
            categories = listOf(category1),
            periods = listOf(period1),
            allocations = emptyList(),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList(),
            adjustments = emptyList<BudgetAdjustmentEntity>(),
            advances = emptyList<AdvanceLedgerEntity>(),
            statementImports = emptyList<StatementImportEntity>(),
            statementLines = emptyList<StatementLineEntity>(),
            savingsGoals = listOf(validGoal, invalidTargetGoal, negativeBalanceGoal),
            savingsContributions = listOf(contribValid, contribNegative, contribOrphan)
        )

        val sg1 = issues.find { it.ruleId == "SG1_INVALID_GOAL_TARGET" }
        assertThat(sg1).isNotNull()
        assertThat(sg1!!.entityId).isEqualTo("2")

        val sg2 = issues.find { it.ruleId == "SG2_GOAL_NEGATIVE_BALANCE" }
        assertThat(sg2).isNotNull()
        assertThat(sg2!!.entityId).isEqualTo("3")
        assertThat(sg2.severity).isEqualTo(IntegritySeverity.CRITICAL)

        val sg3 = issues.find { it.ruleId == "SG3_ORPHANED_SAVINGS_CONTRIBUTION" }
        assertThat(sg3).isNotNull()
        assertThat(sg3!!.entityId).isEqualTo("12")
    }
}

