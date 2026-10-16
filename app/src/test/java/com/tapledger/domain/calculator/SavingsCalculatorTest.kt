package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.SavingsContributionEntity
import com.tapledger.core.database.entity.SavingsGoalEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.GoalTrackStatus
import com.tapledger.core.model.SavingsContributionSource
import com.tapledger.core.model.SavingsGoalStatus
import com.tapledger.core.util.DateTimeUtils
import org.junit.Test
import java.time.YearMonth

class SavingsCalculatorTest {

    @Test
    fun calculateGoalBalance_sumsDepositsAndWithdrawals() {
        val contributions = listOf(
            SavingsContributionEntity(
                id = 1,
                goalId = 10,
                amountPaise = 50_000_00L, // +50,000
                source = SavingsContributionSource.MANUAL,
                createdAt = 1000L
            ),
            SavingsContributionEntity(
                id = 2,
                goalId = 10,
                amountPaise = -10_000_00L, // -10,000
                source = SavingsContributionSource.MANUAL,
                createdAt = 2000L
            ),
            SavingsContributionEntity(
                id = 3,
                goalId = 10,
                amountPaise = 5_000_00L, // +5,000
                source = SavingsContributionSource.TRANSACTION,
                createdAt = 3000L
            )
        )

        val balance = SavingsCalculator.calculateGoalBalance(contributions)
        assertThat(balance).isEqualTo(45_000_00L) // ₹45,000
    }

    @Test
    fun calculateProgressBp_exactCalculations() {
        // 0%
        assertThat(SavingsCalculator.calculateProgressBp(0L, 100_000_00L)).isEqualTo(0)
        // 50% = 5000 bp
        assertThat(SavingsCalculator.calculateProgressBp(50_000_00L, 100_000_00L)).isEqualTo(5000)
        // 33.33% = 3333 bp
        assertThat(SavingsCalculator.calculateProgressBp(33_333_33L, 100_000_00L)).isEqualTo(3333)
        // 100% = 10000 bp
        assertThat(SavingsCalculator.calculateProgressBp(100_000_00L, 100_000_00L)).isEqualTo(10000)
        // Overachieved -> caps at 10000 bp
        assertThat(SavingsCalculator.calculateProgressBp(150_000_00L, 100_000_00L)).isEqualTo(10000)
        // Zero or negative target
        assertThat(SavingsCalculator.calculateProgressBp(500L, 0L)).isEqualTo(10000)
        assertThat(SavingsCalculator.calculateProgressBp(0L, 0L)).isEqualTo(10000)
    }

    @Test
    fun calculateRemainingPaise_calculatesAccurately() {
        assertThat(SavingsCalculator.calculateRemainingPaise(30_000_00L, 100_000_00L)).isEqualTo(70_000_00L)
        assertThat(SavingsCalculator.calculateRemainingPaise(100_000_00L, 100_000_00L)).isEqualTo(0L)
        assertThat(SavingsCalculator.calculateRemainingPaise(120_000_00L, 100_000_00L)).isEqualTo(0L)
    }

    @Test
    fun calculateRequiredMonthlyPaise_ceilingDivision() {
        // ₹10,000 remaining over 3 months -> 10,000 / 3 = 3333.33... -> ceil = ₹3,334 (333400 paise)
        val remaining = 10_000_00L
        val months = 3
        val required = SavingsCalculator.calculateRequiredMonthlyPaise(remaining, months)
        assertThat(required).isEqualTo(333334L)
        // 333334 * 3 = 1000002 >= 1000000 (reaches target)
        assertThat(required * months).isAtLeast(remaining)

        // Remaining 0 -> 0
        assertThat(SavingsCalculator.calculateRequiredMonthlyPaise(0L, 5)).isEqualTo(0L)
        // 0 months left -> full remaining amount required
        assertThat(SavingsCalculator.calculateRequiredMonthlyPaise(50_000L, 0)).isEqualTo(50_000L)
    }

    @Test
    fun calculateTrackStatus_doneWhenTargetReached() {
        val now = DateTimeUtils.getStartOfMonthEpochMillis(2026, 10)
        val goal = SavingsGoalEntity(
            id = 1,
            name = "Laptop",
            targetPaise = 80_000_00L,
            deadlineEpochMs = DateTimeUtils.getEndOfMonthEpochMillis(2026, 12),
            priority = 1,
            status = SavingsGoalStatus.ACTIVE,
            createdAt = DateTimeUtils.getStartOfMonthEpochMillis(2026, 1),
            updatedAt = now
        )

        val status = SavingsCalculator.calculateTrackStatus(goal, 85_000_00L, now)
        assertThat(status).isEqualTo(GoalTrackStatus.DONE)
    }

    @Test
    fun calculateTrackStatus_overdueWhenPastDeadline() {
        val asOf = DateTimeUtils.getStartOfMonthEpochMillis(2026, 11)
        val pastDeadline = DateTimeUtils.getEndOfMonthEpochMillis(2026, 9)
        val goal = SavingsGoalEntity(
            id = 1,
            name = "Trip",
            targetPaise = 50_000_00L,
            deadlineEpochMs = pastDeadline,
            priority = 1,
            status = SavingsGoalStatus.ACTIVE,
            createdAt = DateTimeUtils.getStartOfMonthEpochMillis(2026, 1),
            updatedAt = asOf
        )

        val status = SavingsCalculator.calculateTrackStatus(goal, 30_000_00L, asOf)
        assertThat(status).isEqualTo(GoalTrackStatus.OVERDUE)
    }

    @Test
    fun calculateTrackStatus_onTrackAndBehindScenarios() {
        // Created Jan 2026, Deadline Dec 2026 (12 months total). Target ₹1,20,000 (10k/month).
        // In June 2026 (6 months elapsed): expected progress = 120,000 * 5 / 12 = 50,000.
        val startMs = DateTimeUtils.getStartOfMonthEpochMillis(2026, 1)
        val deadlineMs = DateTimeUtils.getEndOfMonthEpochMillis(2026, 12)
        val juneMs = DateTimeUtils.getStartOfMonthEpochMillis(2026, 6)

        val goal = SavingsGoalEntity(
            id = 1,
            name = "Emergency",
            targetPaise = 1_20_000_00L,
            deadlineEpochMs = deadlineMs,
            priority = 1,
            status = SavingsGoalStatus.ACTIVE,
            createdAt = startMs,
            updatedAt = juneMs
        )

        // Saved ₹60,000 by June -> On Track
        assertThat(SavingsCalculator.calculateTrackStatus(goal, 60_000_00L, juneMs))
            .isEqualTo(GoalTrackStatus.ON_TRACK)

        // Saved only ₹20,000 by June -> Behind
        assertThat(SavingsCalculator.calculateTrackStatus(goal, 20_000_00L, juneMs))
            .isEqualTo(GoalTrackStatus.BEHIND)
    }

    @Test
    fun buildPaceSummary_formatsExpectedIndianStrings() {
        val now = DateTimeUtils.getStartOfMonthEpochMillis(2026, 10)
        val march2027 = DateTimeUtils.getStartOfMonthEpochMillis(2027, 3)

        val goal = SavingsGoalEntity(
            id = 1,
            name = "iPhone",
            targetPaise = 10_000_00L, // ₹10,000
            deadlineEpochMs = march2027,
            priority = 1,
            status = SavingsGoalStatus.ACTIVE,
            createdAt = now,
            updatedAt = now
        )

        val summaryOnTrack = SavingsCalculator.buildPaceSummary(goal, 5_800_00L, now)
        // ₹4,200 remaining, on track for March
        assertThat(summaryOnTrack).contains("₹4,200.00 to go")
        assertThat(summaryOnTrack).contains("on track for March")

        val completedSummary = SavingsCalculator.buildPaceSummary(goal, 10_000_00L, now)
        assertThat(completedSummary).isEqualTo("Goal achieved!")
    }

    @Test
    fun evaluateAccountEarmarks_detectsOverEarmarkedAccounts() {
        val account1 = AccountEntity(
            id = 100,
            name = "HDFC Savings",
            type = AccountType.BANK,
            identifierLast4 = "1234",
            openingBalancePaise = 50_000_00L,
            openingBalanceDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val account2 = AccountEntity(
            id = 200,
            name = "ICICI Bank",
            type = AccountType.BANK,
            identifierLast4 = "5678",
            openingBalancePaise = 1_00_000_00L,
            openingBalanceDate = 1000L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 1000L
        )


        val goal1 = SavingsGoalEntity(
            id = 1,
            name = "Goal 1",
            targetPaise = 40_000_00L,
            status = SavingsGoalStatus.ACTIVE,
            linkedAccountId = 100,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val goal2 = SavingsGoalEntity(
            id = 2,
            name = "Goal 2",
            targetPaise = 30_000_00L,
            status = SavingsGoalStatus.ACTIVE,
            linkedAccountId = 100,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val activeGoals = listOf(goal1, goal2)
        // Goal 1 has 30,000 and Goal 2 has 25,000 -> Total earmarked to Account 100 is 55,000.
        // But Account 100 only has 50,000 balance! Deficit = 5,000.
        val goalBalances = mapOf(1L to 30_000_00L, 2L to 25_000_00L)
        val accountsMap = mapOf(100L to account1, 200L to account2)
        val accountBalances = mapOf(100L to 50_000_00L, 200L to 1_00_000_00L)

        val warnings = SavingsCalculator.evaluateAccountEarmarks(
            activeGoals,
            goalBalances,
            accountsMap,
            accountBalances
        )

        assertThat(warnings).hasSize(1)
        assertThat(warnings[0].accountId).isEqualTo(100L)
        assertThat(warnings[0].accountName).isEqualTo("HDFC Savings")
        assertThat(warnings[0].totalEarmarkedPaise).isEqualTo(55_000_00L)
        assertThat(warnings[0].accountBalancePaise).isEqualTo(50_000_00L)
        assertThat(warnings[0].deficitPaise).isEqualTo(5_000_00L)
    }

    @Test
    fun evaluateAccountEarmarks_withDefaultSavingsAccountId_assignsUnlinkedGoals() {
        val sbiAccount = AccountEntity(
            id = 200,
            name = "SBI Savings",
            type = AccountType.BANK,
            isSavings = true,
            openingBalancePaise = 20_000_00L,
            openingBalanceDate = 1000L,
            identifierLast4 = "1234",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Goal without explicit linkedAccountId
        val unlinkedGoal = SavingsGoalEntity(
            id = 10,
            name = "General Emergency Fund",
            targetPaise = 50_000_00L,
            status = SavingsGoalStatus.ACTIVE,
            linkedAccountId = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val activeGoals = listOf(unlinkedGoal)
        val goalBalances = mapOf(10L to 30_000_00L) // 30,000 earmarked
        val accountsMap = mapOf(200L to sbiAccount)
        val accountBalances = mapOf(200L to 20_000_00L) // 20,000 real balance -> 10,000 deficit

        val warnings = SavingsCalculator.evaluateAccountEarmarks(
            activeGoals = activeGoals,
            goalBalances = goalBalances,
            accountsMap = accountsMap,
            accountBalances = accountBalances,
            defaultSavingsAccountId = 200L
        )

        assertThat(warnings).hasSize(1)
        assertThat(warnings[0].accountId).isEqualTo(200L)
        assertThat(warnings[0].accountName).isEqualTo("SBI Savings")
        assertThat(warnings[0].totalEarmarkedPaise).isEqualTo(30_000_00L)
        assertThat(warnings[0].deficitPaise).isEqualTo(10_000_00L)
    }

    @Test
    fun calculateGoalSummary_withBacking_calculatesCorrectBackedBpAndAmount() {
        val goal = SavingsGoalEntity(
            id = 1,
            name = "Vacation",
            targetPaise = 50_000_00L,
            status = SavingsGoalStatus.ACTIVE,
            linkedAccountId = 200L,
            createdAt = 1000L,
            updatedAt = 1000L
        )

        val summary = SavingsCalculator.calculateGoalSummary(
            goal = goal,
            balancePaise = 20_000_00L,
            asOfEpochMs = 2000L,
            isOverEarmarked = false,
            backedPaise = 12_000_00L,
            backedBp = 6000,
            backingAccountName = "SBI"
        )

        assertThat(summary.backedPaise).isEqualTo(12_000_00L)
        assertThat(summary.backedBp).isEqualTo(6000) // 60%
        assertThat(summary.backingAccountName).isEqualTo("SBI")
    }
}
