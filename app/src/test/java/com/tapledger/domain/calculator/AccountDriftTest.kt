package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.CheckpointType
import org.junit.Test

/**
 * Validates Flow.md §6.3: Real-Time Balance Drift & Stability Protocol
 * Formula:
 * BalanceDrift = checkpoint_balance_paise - ExpectedBankBalance
 * Expected:
 * - AVAILABLE_BALANCE: Balance(A, occurred_at)
 * - AVAILABLE_CREDIT_LIMIT: credit_limit_paise + Balance(A, occurred_at)
 * - TOTAL_OUTSTANDING: -Balance(A, occurred_at)
 *
 * Stability Protocol:
 * Banner displayed only if:
 * 1. Latest checkpoint for account
 * 2. Zero unassigned pending transactions for account
 * 3. Elapsed >= 5 minutes (300_000 ms)
 */
class AccountDriftTest {

    private val bankAccount = AccountEntity(
        id = 1,
        name = "HDFC Savings",
        type = AccountType.BANK,
        identifierLast4 = "1234",
        openingBalancePaise = 50_000_00L,
        openingBalanceDate = 1000L,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private val creditCardAccount = AccountEntity(
        id = 2,
        name = "ICICI Amazon Pay",
        type = AccountType.CREDIT_CARD,
        identifierLast4 = "4321",
        openingBalancePaise = -15_000_00L,
        openingBalanceDate = 1000L,
        creditLimitPaise = 100_000_00L, // ₹1,00,000 credit limit
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun testSignedDrift_availableBalance() {
        // App balance at occurred_at = ₹45,000
        val appBalance = 45_000_00L

        // Bank SMS reports ₹48,000 available balance -> Drift = +₹3,000 (missing credit / income)
        val positiveDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = bankAccount,
            checkpointBalancePaise = 48_000_00L,
            checkpointType = CheckpointType.AVAILABLE_BALANCE,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(positiveDrift).isEqualTo(3_000_00L)

        // Bank SMS reports ₹40,000 available balance -> Drift = -₹5,000 (missing debit / expense)
        val negativeDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = bankAccount,
            checkpointBalancePaise = 40_000_00L,
            checkpointType = CheckpointType.AVAILABLE_BALANCE,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(negativeDrift).isEqualTo(-5_000_00L)

        // Exactly matches -> 0L
        val zeroDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = bankAccount,
            checkpointBalancePaise = 45_000_00L,
            checkpointType = CheckpointType.AVAILABLE_BALANCE,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(zeroDrift).isEqualTo(0L)
    }

    @Test
    fun testSignedDrift_availableCreditLimit() {
        // Credit limit = ₹1,00,000, App balance = -₹20,000 (₹20,000 spent)
        // Expected available limit = ₹1,00,000 + (-₹20,000) = ₹80,000
        val appBalance = -20_000_00L

        // Bank reports ₹85,000 available limit -> Drift = 85,000 - 80,000 = +₹5,000
        val positiveDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = creditCardAccount,
            checkpointBalancePaise = 85_000_00L,
            checkpointType = CheckpointType.AVAILABLE_CREDIT_LIMIT,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(positiveDrift).isEqualTo(5_000_00L)

        // Bank reports ₹78,000 available limit -> Drift = 78,000 - 80,000 = -₹2,000
        val negativeDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = creditCardAccount,
            checkpointBalancePaise = 78_000_00L,
            checkpointType = CheckpointType.AVAILABLE_CREDIT_LIMIT,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(negativeDrift).isEqualTo(-2_000_00L)
    }

    @Test
    fun testSignedDrift_totalOutstanding() {
        // Credit card App balance = -₹20,000
        // Expected outstanding = -(-₹20,000) = +₹20,000
        val appBalance = -20_000_00L

        // Bank reports ₹22,000 total outstanding -> Drift = 22,000 - 20,000 = +₹2,000
        val positiveDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = creditCardAccount,
            checkpointBalancePaise = 22_000_00L,
            checkpointType = CheckpointType.TOTAL_OUTSTANDING,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(positiveDrift).isEqualTo(2_000_00L)

        // Bank reports ₹18,000 total outstanding -> Drift = 18,000 - 20,000 = -₹2,000
        val negativeDrift = AccountBalanceCalculator.calculateBalanceDrift(
            account = creditCardAccount,
            checkpointBalancePaise = 18_000_00L,
            checkpointType = CheckpointType.TOTAL_OUTSTANDING,
            balanceAtOccurredAtPaise = appBalance
        )
        assertThat(negativeDrift).isEqualTo(-2_000_00L)
    }

    @Test
    fun testStabilityProtocol_threeConditionsEnforced() {
        val checkpointCreatedAt = 1_000_000L

        // 1. Not latest checkpoint -> suppressed even if older than 5 min and zero pending
        assertThat(
            AccountBalanceCalculator.shouldDisplayDriftBanner(
                isLatestCheckpointForAccount = false,
                hasUnassignedPendingTransactions = false,
                checkpointCreatedAtEpochMillis = checkpointCreatedAt,
                nowEpochMillis = checkpointCreatedAt + 600_000L
            )
        ).isFalse()

        // 2. Has unassigned pending transactions -> suppressed (wait for user/auto attribution)
        assertThat(
            AccountBalanceCalculator.shouldDisplayDriftBanner(
                isLatestCheckpointForAccount = true,
                hasUnassignedPendingTransactions = true,
                checkpointCreatedAtEpochMillis = checkpointCreatedAt,
                nowEpochMillis = checkpointCreatedAt + 600_000L
            )
        ).isFalse()

        // 3. Within 5-minute stability window (4 minutes 59 seconds elapsed) -> suppressed
        assertThat(
            AccountBalanceCalculator.shouldDisplayDriftBanner(
                isLatestCheckpointForAccount = true,
                hasUnassignedPendingTransactions = false,
                checkpointCreatedAtEpochMillis = checkpointCreatedAt,
                nowEpochMillis = checkpointCreatedAt + 299_999L
            )
        ).isFalse()

        // 4. Exactly 5 minutes (300,000 ms) elapsed -> DISPLAY BANNER
        assertThat(
            AccountBalanceCalculator.shouldDisplayDriftBanner(
                isLatestCheckpointForAccount = true,
                hasUnassignedPendingTransactions = false,
                checkpointCreatedAtEpochMillis = checkpointCreatedAt,
                nowEpochMillis = checkpointCreatedAt + 300_000L
            )
        ).isTrue()

        // 5. Well past 5 minutes (10 minutes elapsed) -> DISPLAY BANNER
        assertThat(
            AccountBalanceCalculator.shouldDisplayDriftBanner(
                isLatestCheckpointForAccount = true,
                hasUnassignedPendingTransactions = false,
                checkpointCreatedAtEpochMillis = checkpointCreatedAt,
                nowEpochMillis = checkpointCreatedAt + 600_000L
            )
        ).isTrue()
    }
}
