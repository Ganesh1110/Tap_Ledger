package com.tapledger.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SafeDailySpendCalculatorTest {

    private val dayMillis = 86_400_000L
    private val startAt = 1_700_000_000_000L
    private val totalDays = 31
    private val endAt = startAt + totalDays * dayMillis

    @Test
    fun testDay1_FullRemainingBudgetDistributedEvenly() {
        // ₹31,000 budget, 0 spent, Day 1
        val heroBudget = 31_000_00L
        val heroSpent = 0L
        val now = startAt + 1000L // Day 1

        val result = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = heroBudget,
            heroSpentPaise = heroSpent,
            periodStartAt = startAt,
            periodEndAt = endAt,
            nowIST = now,
            isClosed = false
        )

        assertThat(result.isPeriodActive).isTrue()
        assertThat(result.isOverBudget).isFalse()
        assertThat(result.totalDaysInPeriod).isEqualTo(31)
        assertThat(result.elapsedDays).isEqualTo(1)
        assertThat(result.daysRemaining).isEqualTo(31)
        // ₹31,000 / 31 days = ₹1,000 / day
        assertThat(result.safeDailyPaise).isEqualTo(1_000_00L)
        assertThat(result.remainingBudgetPaise).isEqualTo(31_000_00L)
    }

    @Test
    fun testMidMonth_OnTrack_CalculatesRemainingDaysAccurately() {
        // ₹31,000 budget, ₹18,000 spent, Day 22 (10 days remaining: 22..31)
        val heroBudget = 31_000_00L
        val heroSpent = 18_000_00L
        val now = startAt + 21L * dayMillis + 1000L // Day 22

        val result = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = heroBudget,
            heroSpentPaise = heroSpent,
            periodStartAt = startAt,
            periodEndAt = endAt,
            nowIST = now,
            isClosed = false,
            todaySpentPaise = 200_00L
        )

        assertThat(result.isPeriodActive).isTrue()
        assertThat(result.elapsedDays).isEqualTo(22)
        assertThat(result.daysRemaining).isEqualTo(10) // 31 - 22 + 1 = 10
        assertThat(result.remainingBudgetPaise).isEqualTo(13_000_00L) // 31k - 18k = 13k
        // ₹13,000 / 10 days = ₹1,300 / day
        assertThat(result.safeDailyPaise).isEqualTo(1_300_00L)
        // Spent 200 today -> 1,100 left for today
        assertThat(result.todayRemainingPaise).isEqualTo(1_100_00L)
    }

    @Test
    fun testOverBudget_ReturnsZeroSafeSpend() {
        val heroBudget = 30_000_00L
        val heroSpent = 35_000_00L // Exceeded by 5k
        val now = startAt + 15L * dayMillis

        val result = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = heroBudget,
            heroSpentPaise = heroSpent,
            periodStartAt = startAt,
            periodEndAt = endAt,
            nowIST = now,
            isClosed = false
        )

        assertThat(result.isOverBudget).isTrue()
        assertThat(result.safeDailyPaise).isEqualTo(0L)
        assertThat(result.remainingBudgetPaise).isEqualTo(-5_000_00L)
        assertThat(result.todayRemainingPaise).isEqualTo(0L)
    }

    @Test
    fun testClosedPeriod_ReturnsZeroDaysRemainingAndZeroSafeSpend() {
        val heroBudget = 30_000_00L
        val heroSpent = 25_000_00L
        val now = startAt + 15L * dayMillis

        val result = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = heroBudget,
            heroSpentPaise = heroSpent,
            periodStartAt = startAt,
            periodEndAt = endAt,
            nowIST = now,
            isClosed = true
        )

        assertThat(result.isPeriodActive).isFalse()
        assertThat(result.daysRemaining).isEqualTo(0)
        assertThat(result.safeDailyPaise).isEqualTo(0L)
    }

    @Test
    fun testFuturePeriod_DistributesOverEntireMonth() {
        val heroBudget = 30_000_00L
        val heroSpent = 0L
        val now = startAt - 5L * dayMillis // 5 days before month starts

        val result = SafeDailySpendCalculator.calculate(
            heroBudgetPaise = heroBudget,
            heroSpentPaise = heroSpent,
            periodStartAt = startAt,
            periodEndAt = endAt,
            nowIST = now,
            isClosed = false
        )

        assertThat(result.isPeriodActive).isFalse()
        assertThat(result.elapsedDays).isEqualTo(0)
        assertThat(result.daysRemaining).isEqualTo(31)
        assertThat(result.safeDailyPaise).isEqualTo(30_000_00L / 31)
    }
}
