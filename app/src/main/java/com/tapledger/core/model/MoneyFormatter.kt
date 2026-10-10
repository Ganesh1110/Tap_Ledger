package com.tapledger.core.model

import java.util.Locale

/**
 * Representation and formatting utilities for monetary values in TapLedger.
 * All monetary amounts are strictly persisted and computed as signed Long paise.
 * Zero floating-point arithmetic (no Float, Double, or BigDecimal).
 */
object MoneyFormatter {

    /**
     * Max sanity cap on monetary amounts: ₹100 Crores = 10,000,000,000,00 paise (Flow.md §1.2).
     */
    const val MAX_SANITY_PAISE: Long = 100_00_00_000_00L

    /**
     * Deterministic string-based paise parser strictly conforming to Flow.md §1.2.
     * Rejects empty amounts, multiple decimal points, sub-paise precision (>2 decimals),
     * non-ASCII digits, negative amounts, 0.00 (unless allowZero = true), and amounts exceeding ₹100 Cr.
     */
    fun parseRupeesStringToPaise(raw: String, allowZero: Boolean = false): Long {
        // 1. Strip trailing sentence punctuation, Indian '/-' suffix (e.g. "Rs. 500/-."), symbols, and whitespace
        var cleaned = raw.trim().trimEnd('.').replace(Regex("/-\\s*$"), "").trimEnd('.')
        cleaned = cleaned.replace(Regex("[₹,\\s]|Rs\\.?|INR", RegexOption.IGNORE_CASE), "").trim()
        if (cleaned.isEmpty()) throw IllegalArgumentException("Empty amount: $raw")

        // 2. Tokenize on decimal point
        val parts = cleaned.split(".")
        if (parts.size > 2) throw IllegalArgumentException("Multiple decimal points: $raw")

        // 3. Whole part parsing (ASCII digits only; support empty leading whole ".50" -> 0)
        val wholeStr = if (parts[0].isEmpty()) "0" else parts[0]
        if (!wholeStr.all { it in '0'..'9' }) throw IllegalArgumentException("Non-ASCII/digit whole part: $raw")
        if (wholeStr.length > 12) throw IllegalArgumentException("Amount exceeds sanity cap (₹100 Cr): $raw")
        val wholePart = wholeStr.toLong()

        // 4. Fraction part parsing (ASCII digits only, max 2 decimals)
        val fractionStr = if (parts.size == 2) parts[1] else ""
        if (fractionStr.isNotEmpty() && !fractionStr.all { it in '0'..'9' }) {
            throw IllegalArgumentException("Non-ASCII/digit fraction part: $raw")
        }
        if (fractionStr.length > 2) {
            throw IllegalArgumentException("Sub-paise precision rejected (>2 decimals): $raw")
        }

        // Right-pad single digit (e.g., ".5" -> "50") or use "00"
        val paddedFraction = fractionStr.padEnd(2, '0').toLong()

        // Guard against overflow before multiplication
        if (wholePart > 100_00_00_000L || (wholePart == 100_00_00_000L && paddedFraction > 0L)) {
            throw IllegalArgumentException("Amount exceeds sanity cap (₹100 Cr): $raw")
        }

        val totalPaise = Math.addExact(Math.multiplyExact(wholePart, 100L), paddedFraction)

        // 5. Positive amount & sanity cap enforcement (Max ₹100 Crores)
        if (totalPaise < 0L || (!allowZero && totalPaise == 0L)) {
            throw IllegalArgumentException("Amount must be strictly positive: $raw")
        }
        if (totalPaise > MAX_SANITY_PAISE) {
            throw IllegalArgumentException("Amount exceeds sanity cap (₹100 Cr): $raw")
        }

        return totalPaise
    }

    /**
     * Converts a rupees string or decimal string (e.g. "1234.56", "₹1,50,000.00") to paise.
     */
    fun toPaise(rupeesStr: String, allowZero: Boolean = false): Long {
        val trimmed = rupeesStr.trim()
        if (trimmed.isEmpty() && allowZero) return 0L
        return parseRupeesStringToPaise(trimmed, allowZero = allowZero)
    }

    /**
     * Converts rupees and paise directly to Long paise without floating point issues.
     */
    fun toPaise(rupees: Long, paise: Long = 0L): Long {
        require(rupees >= 0L && paise in 0L..99L) { "Invalid rupees ($rupees) or paise ($paise)" }
        return Math.addExact(Math.multiplyExact(rupees, 100L), paise)
    }

    /**
     * Formats Long paise to Indian Rupee string using Indian numbering format (Lakhs / Crores).
     * e.g., 15000000L paise -> "₹1,50,000.00"
     *        123456L paise  -> "₹1,234.56"
     */
    fun formatRupees(paise: Long, showSign: Boolean = false, includeSymbol: Boolean = true): String {
        val isNegative = paise < 0L
        val absPaise = if (isNegative) -paise else paise
        val wholeRupees = absPaise / 100L
        val fractionPaise = absPaise % 100L

        val formattedWhole = formatIndianNumber(wholeRupees)
        val formattedFraction = String.format(Locale.ROOT, "%02d", fractionPaise)

        val prefix = when {
            isNegative -> "-"
            showSign && paise > 0L -> "+"
            else -> ""
        }
        val symbol = if (includeSymbol) "₹" else ""
        return "$prefix$symbol$formattedWhole.$formattedFraction"
    }

    /**
     * Alias conforming to Flow.md §1.3 naming convention.
     */
    fun formatPaiseToRupees(paise: Long, showSign: Boolean = false, includeSymbol: Boolean = true): String {
        return formatRupees(paise, showSign = showSign, includeSymbol = includeSymbol)
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
