package com.tapledger.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale

/**
 * Representation and formatting utilities for monetary values in tapLedger.
 * All monetary amounts are strictly persisted and computed as Long paise.
 * Never use Double or Float for financial calculations.
 */
object MoneyFormatter {

    /**
     * Converts a rupees string or decimal string (e.g. "1234.56", "₹1,50,000.00") to paise.
     */
    fun toPaise(rupeesStr: String): Long {
        val sanitized = rupeesStr
            .replace("₹", "")
            .replace(",", "")
            .trim()
        if (sanitized.isEmpty()) return 0L
        val decimal = BigDecimal(sanitized)
        return decimal.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    /**
     * Converts rupees and paise directly to Long paise without floating point issues.
     */
    fun toPaise(rupees: Long, paise: Long = 0L): Long {
        return (rupees * 100L) + paise
    }

    /**
     * Formats Long paise to Indian Rupee string using Indian numbering format (Lakhs / Crores).
     * e.g., 15000000L paise -> "₹1,50,000.00"
     *        123456L paise  -> "₹1,234.56"
     */
    fun formatRupees(paise: Long, showSign: Boolean = false, includeSymbol: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = if (isNegative) -paise else paise
        val wholeRupees = absPaise / 100L
        val fractionPaise = absPaise % 100L

        val formattedWhole = formatIndianNumber(wholeRupees)
        val formattedFraction = String.format(Locale.ROOT, "%02d", fractionPaise)

        val prefix = when {
            isNegative -> "-"
            showSign && paise > 0 -> "+"
            else -> ""
        }
        val symbol = if (includeSymbol) "₹" else ""
        return "$prefix$symbol$formattedWhole.$formattedFraction"
    }

    /**
     * Formats whole rupee amount according to the Indian grouping system:
     * e.g. 150000 -> "1,50,000"
     *      10000000 -> "1,00,00,000"
     */
    private fun formatIndianNumber(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val last3 = s.substring(s.length - 3)
        val rest = s.substring(0, s.length - 3)
        val formattedRest = buildString {
            for (i in rest.indices) {
                if (i > 0 && (rest.length - i) % 2 == 0) {
                    append(',')
                }
                append(rest[i])
            }
        }
        return "$formattedRest,$last3"
    }
}
