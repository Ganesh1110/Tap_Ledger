package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.TransactionEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection
import org.junit.Test

class AccountBalanceCalculatorTest {

    private val bankAccount = AccountEntity(
        id = 1,
        name = "HDFC Savings",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 50_000_00L, // ₹50,000
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val creditCardAccount = AccountEntity(
        id = 2,
        name = "HDFC Credit Card",
        type = AccountType.CREDIT_CARD,
        identifierLast4 = "5678",
        openingBalancePaise = -8_000_00L, // -₹8,000 liability
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun pendingTransaction_affectsAccountBalance() {
        // Pending transaction of ₹850 must reduce bank balance immediately (D002)
        val pendingExpense = TransactionEntity(
            id = 101,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 850_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.PENDING,
            source = TransactionSource.NOTIFICATION
        )

        val summary = AccountBalanceCalculator.calculateAccountBalance(
            bankAccount,
            listOf(pendingExpense)
        )

        // ₹50,000 - ₹850 = ₹49,150
        assertThat(summary.currentBalancePaise).isEqualTo(49_150_00L)
    }

    @Test
    fun ignoredAndVoided_excludedFromAccountBalance() {
        val ignoredTx = TransactionEntity(
            id = 102,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 500_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.IGNORED,
            source = TransactionSource.NOTIFICATION
        )

        val voidedTx = TransactionEntity(
            id = 103,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 1000_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.VOIDED,
            source = TransactionSource.MANUAL
        )

        val summary = AccountBalanceCalculator.calculateAccountBalance(
            bankAccount,
            listOf(ignoredTx, voidedTx)
        )

        // Neither affects balance
        assertThat(summary.currentBalancePaise).isEqualTo(50_000_00L)
    }

    @Test
    fun creditCardSignConvention_purchasesIncreaseLiability_paymentsDecreaseLiability() {
        // Purchase of ₹2,000 increases liability from -₹8,000 to -₹10,000
        val ccPurchase = TransactionEntity(
            id = 201,
            accountId = 2,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 2_000_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.NOTIFICATION
        )

        // Bill payment (Transfer IN) of ₹5,000 reduces liability to -₹5,000
        val ccPayment = TransactionEntity(
            id = 202,
            accountId = 2,
            kind = TransactionKind.TRANSFER,
            direction = com.tapledger.core.model.TransactionDirection.IN,
            amountPaise = 5_000_00L,
            occurredAt = 3000L,
            createdAt = 3000L,
            updatedAt = 3000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL,
            transferGroupId = "55"
        )

        val summary = AccountBalanceCalculator.calculateAccountBalance(
            creditCardAccount,
            listOf(ccPurchase, ccPayment)
        )

        // -8,000 - 2,000 + 5,000 = -5,000
        assertThat(summary.currentBalancePaise).isEqualTo(-5_000_00L)
    }

    @Test
    fun netWorthCalculation_combinesAssetsAndLiabilities() {
        val bankSummary = AccountBalanceCalculator.calculateAccountBalance(
            bankAccount,
            emptyList()
        ) // +₹50,000
        val ccSummary = AccountBalanceCalculator.calculateAccountBalance(
            creditCardAccount,
            emptyList()
        ) // -₹8,000

        val netWorth = AccountBalanceCalculator.calculateNetWorth(listOf(bankSummary, ccSummary))

        assertThat(netWorth.totalAssetsPaise).isEqualTo(50_000_00L)
        assertThat(netWorth.totalLiabilitiesPaise).isEqualTo(8_000_00L)
        assertThat(netWorth.netWorthPaise).isEqualTo(42_000_00L) // ₹50,000 - ₹8,000 = ₹42,000
    }
}
