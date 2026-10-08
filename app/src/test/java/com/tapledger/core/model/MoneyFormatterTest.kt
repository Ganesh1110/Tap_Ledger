package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun toPaise_convertsStandardRupeesString() {
        assertThat(MoneyFormatter.toPaise("100")).isEqualTo(10000L)
        assertThat(MoneyFormatter.toPaise("1234.56")).isEqualTo(123456L)
        assertThat(MoneyFormatter.toPaise("₹1,234.56")).isEqualTo(123456L)
    }

    @Test
    fun toPaise_convertsLakhFormat() {
        assertThat(MoneyFormatter.toPaise("₹1,50,000.00")).isEqualTo(15000000L)
        assertThat(MoneyFormatter.toPaise("₹1,00,00,000.00")).isEqualTo(1000000000L) // 1 Crore
    }

    @Test
    fun toPaise_withRupeesAndPaiseArgs() {
        assertThat(MoneyFormatter.toPaise(500L, 50L)).isEqualTo(50050L)
        assertThat(MoneyFormatter.toPaise(0L, 99L)).isEqualTo(99L)
    }

    @Test
    fun formatRupees_formatsLakhsCorrectly() {
        assertThat(MoneyFormatter.formatRupees(15000000L)).isEqualTo("₹1,50,000.00")
        assertThat(MoneyFormatter.formatRupees(123456L)).isEqualTo("₹1,234.56")
        assertThat(MoneyFormatter.formatRupees(50L)).isEqualTo("₹0.50")
    }

    @Test
    fun formatRupees_negativeAmounts_representsLiability() {
        assertThat(MoneyFormatter.formatRupees(-800000L)).isEqualTo("-₹8,000.00")
    }
}
