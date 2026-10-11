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

    @Test
    fun testRunningBalance_strictlyFollowsOpeningPlusInMinusOut() {
        // Flow.md §6.1: Balance(A) = opening_balance_paise + Σ(IN) - Σ(OUT)
        val salaryIn = TransactionEntity(
            id = 301,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = com.tapledger.core.model.TransactionDirection.IN,
            amountPaise = 100_000_00L,
            occurredAt = 2000L,
            createdAt = 2000L,
            updatedAt = 2000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION
        )
        val rentOut = TransactionEntity(
            id = 302,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 25_000_00L,
            occurredAt = 3000L,
            createdAt = 3000L,
            updatedAt = 3000L,
            status = TransactionStatus.CATEGORIZED,
            source = TransactionSource.MANUAL
        )

        val summary = AccountBalanceCalculator.calculateAccountBalance(
            bankAccount,
            listOf(salaryIn, rentOut)
        )

        // ₹50,000 + ₹100,000 - ₹25,000 = ₹125,000
        assertThat(summary.totalIncomingPaise).isEqualTo(100_000_00L)
        assertThat(summary.totalOutgoingPaise).isEqualTo(25_000_00L)
        assertThat(summary.currentBalancePaise).isEqualTo(125_000_00L)
    }

    @Test
    fun testAsOfBalance_boundaryRules_startOfDayISTAndAsOfCaps() {
        // Opening balance date = 1704067200000L (2024-01-01 00:00:00 UTC / 05:30 IST)
        // StartOfDayIST of openingBalanceDate is 1704047400000L (2024-01-01 00:00:00 IST)
        val openingDateEpoch = 1704067200000L
        val testAccount = bankAccount.copy(
            openingBalanceDate = openingDateEpoch,
            openingBalancePaise = 10_000_00L
        )

        val startOfDayIST = com.tapledger.core.util.DateTimeUtils.getStartOfDayEpochMillis(openingDateEpoch)

        // 1. Transaction BEFORE StartOfDayIST of opening date -> MUST BE EXCLUDED
        val beforeOpening = TransactionEntity(
            id = 401,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = com.tapledger.core.model.TransactionDirection.IN,
            amountPaise = 5_000_00L,
            occurredAt = startOfDayIST - 1000L,
            createdAt = startOfDayIST - 1000L,
            updatedAt = startOfDayIST - 1000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.MANUAL
        )

        // 2. Transaction ON opening day
        val onOpeningDay = TransactionEntity(
            id = 402,
            accountId = 1,
            kind = TransactionKind.EXPENSE,
            direction = com.tapledger.core.model.TransactionDirection.OUT,
            amountPaise = 2_000_00L,
            occurredAt = startOfDayIST + 3600_000L,
            createdAt = startOfDayIST + 3600_000L,
            updatedAt = startOfDayIST + 3600_000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION
        )

        // 3. Transaction at T = 1704153600000L (2024-01-02)
        val dayTwoTx = TransactionEntity(
            id = 403,
            accountId = 1,
            kind = TransactionKind.INCOME,
            direction = com.tapledger.core.model.TransactionDirection.IN,
            amountPaise = 8_000_00L,
            occurredAt = startOfDayIST + 86400_000L + 1000L,
            createdAt = startOfDayIST + 86400_000L + 1000L,
            updatedAt = startOfDayIST + 86400_000L + 1000L,
            status = TransactionStatus.CONFIRMED,
            source = TransactionSource.NOTIFICATION
        )

        val txs = listOf(beforeOpening, onOpeningDay, dayTwoTx)

        // As of end of Day 1: should include opening balance (10,000) - day 1 expense (2,000) = 8,000
        val asOfDayOne = AccountBalanceCalculator.calculateAsOfBalance(
            testAccount,
            txs,
            asOfEpochMillis = startOfDayIST + 86400_000L
        )
        assertThat(asOfDayOne).isEqualTo(8_000_00L)

        // As of end of Day 2: 8,000 + 8,000 = 16,000
        val asOfDayTwo = AccountBalanceCalculator.calculateAsOfBalance(
            testAccount,
            txs,
            asOfEpochMillis = startOfDayIST + 86400_000L * 2
        )
        assertThat(asOfDayTwo).isEqualTo(16_000_00L)
    }

    @Test
    fun testTypeAgnosticNetWorthIdentity() {
        // Flow.md §6.2: Assets = Σ max(Balance(A), 0), Liabilities = Σ max(-Balance(A), 0)
        // NetWorth = Assets - Liabilities = Σ Balance(A)
        // Test across all 6 account types: BANK, CREDIT_CARD, CASH, INVESTMENT, LOAN, WALLET

        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, identifierLast4 = null, openingBalancePaise = 50_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val investment = AccountEntity(id = 2, name = "MF", type = AccountType.INVESTMENT, identifierLast4 = null, openingBalancePaise = 200_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val wallet = AccountEntity(id = 3, name = "Paytm", type = AccountType.WALLET, identifierLast4 = null, openingBalancePaise = 1_500_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val cash = AccountEntity(id = 4, name = "Cash", type = AccountType.CASH, identifierLast4 = null, openingBalancePaise = 3_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val creditCard = AccountEntity(id = 5, name = "CC", type = AccountType.CREDIT_CARD, identifierLast4 = null, openingBalancePaise = -25_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val loan = AccountEntity(id = 6, name = "Home Loan", type = AccountType.LOAN, identifierLast4 = null, openingBalancePaise = -100_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val inactiveBank = AccountEntity(id = 7, name = "Old Bank", type = AccountType.BANK, identifierLast4 = null, openingBalancePaise = 10_000_00L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L, isActive = false)

        val summaries = listOf(
            AccountBalanceCalculator.calculateAccountBalance(bank, emptyList()),        // +50,000
            AccountBalanceCalculator.calculateAccountBalance(investment, emptyList()),  // +200,000
            AccountBalanceCalculator.calculateAccountBalance(wallet, emptyList()),      // +1,500
            AccountBalanceCalculator.calculateAccountBalance(cash, emptyList()),        // +3,000
            AccountBalanceCalculator.calculateAccountBalance(creditCard, emptyList()),  // -25,000
            AccountBalanceCalculator.calculateAccountBalance(loan, emptyList()),        // -100,000
            AccountBalanceCalculator.calculateAccountBalance(inactiveBank, emptyList()) // Inactive (ignored)
        )

        val netWorth = AccountBalanceCalculator.calculateNetWorth(summaries)

        // Assets = 50,000 + 200,000 + 1,500 + 3,000 = 254,500
        assertThat(netWorth.totalAssetsPaise).isEqualTo(254_500_00L)
        // Liabilities = 25,000 + 100,000 = 125,000
        assertThat(netWorth.totalLiabilitiesPaise).isEqualTo(125_000_00L)
        // NetWorth = 254,500 - 125,000 = 129,500
        assertThat(netWorth.netWorthPaise).isEqualTo(129_500_00L)
        // Universal identity verification: Assets - Liabilities == netWorthPaise
        assertThat(netWorth.netWorthPaise).isEqualTo(netWorth.totalAssetsPaise - netWorth.totalLiabilitiesPaise)
    }

    @Test
    fun testCreditCardUtilization_clampedBetween0And999() {
        val cc = AccountEntity(
            id = 2,
            name = "CC",
            type = AccountType.CREDIT_CARD,
            identifierLast4 = "1234",
            openingBalancePaise = 0L,
            openingBalanceDate = 0L,
            creditLimitPaise = 100_000_00L, // ₹1,00,000 limit
            createdAt = 0L,
            updatedAt = 0L
        )

        // 1. Normal utilization: ₹35,000 outstanding (balance = -35,000) on ₹100,000 limit -> 35%
        assertThat(AccountBalanceCalculator.calculateCreditCardUtilization(cc, -35_000_00L)).isEqualTo(35)

        // 2. Zero limit account -> 0%
        val zeroLimitCc = cc.copy(creditLimitPaise = 0L)
        assertThat(AccountBalanceCalculator.calculateCreditCardUtilization(zeroLimitCc, -35_000_00L)).isEqualTo(0)

        // 3. Positive balance (overpayment: ₹5,000) -> 0%
        assertThat(AccountBalanceCalculator.calculateCreditCardUtilization(cc, 5_000_00L)).isEqualTo(0)

        // 4. Zero balance -> 0%
        assertThat(AccountBalanceCalculator.calculateCreditCardUtilization(cc, 0L)).isEqualTo(0)

        // 5. Over limit: ₹1,500,000 outstanding on ₹100,000 limit (1500%) -> clamped to 999%
        assertThat(AccountBalanceCalculator.calculateCreditCardUtilization(cc, -1_500_000_00L)).isEqualTo(999)
    }

    @Test
    fun testNegativeAccountWarning_triggersForBankCashWalletOnly() {
        val bank = AccountEntity(id = 1, name = "Bank", type = AccountType.BANK, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val cash = AccountEntity(id = 2, name = "Cash", type = AccountType.CASH, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val wallet = AccountEntity(id = 3, name = "Wallet", type = AccountType.WALLET, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val cc = AccountEntity(id = 4, name = "CC", type = AccountType.CREDIT_CARD, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)
        val loan = AccountEntity(id = 5, name = "Loan", type = AccountType.LOAN, identifierLast4 = null, openingBalancePaise = 0L, openingBalanceDate = 0L, createdAt = 0L, updatedAt = 0L)

        // Bank negative -> warning true
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(bank, -100_00L)).isTrue()
        // Bank positive -> warning false
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(bank, 500_00L)).isFalse()

        // Cash negative -> warning true
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(cash, -50_00L)).isTrue()

        // Wallet negative -> warning true
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(wallet, -1_00L)).isTrue()

        // Credit Card negative is NORMAL liability -> warning false
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(cc, -50_000_00L)).isFalse()

        // Loan negative is NORMAL liability -> warning false
        assertThat(AccountBalanceCalculator.hasDataInconsistencyWarning(loan, -500_000_00L)).isFalse()
    }
}
