package com.tapledger.data.statement

import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Normalizes and parses Indian bank statement CSV files conforming to Flow.md §11.1.
 * Supports HDFC, ICICI, SBI, Axis, and generic bank CSV dialects with header auto-detection
 * and strictly zero-float paise conversion.
 */
object StatementCsvParser : StatementParser {

    override fun parse(
        content: String,
        importId: Long,
        createdAt: Long
    ): List<StatementLineEntity> = parseCsv(content, importId, createdAt)

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yyyy").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd MMM yyyy").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd-MMM-yy").toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("dd/MMM/yy").toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("dd-MM-yy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy")
    )

    data class ColumnMapping(
        val dateCol: Int,
        val valueDateCol: Int?,
        val descCol: Int,
        val refCol: Int?,
        val debitCol: Int?,
        val creditCol: Int?,
        val balanceCol: Int?,
        val typeCol: Int?,
        val amountCol: Int?
    )

    fun parseCsv(
        csvContent: String,
        importId: Long,
        createdAt: Long = DateTimeUtils.nowEpochMillis()
    ): List<StatementLineEntity> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // 1. Detect header row & dialect
        var headerIndex = -1
        var mapping: ColumnMapping? = null

        for (i in lines.indices) {
            val tokens = parseCsvLine(lines[i])
            val detected = detectColumnMapping(tokens)
            if (detected != null) {
                headerIndex = i
                mapping = detected
                break
            }
        }

        val parsedLines = mutableListOf<StatementLineEntity>()
        val startLine = if (headerIndex >= 0) headerIndex + 1 else 0

        for (i in startLine until lines.size) {
            val lineStr = lines[i]
            val tokens = parseCsvLine(lineStr)
            if (tokens.size < 2) continue

            val row = if (mapping != null) {
                extractWithMapping(tokens, mapping)
            } else {
                extractPositional(tokens)
            } ?: continue

            if (row.debitPaise == 0L && row.creditPaise == 0L) continue

            parsedLines.add(
                StatementLineEntity(
                    importId = importId,
                    transactionDate = row.txDate,
                    valueDate = row.valueDate ?: row.txDate,
                    description = row.desc,
                    reference = if (row.ref.isNullOrBlank()) null else row.ref,
                    debitPaise = row.debitPaise,
                    creditPaise = row.creditPaise,
                    balanceAfterPaise = row.balancePaise,
                    matchedTransactionId = null,
                    matchStatus = StatementMatchStatus.MISSING_IN_APP
                )
            )
        }

        return parsedLines
    }

    private fun detectColumnMapping(headers: List<String>): ColumnMapping? {
        val normalized = headers.map { normalizeHeader(it) }

        var dateCol = -1
        var valueDateCol: Int? = null
        var descCol = -1
        var refCol: Int? = null
        var debitCol: Int? = null
        var creditCol: Int? = null
        var balanceCol: Int? = null
        var typeCol: Int? = null
        var amountCol: Int? = null

        for (idx in normalized.indices) {
            val h = normalized[idx]
            when {
                h.contains("valuedate") || h.contains("valuedt") -> valueDateCol = idx
                h.contains("amount") -> amountCol = idx
                h.contains("withdrawal") || h == "dr" || h.contains("debit") -> debitCol = idx
                h.contains("deposit") || h == "cr" || h.contains("credit") -> creditCol = idx
                h.contains("balance") || h == "bal" -> balanceCol = idx
                h == "type" || h == "crdr" || h == "drcr" -> typeCol = idx
                h.contains("date") || h == "trandate" || h == "txndate" -> if (dateCol == -1 && valueDateCol != idx) dateCol = idx
                h.contains("narration") || h.contains("desc") || h.contains("particular") || h.contains("remark") || h.contains("detail") -> if (descCol == -1) descCol = idx
                h.contains("ref") || h.contains("chq") || h.contains("cheque") || h.contains("utr") -> if (refCol == null) refCol = idx
            }
        }

        // Must have at least a date and description, plus amount or debit/credit column
        if (dateCol != -1 && descCol != -1 && ((debitCol != null || creditCol != null) || (amountCol != null))) {
            return ColumnMapping(
                dateCol = dateCol,
                valueDateCol = valueDateCol,
                descCol = descCol,
                refCol = refCol,
                debitCol = debitCol,
                creditCol = creditCol,
                balanceCol = balanceCol,
                typeCol = typeCol,
                amountCol = amountCol
            )
        }
        return null
    }

    private fun normalizeHeader(h: String): String {
        return h.lowercase(Locale.ENGLISH)
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private data class ExtractedRow(
        val txDate: Long,
        val valueDate: Long?,
        val desc: String,
        val ref: String?,
        val debitPaise: Long,
        val creditPaise: Long,
        val balancePaise: Long?
    )

    private fun extractWithMapping(tokens: List<String>, mapping: ColumnMapping): ExtractedRow? {
        val dateStr = tokens.getOrNull(mapping.dateCol) ?: return null
        val txDate = parseDate(dateStr) ?: return null

        val valDateStr = mapping.valueDateCol?.let { tokens.getOrNull(it) }
        val valueDate = valDateStr?.let { parseDate(it) }

        val desc = tokens.getOrNull(mapping.descCol)?.trim().orEmpty()
        val ref = mapping.refCol?.let { tokens.getOrNull(it)?.trim() }

        var debit = 0L
        var credit = 0L

        if (mapping.typeCol != null && mapping.amountCol != null) {
            val typeStr = tokens.getOrNull(mapping.typeCol)?.trim().orEmpty().uppercase(Locale.ENGLISH)
            val amt = parseAmount(tokens.getOrNull(mapping.amountCol))
            if (typeStr.contains("CR") || typeStr.contains("CREDIT")) {
                credit = amt
            } else if (typeStr.contains("DR") || typeStr.contains("DEBIT")) {
                debit = amt
            } else {
                if (amt < 0L) debit = -amt else credit = amt
            }
        } else {
            debit = mapping.debitCol?.let { parseAmount(tokens.getOrNull(it)) } ?: 0L
            credit = mapping.creditCol?.let { parseAmount(tokens.getOrNull(it)) } ?: 0L
        }

        val balance = mapping.balanceCol?.let { parseAmountOrNull(tokens.getOrNull(it)) }

        return ExtractedRow(
            txDate = txDate,
            valueDate = valueDate,
            desc = desc,
            ref = ref,
            debitPaise = debit,
            creditPaise = credit,
            balancePaise = balance
        )
    }

    private fun extractPositional(tokens: List<String>): ExtractedRow? {
        val dateStr = tokens.getOrNull(0) ?: return null
        val txDate = parseDate(dateStr) ?: return null
        val desc = tokens.getOrNull(1)?.trim().orEmpty()

        var ref: String? = null
        var debit = 0L
        var credit = 0L
        var balance: Long? = null

        if (tokens.size >= 6) {
            ref = tokens.getOrNull(2)?.trim()
            debit = parseAmount(tokens.getOrNull(3))
            credit = parseAmount(tokens.getOrNull(4))
            balance = parseAmountOrNull(tokens.getOrNull(5))
        } else if (tokens.size >= 4) {
            debit = parseAmount(tokens.getOrNull(2))
            credit = parseAmount(tokens.getOrNull(3))
        } else if (tokens.size >= 3) {
            val amt = parseAmount(tokens.getOrNull(2))
            if (amt < 0L) debit = -amt else credit = amt
        }

        return ExtractedRow(
            txDate = txDate,
            valueDate = txDate,
            desc = desc,
            ref = ref,
            debitPaise = debit,
            creditPaise = credit,
            balancePaise = balance
        )
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    fun parseDate(dateStr: String): Long? {
        val cleanStr = dateStr.replace("\"", "").trim()
        if (cleanStr.isBlank()) return null
        for (formatter in DATE_FORMATTERS) {
            try {
                val localDate = LocalDate.parse(cleanStr, formatter)
                return localDate.atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
            } catch (e: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    fun parseAmount(amountStr: String?): Long {
        if (amountStr.isNullOrBlank()) return 0L
        val clean = cleanAmountString(amountStr)
        if (clean.isEmpty() || clean == "-" || clean == "0" || clean == "0.00" || clean.equals("nil", ignoreCase = true)) {
            return 0L
        }
        return try {
            val isNegative = clean.startsWith("-") || (clean.startsWith("(") && clean.endsWith(")"))
            val stripped = clean.removePrefix("-").removePrefix("(").removeSuffix(")")
            val paise = MoneyFormatter.toPaise(stripped)
            if (isNegative) -paise else paise
        } catch (e: Exception) {
            0L
        }
    }

    fun parseAmountOrNull(amountStr: String?): Long? {
        if (amountStr.isNullOrBlank()) return null
        val clean = cleanAmountString(amountStr)
        if (clean.isEmpty() || clean == "-" || clean.equals("nil", ignoreCase = true)) {
            return null
        }
        return try {
            val isNegative = clean.startsWith("-") || (clean.startsWith("(") && clean.endsWith(")"))
            val stripped = clean.removePrefix("-").removePrefix("(").removeSuffix(")")
            val paise = MoneyFormatter.toPaise(stripped)
            if (isNegative) -paise else paise
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanAmountString(str: String): String {
        return str.replace("\"", "")
            .replace(",", "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace("CR", "", ignoreCase = true)
            .replace("DR", "", ignoreCase = true)
            .replace("/-", "")
            .trim()
    }
}
