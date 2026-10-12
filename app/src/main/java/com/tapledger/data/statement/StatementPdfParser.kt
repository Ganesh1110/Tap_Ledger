package com.tapledger.data.statement

import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.util.DateTimeUtils
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.util.Locale
import java.util.regex.Pattern

class PdfPasswordRequiredException(
    message: String = "This PDF statement is password-protected. Please enter the statement password."
) : Exception(message)

data class PdfStatementResult(
    val lines: List<StatementLineEntity>,
    val detectedOpeningBalancePaise: Long?,
    val detectedClosingBalancePaise: Long?,
    val detectedPeriodStart: Long?,
    val detectedPeriodEnd: Long?,
    val rawText: String
)

/**
 * Robust on-device parser for Indian bank PDF statements (HDFC, SBI, ICICI, Axis, etc.)
 * conforming to IMPLEMENTATION_PLAN.md §24.
 *
 * Runs 100% offline without network access.
 * Supports password-protected PDF statements (passwords are used strictly for decryption
 * and never persisted).
 */
object StatementPdfParser {

    private val DATE_PREFIX_REGEX = Pattern.compile(
        "^(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[-\\s][A-Za-z]{3}[-\\s]\\d{2,4})\\b"
    )

    private val AMOUNT_PATTERN = Pattern.compile(
        "[-+]?(?:₹|Rs\\.?|INR\\s*)?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?(?:\\s*(?:[Cc][Rr]|[Dd][Rr]))?\\b"
    )

    private val OPENING_BALANCE_REGEX = Pattern.compile(
        "(?i)(?:opening\\s*balance|balance\\s*b/?f|brought\\s*forward|open\\s*bal)[\\s:=-]*([₹RsINR\\s]*\\d[\\d,.]*)"
    )

    private val CLOSING_BALANCE_REGEX = Pattern.compile(
        "(?i)(?:closing\\s*balance|balance\\s*c/?f|carried\\s*forward|close\\s*bal|ending\\s*balance)[\\s:=-]*([₹RsINR\\s]*\\d[\\d,.]*)"
    )

    /**
     * Extracts text and parses statement lines from a PDF input stream.
     */
    fun parsePdf(
        inputStream: InputStream,
        password: String? = null,
        importId: Long = 0L,
        createdAt: Long = DateTimeUtils.nowEpochMillis()
    ): PdfStatementResult {
        val document: PDDocument = try {
            if (password.isNullOrEmpty()) {
                PDDocument.load(inputStream)
            } else {
                PDDocument.load(inputStream, password)
            }
        } catch (e: InvalidPasswordException) {
            throw PdfPasswordRequiredException()
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.cause?.message?.contains("password", ignoreCase = true) == true
            ) {
                throw PdfPasswordRequiredException()
            }
            throw e
        }

        try {
            if (document.isEncrypted && !document.currentAccessPermission.canExtractContent()) {
                throw PdfPasswordRequiredException("Cannot extract content from this encrypted PDF. Check your password.")
            }

            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            val rawText = stripper.getText(document)

            val detectedOpening = detectOpeningBalance(rawText)
            val detectedClosing = detectClosingBalance(rawText)

            val lines = parseTextToLines(rawText, importId)

            val minDate = lines.minOfOrNull { it.transactionDate }
            val maxDate = lines.maxOfOrNull { it.transactionDate }

            return PdfStatementResult(
                lines = lines,
                detectedOpeningBalancePaise = detectedOpening,
                detectedClosingBalancePaise = detectedClosing,
                detectedPeriodStart = minDate,
                detectedPeriodEnd = maxDate,
                rawText = rawText
            )
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }

    internal fun detectOpeningBalance(text: String): Long? {
        val matcher = OPENING_BALANCE_REGEX.matcher(text)
        if (matcher.find()) {
            val amtStr = matcher.group(1)
            val parsed = StatementCsvParser.parseAmountOrNull(amtStr)
            if (parsed != null && parsed != 0L) return parsed
        }
        return null
    }

    internal fun detectClosingBalance(text: String): Long? {
        val matcher = CLOSING_BALANCE_REGEX.matcher(text)
        if (matcher.find()) {
            val amtStr = matcher.group(1)
            val parsed = StatementCsvParser.parseAmountOrNull(amtStr)
            if (parsed != null && parsed != 0L) return parsed
        }
        return null
    }

    /**
     * Parses the stripped PDF text lines into [StatementLineEntity] objects.
     */
    fun parseTextToLines(
        rawText: String,
        importId: Long
    ): List<StatementLineEntity> {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<StatementLineEntity>()
        var currentEntity: StatementLineEntity? = null

        for (line in rawLines) {
            val parsedLine = tryParseTransactionLine(line, importId)
            if (parsedLine != null) {
                if (currentEntity != null) {
                    result.add(currentEntity)
                }
                currentEntity = parsedLine
            } else if (currentEntity != null) {
                // Continuation line for narration / description
                if (!isHeaderOrNoiseLine(line)) {
                    currentEntity = currentEntity.copy(
                        description = "${currentEntity.description} $line".trim()
                    )
                }
            }
        }

        if (currentEntity != null) {
            result.add(currentEntity)
        }

        return result
    }

    private fun tryParseTransactionLine(line: String, importId: Long): StatementLineEntity? {
        val dateMatcher = DATE_PREFIX_REGEX.matcher(line)
        if (!dateMatcher.find()) return null

        val dateStr = dateMatcher.group(1) ?: return null
        val txDate = StatementCsvParser.parseDate(dateStr) ?: return null

        // Remainder after date
        val remainder = line.substring(dateMatcher.end()).trim()
        if (remainder.isEmpty()) return null

        // 1. Try splitting by multiple spaces / tabs (tabular format)
        val tokens = remainder.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val extracted = extractFromTokens(txDate, tokens, importId)
            if (extracted != null) return extracted
        }

        // 2. Fallback: extract amount matches from end of remainder
        return extractFromAmountsAtEnd(txDate, remainder, importId)
    }

    private fun extractFromTokens(
        txDate: Long,
        tokens: List<String>,
        importId: Long
    ): StatementLineEntity? {
        val desc = tokens[0]
        val otherTokens = tokens.subList(1, tokens.size)

        // Parse numerical amounts in the remaining tokens
        val amounts = otherTokens.mapNotNull { StatementCsvParser.parseAmountOrNull(it) }
        if (amounts.isEmpty()) return null

        var debit = 0L
        var credit = 0L
        var balance: Long? = null
        var ref: String? = null

        // Check if there is an alphanumeric reference token
        val nonAmountTokens = otherTokens.filter { StatementCsvParser.parseAmountOrNull(it) == null }
        if (nonAmountTokens.isNotEmpty()) {
            ref = nonAmountTokens.firstOrNull { it.matches(Regex("[A-Za-z0-9/-]{4,}")) }
        }

        when (amounts.size) {
            3 -> {
                debit = amounts[0]
                credit = amounts[1]
                balance = amounts[2]
            }
            2 -> {
                // In bank statement lines with 2 amounts, amounts[1] is the running balance
                val isCredit = otherTokens.any { it.contains("CR", ignoreCase = true) } ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep)\\b"))
                val isDebit = otherTokens.any { it.contains("DR", ignoreCase = true) } ||
                        desc.contains(Regex("(?i)\\b(debit|dr|upi|pos|atm|wdl|withdrawal|paid|transfer to)\\b"))

                if (isCredit && !isDebit) {
                    credit = amounts[0]
                    balance = amounts[1]
                } else {
                    debit = amounts[0]
                    balance = amounts[1]
                }
            }
            1 -> {
                val isCredit = otherTokens.any { it.contains("CR", ignoreCase = true) }
                val isDebit = otherTokens.any { it.contains("DR", ignoreCase = true) }
                if (isCredit) {
                    credit = amounts[0]
                } else if (isDebit) {
                    debit = amounts[0]
                } else {
                    if (amounts[0] < 0L) debit = -amounts[0] else credit = amounts[0]
                }
            }
            else -> {
                if (amounts.size >= 4) {
                    debit = amounts[amounts.size - 3]
                    credit = amounts[amounts.size - 2]
                    balance = amounts[amounts.size - 1]
                }
            }
        }

        if (debit == 0L && credit == 0L) return null

        return StatementLineEntity(
            importId = importId,
            transactionDate = txDate,
            valueDate = txDate,
            description = desc,
            reference = ref,
            debitPaise = debit,
            creditPaise = credit,
            balanceAfterPaise = balance,
            matchedTransactionId = null,
            matchStatus = StatementMatchStatus.MISSING_IN_APP
        )
    }

    private fun extractFromAmountsAtEnd(
        txDate: Long,
        remainder: String,
        importId: Long
    ): StatementLineEntity? {
        val matches = mutableListOf<String>()
        val matcher = AMOUNT_PATTERN.matcher(remainder)
        while (matcher.find()) {
            matches.add(matcher.group())
        }

        if (matches.isEmpty()) return null

        val amounts = matches.mapNotNull { StatementCsvParser.parseAmountOrNull(it) }
        if (amounts.isEmpty()) return null

        // Find the index of the first amount token in remainder to separate description
        val firstAmountStr = matches.first()
        val firstIdx = remainder.indexOf(firstAmountStr)
        val desc = if (firstIdx > 0) remainder.substring(0, firstIdx).trim() else remainder.trim()

        var debit = 0L
        var credit = 0L
        var balance: Long? = null

        when (amounts.size) {
            3 -> {
                debit = amounts[0]
                credit = amounts[1]
                balance = amounts[2]
            }
            2 -> {
                val hasCr = remainder.contains("CR", ignoreCase = true) ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep)\\b"))
                val hasDr = remainder.contains("DR", ignoreCase = true) ||
                        desc.contains(Regex("(?i)\\b(debit|dr|upi|pos|atm|wdl|withdrawal|paid|transfer to)\\b"))
                if (hasCr && !hasDr) {
                    credit = amounts[0]
                    balance = amounts[1]
                } else {
                    debit = amounts[0]
                    balance = amounts[1]
                }
            }
            1 -> {
                val hasCr = remainder.contains("CR", ignoreCase = true)
                val hasDr = remainder.contains("DR", ignoreCase = true)
                if (hasCr) {
                    credit = amounts[0]
                } else if (hasDr) {
                    debit = amounts[0]
                } else {
                    if (amounts[0] < 0L) debit = -amounts[0] else credit = amounts[0]
                }
            }
            else -> {
                debit = amounts[amounts.size - 3]
                credit = amounts[amounts.size - 2]
                balance = amounts[amounts.size - 1]
            }
        }

        if (debit == 0L && credit == 0L) return null

        return StatementLineEntity(
            importId = importId,
            transactionDate = txDate,
            valueDate = txDate,
            description = desc,
            reference = null,
            debitPaise = debit,
            creditPaise = credit,
            balanceAfterPaise = balance,
            matchedTransactionId = null,
            matchStatus = StatementMatchStatus.MISSING_IN_APP
        )
    }

    private fun isHeaderOrNoiseLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ENGLISH)
        return lower.startsWith("page ") ||
                lower.contains("statement summary") ||
                lower.contains("account number") ||
                lower.contains("customer care") ||
                lower.contains("opening balance") ||
                lower.contains("closing balance")
    }
}
