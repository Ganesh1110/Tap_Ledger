package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun formatRupees_standardIndianGrouping() {
        // 123456L paise -> ₹1,234.56
        assertThat(MoneyFormatter.formatRupees(123456L)).isEqualTo("₹1,234.56")
        assertThat(MoneyFormatter.formatPaiseToRupees(123456L)).isEqualTo("₹1,234.56")

        // 15000000L paise -> ₹1,50,000.00
        assertThat(MoneyFormatter.formatRupees(15000000L)).isEqualTo("₹1,50,000.00")

        // 100000000000L paise -> ₹1,00,00,00,000.00 (100 Crores)
        assertThat(MoneyFormatter.formatRupees(100_00_00_000_00L)).isEqualTo("₹1,00,00,00,000.00")
    }

    @Test
    fun formatRupees_subRupeeAmounts() {
        // 50L paise -> ₹0.50
        assertThat(MoneyFormatter.formatRupees(50L)).isEqualTo("₹0.50")

        // 5L paise -> ₹0.05
        assertThat(MoneyFormatter.formatRupees(5L)).isEqualTo("₹0.05")

        // 0L paise -> ₹0.00
        assertThat(MoneyFormatter.formatRupees(0L)).isEqualTo("₹0.00")
    }

    @Test
    fun formatRupees_negativeAmounts() {
        // -123456L paise -> -₹1,234.56
        assertThat(MoneyFormatter.formatRupees(-123456L)).isEqualTo("-₹1,234.56")

        // -50L paise -> -₹0.50
        assertThat(MoneyFormatter.formatRupees(-50L)).isEqualTo("-₹0.50")
    }

    @Test
    fun formatRupees_showSignOption() {
        // Positive with showSign = true -> +₹1,234.56
        assertThat(MoneyFormatter.formatRupees(123456L, showSign = true)).isEqualTo("+₹1,234.56")

        // Zero with showSign = true -> ₹0.00 (no sign on zero)
        assertThat(MoneyFormatter.formatRupees(0L, showSign = true)).isEqualTo("₹0.00")

        // Negative with showSign = true -> -₹1,234.56
        assertThat(MoneyFormatter.formatRupees(-123456L, showSign = true)).isEqualTo("-₹1,234.56")
    }

    @Test
    fun formatRupees_withoutCurrencySymbol() {
        assertThat(MoneyFormatter.formatRupees(123456L, includeSymbol = false)).isEqualTo("1,234.56")
        assertThat(MoneyFormatter.formatRupees(-123456L, includeSymbol = false)).isEqualTo("-1,234.56")
        assertThat(MoneyFormatter.formatRupees(15000000L, includeSymbol = false)).isEqualTo("1,50,000.00")
    }

    @Test
    fun toPaise_rupeesAndPaiseHelper() {
        assertThat(MoneyFormatter.toPaise(rupees = 10L, paise = 50L)).isEqualTo(1050L)
        assertThat(MoneyFormatter.toPaise(rupees = 0L, paise = 75L)).isEqualTo(75L)
        assertThat(MoneyFormatter.toPaise(rupees = 500L, paise = 0L)).isEqualTo(50000L)
    }
}
