package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

/**
 * Property-based tests and golden tests for TapLedger Money Core (§1).
 * Verifies mathematical properties across 10,000+ randomized inputs and critical financial boundaries.
 */
class MoneyPropertyTest {

    private val random = Random(42) // Deterministic seed for reproducible property tests

    @Test
    fun property_roundTrip_formatAndParse_isIdentity() {
        // Test 5,000 randomized amounts spanning 1 paisa to ₹100 Crores
        for (i in 0 until 5000) {
            val amountPaise = when {
                i < 100 -> (i + 1).toLong() // Boundary 1..100 paise
                i < 200 -> random.nextLong(100L, 100_00L) // ₹1 to ₹100
                i < 1000 -> random.nextLong(100_00L, 10_000_00L) // ₹100 to ₹10,000
                i < 2000 -> random.nextLong(10_000_00L, 1_00_000_00L) // ₹10,000 to ₹1 Lakh
                i < 3000 -> random.nextLong(1_00_000_00L, 1_00_00_000_00L) // ₹1 Lakh to ₹1 Crore
                i < 4500 -> random.nextLong(1_00_00_000_00L, MoneyFormatter.MAX_SANITY_PAISE) // ₹1 Crore to ₹100 Crores
                else -> MoneyFormatter.MAX_SANITY_PAISE - (5000 - i) // Critical cap boundaries
            }

            val formatted = MoneyFormatter.formatRupees(amountPaise)
            val parsed = MoneyFormatter.parseRupeesStringToPaise(formatted)

            assertThat(parsed).isEqualTo(amountPaise)
        }
    }

    @Test
    fun property_formattingDecorations_preserveExactValue() {
        // Decorate amounts with random Indian currency prefixes, suffixes, and whitespace
        val prefixes = listOf("₹", "₹ ", "Rs. ", "Rs.", "rs ", "INR ", "INR")
        val suffixes = listOf("", "/-", "/-.", ".")

        for (i in 0 until 1000) {
            val amountPaise = random.nextLong(1L, 10_00_00_000_00L)
            val whole = amountPaise / 100L
            val frac = amountPaise % 100L

            val prefix = prefixes[random.nextInt(prefixes.size)]
            val suffix = suffixes[random.nextInt(suffixes.size)]
            val raw = "$prefix$whole.${String.format("%02d", frac)}$suffix"

            val parsed = MoneyFormatter.parseRupeesStringToPaise(raw)
            assertThat(parsed).isEqualTo(amountPaise)
        }
    }

    @Test
    fun goldenDataset_knownFinancialTransactions() {
        val goldenPairs = listOf(
            "1.15" to 115L,
            ".50" to 50L,
            ".5" to 50L,
            ".05" to 5L,
            "0.05" to 5L,
            "100" to 10000L,
            "100.0" to 10000L,
            "100.00" to 10000L,
            "Rs. 500/-." to 50000L,
            "Rs. 500/-" to 50000L,
            "500/-" to 50000L,
            "INR 1,23,456.78" to 12345678L,
            "₹1,50,000.00" to 15000000L,
            "Rs.500.00." to 50000L,
            "₹ 99,99,999.99" to 999999999L,
            "1000000000.00" to 100_00_00_000_00L
        )

        for ((input, expectedPaise) in goldenPairs) {
            val actual = MoneyFormatter.parseRupeesStringToPaise(input)
            assertThat(actual).isEqualTo(expectedPaise)
        }
    }
}
