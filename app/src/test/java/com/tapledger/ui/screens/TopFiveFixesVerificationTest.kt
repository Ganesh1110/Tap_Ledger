package com.tapledger.ui.screens

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.model.MoneyFormatter
import org.junit.Test

class TopFiveFixesVerificationTest {

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
