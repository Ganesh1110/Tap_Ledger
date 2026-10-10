package com.tapledger.domain.validator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.database.entity.TransactionAllocationEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import org.junit.Test

class DataIntegrityCheckerTest {

    private val checker = DataIntegrityChecker()

    private val validAccount = AccountEntity(
        id = 1,
        name = "HDFC Savings",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 50_000_00L,
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val validCategory = CategoryEntity(
        id = 10,
        name = "Groceries",
        defaultBucket = BudgetBucket.NEEDS,
        defaultSortOrder = 1,
        defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun verifyIntegrity_flagsSplitSumMismatch() {
        // Transaction is ₹1,000, but allocation is only ₹800
        val tx = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 1_000_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val alloc = TransactionAllocationEntity(
            id = 501,
            transactionId = 101,
            categoryId = 10,
            amountPaise = 800_00L // Mismatch!
        )

        val issues = checker.verifyIntegrity(
            accounts = listOf(validAccount),
            categories = listOf(validCategory),
            periods = emptyList(),
            allocations = emptyList(),
            transactions = listOf(tx),
            transactionAllocations = listOf(alloc),
            monthCloses = emptyList()
        )

        val splitIssue = issues.firstOrNull { it.ruleId == "A1_SPLIT_SUM_MISMATCH" }
        assertThat(splitIssue).isNotNull()
    }

    @Test
    fun verifyIntegrity_flagsUnbalancedTransfer() {
        // Transfer group has only 1 leg instead of 2
        val singleLeg = TransactionEntity(
            id = 201,
            accountId = 1,
            kind = TransactionKind.TRANSFER,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 5_000_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL,
            transferGroupId = "999"
        )

        val issues = checker.verifyIntegrity(
            accounts = listOf(validAccount),
            categories = listOf(validCategory),
            periods = emptyList(),
            allocations = emptyList(),
            transactions = listOf(singleLeg),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )

        val transferIssue = issues.firstOrNull { it.ruleId == "TR1_UNBALANCED_TRANSFER" }
        assertThat(transferIssue).isNotNull()
    }

    @Test
    fun verifyIntegrity_flagsInvalidAccountReference() {
        // Transaction references non-existent account 999
        val invalidTx = TransactionEntity(
            id = 301,
            accountId = 999, // Missing!
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 500_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )

        val issues = checker.verifyIntegrity(
            accounts = listOf(validAccount),
            categories = listOf(validCategory),
            periods = emptyList(),
            allocations = emptyList(),
            transactions = listOf(invalidTx),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )

        val accountIssue = issues.firstOrNull { it.ruleId == "T1_INVALID_ACCOUNT" }
        assertThat(accountIssue).isNotNull()
    }
}
