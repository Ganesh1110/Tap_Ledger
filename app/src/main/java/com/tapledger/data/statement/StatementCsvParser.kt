package com.tapledger.data.statement

import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object StatementCsvParser {

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
        DateTimeFormatter.ofPattern("dd-MMM-yy")
    )

    fun parseCsv(
        csvContent: String,
        importId: Long,
        createdAt: Long = DateTimeUtils.nowEpochMillis()
    ): List<StatementLineEntity> {
        val lines = csvContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        val parsedLines = mutableListOf<StatementLineEntity>()
        var lineNumber = 1

        for (lineStr in lines) {
            // Ignore header row if it contains text like "Date" or "Description"
            if (lineStr.contains("Date", ignoreCase = true) && lineStr.contains("Description", ignoreCase = true)) {
                continue
            }

            val tokens = parseCsvLine(lineStr)
            if (tokens.size < 3) continue

            val txDate = parseDate(tokens[0]) ?: continue
            val desc = tokens.getOrNull(1)?.trim().orEmpty()

            var ref: String? = null
            var debitPaise = 0L
            var creditPaise = 0L
            var balancePaise: Long? = null

            if (tokens.size >= 6) {
                ref = tokens.getOrNull(2)?.trim()
                debitPaise = parseAmount(tokens.getOrNull(3))
                creditPaise = parseAmount(tokens.getOrNull(4))
                balancePaise = parseAmountOrNull(tokens.getOrNull(5))
            } else if (tokens.size >= 4) {
                debitPaise = parseAmount(tokens.getOrNull(2))
                creditPaise = parseAmount(tokens.getOrNull(3))
            }

            if (debitPaise == 0L && creditPaise == 0L) continue

            parsedLines.add(
                StatementLineEntity(
                    statementImportId = importId,
                    lineNumber = lineNumber++,
                    transactionDate = txDate,
                    valueDate = txDate,
                    description = desc,
                    reference = if (ref.isNullOrBlank()) null else ref,
                    debitPaise = debitPaise,
                    creditPaise = creditPaise,
                    balancePaise = balancePaise,
                    matchedTransactionId = null,
                    matchStatus = StatementMatchStatus.MISSING_IN_APP,
                    createdAt = createdAt
                )
            )
        }

        return parsedLines
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

    private fun parseDate(dateStr: String): Long? {
        val cleanStr = dateStr.replace("\"", "").trim()
        for (formatter in DATE_FORMATTERS) {
            try {
                val localDate = LocalDate.parse(cleanStr, formatter)
                return localDate.atStartOfDay(DateTimeUtils.IST_ZONE).toInstant().toEpochMilli()
            } catch (e: Exception) {
                // Try next
            }
        }
        return null
    }

    private fun parseAmount(amountStr: String?): Long {
        if (amountStr.isNullOrBlank()) return 0L
        val clean = amountStr.replace("\"", "").replace(",", "").trim()
        return try {
            MoneyFormatter.toPaise(clean)
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseAmountOrNull(amountStr: String?): Long? {
        if (amountStr.isNullOrBlank()) return null
        val clean = amountStr.replace("\"", "").replace(",", "").trim()
        return try {
            MoneyFormatter.toPaise(clean)
        } catch (e: Exception) {
            null
        }
    }
}
