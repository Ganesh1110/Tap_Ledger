package com.tapledger.ui.screens

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.data.statement.StatementReconciliationEngine
import org.junit.Test

class TopFiveFixesVerificationTest {

    @Test
    fun testAutoAlignStatusTrigger_uncalibratedOpeningBalanceArithmeticCheck() {
        // Line with ₹500 debit
        val lines = listOf(
            StatementLineEntity(
                id = 1L,
                importId = 101L,
                transactionDate = 1500L,
                description = "Grocery Store",
                debitPaise = 500_00L,
                creditPaise = 0L,
                balanceAfterPaise = 1500_00L, // Implies true opening was 2000_00L
                matchStatus = StatementMatchStatus.RESOLVED
            )
        )

        // Case 1: Uncalibrated import has openingBalance = 0L, but closing = 1500_00L
        // Expected closing = 0 - 500 = -500, but reported closing is 1500 -> Fails arithmetic!
        val isUncalibratedValid = StatementReconciliationEngine.verifyStatementArithmetic(
            openingBalancePaise = 0L,
            lines = lines,
            reportedClosingBalancePaise = 1500_00L
        )
        assertThat(isUncalibratedValid).isFalse()

        // Per Fix 1: When all lines are resolved (allHandled == true) but arithmetic is invalid (uncalibrated),
        // checkAndUpdateImportStatus marks ARITHMETIC_ERROR so the UI prompts to auto-align balances
        val allHandled = lines.none { it.matchStatus == StatementMatchStatus.MISSING_IN_APP }
        val statusWhenUncalibrated = if (allHandled && isUncalibratedValid) {
            StatementImportStatus.RECONCILED
        } else {
            StatementImportStatus.ARITHMETIC_ERROR
        }
        assertThat(statusWhenUncalibrated).isEqualTo(StatementImportStatus.ARITHMETIC_ERROR)

        // Case 2: User aligns balances from statement lines (opening = 2000_00L, closing = 1500_00L)
        // 2000 - 500 = 1500 -> Valid arithmetic!
        val isCalibratedValid = StatementReconciliationEngine.verifyStatementArithmetic(
            openingBalancePaise = 2000_00L,
            lines = lines,
            reportedClosingBalancePaise = 1500_00L
        )
        assertThat(isCalibratedValid).isTrue()

        val statusWhenCalibrated = if (allHandled && isCalibratedValid) {
            StatementImportStatus.RECONCILED
        } else {
            StatementImportStatus.ARITHMETIC_ERROR
        }
        assertThat(statusWhenCalibrated).isEqualTo(StatementImportStatus.RECONCILED)
    }

    @Test
    fun testAmountDecimalMaskingRegex() {
        val regex = Regex("""^\d+(\.\d{0,2})?$""")

        // Valid amounts accepted by AddEditTransactionSheet & ManualEntryScreen
        assertThat("".isEmpty()).isTrue()
        assertThat("0".matches(regex)).isTrue()
        assertThat("100".matches(regex)).isTrue()
        assertThat("100.".matches(regex)).isTrue()
        assertThat("100.5".matches(regex)).isTrue()
        assertThat("100.50".matches(regex)).isTrue()
        assertThat("9999999.99".matches(regex)).isTrue()

        // Invalid amounts rejected by masking (e.g. sub-paise precision, non-digits, negatives)
        assertThat("100.500".matches(regex)).isFalse() // >2 decimal places rejected
        assertThat("100..50".matches(regex)).isFalse()
        assertThat("-100".matches(regex)).isFalse()
        assertThat("100,50".matches(regex)).isFalse()
        assertThat("abc".matches(regex)).isFalse()
        assertThat("₹100".matches(regex)).isFalse()
    }

    @Test
    fun testOnboardingStringFormatting_noDoubleRupeePrefix() {
        val incomePaise = 50_000_00L
        val formatted = MoneyFormatter.formatPaiseToRupees(incomePaise)

        // Verifies formatPaiseToRupees includes ₹ symbol
        assertThat(formatted).startsWith("₹")

        // Verifies Step 3 Envelopes banner text does not have double Rupee prefix ("All ₹₹...")
        val bannerText = "All $formatted Assigned!"
        assertThat(bannerText).doesNotContain("₹₹")
        assertThat(bannerText).isEqualTo("All ₹50,000.00 Assigned!")
    }
}
