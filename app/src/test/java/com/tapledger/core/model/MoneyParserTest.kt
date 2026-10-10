package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyParserTest {

    @Test
    fun parseRupeesStringToPaise_binaryFloatTrap_1_15_returnsExact115Paise() {
        // §1.1 & §1.2: 1.15 in binary floating point is 114.99999999999999.
        // Pure string parsing must yield exactly 115 paise.
        val paise = MoneyFormatter.parseRupeesStringToPaise("1.15")
        assertThat(paise).isEqualTo(115L)
    }

    @Test
    fun parseRupeesStringToPaise_leadingDecimal_returnsPaddedPaise() {
        // ".5" -> 50 paise, ".50" -> 50 paise
        assertThat(MoneyFormatter.parseRupeesStringToPaise(".5")).isEqualTo(50L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise(".50")).isEqualTo(50L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise(".05")).isEqualTo(5L)
    }

    @Test
    fun parseRupeesStringToPaise_indianSuffixAndPunctuation_strippedCorrectly() {
        // "Rs. 500/-." -> 50000 paise
        assertThat(MoneyFormatter.parseRupeesStringToPaise("Rs. 500/-.")).isEqualTo(50000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("Rs. 500/-")).isEqualTo(50000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("500/-")).isEqualTo(50000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("500 /-")).isEqualTo(50000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("Rs.500.00.")).isEqualTo(50000L)
    }

    @Test
    fun parseRupeesStringToPaise_currencySymbolsAndCommas_parsedCorrectly() {
        // "INR 1,23,456.78" -> 12345678 paise
        assertThat(MoneyFormatter.parseRupeesStringToPaise("INR 1,23,456.78")).isEqualTo(12345678L)
        // "₹1,50,000.00" -> 15000000 paise
        assertThat(MoneyFormatter.parseRupeesStringToPaise("₹1,50,000.00")).isEqualTo(15000000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("rs. 10")).isEqualTo(1000L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("Rs 10.5")).isEqualTo(1050L)
    }

    @Test
    fun parseRupeesStringToPaise_100CroreSanityCap_exactBoundaryPasses() {
        // ₹100 Crores = 100_00_00_000 Rupees = 100_00_00_000_00 paise (10 trillion / 100 billion paise)
        val capRupees = "1000000000.00"
        val expectedPaise = MoneyFormatter.MAX_SANITY_PAISE
        assertThat(MoneyFormatter.parseRupeesStringToPaise(capRupees)).isEqualTo(expectedPaise)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("1000000000")).isEqualTo(expectedPaise)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("₹1,00,00,00,000.00")).isEqualTo(expectedPaise)
    }

    @Test
    fun parseRupeesStringToPaise_100CroreSanityCap_exceededThrowsException() {
        // ₹100 Crores + 1 paisa
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("1000000000.01")
        }
        // ₹100 Crores + 1 rupee
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("1000000001")
        }
        // Massive overflow attempt
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("999999999999999999")
        }
    }

    @Test
    fun parseRupeesStringToPaise_zeroAmount_rejectedByDefault() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("0.00")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("0")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise(".00")
        }

        // When allowZero = true, returns 0L
        assertThat(MoneyFormatter.parseRupeesStringToPaise("0.00", allowZero = true)).isEqualTo(0L)
        assertThat(MoneyFormatter.parseRupeesStringToPaise("0", allowZero = true)).isEqualTo(0L)
    }

    @Test
    fun parseRupeesStringToPaise_subPaisePrecision_rejected() {
        // > 2 decimals rejected
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("10.125")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("1.001")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise(".555")
        }
    }

    @Test
    fun parseRupeesStringToPaise_negativeAmounts_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("-500")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("-0.50")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("₹-100.00")
        }
    }

    @Test
    fun parseRupeesStringToPaise_devanagariDigits_rejected() {
        // Non-ASCII digits must be strictly rejected (§1.2)
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("५००")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("₹५००.००")
        }
    }

    @Test
    fun parseRupeesStringToPaise_invalidTokensAndMultipleDecimals_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("1.2.3")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("abc")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("12a.50")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseRupeesStringToPaise("₹")
        }
    }
}
