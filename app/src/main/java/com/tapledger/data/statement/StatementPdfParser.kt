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
import kotlin.math.abs

import java.time.YearMonth

class PdfPasswordRequiredException(
    message: String = "This PDF statement is password-protected. Please enter the statement password."
) : Exception(message)

data class StatementMonthData(
    val yearMonth: YearMonth,
    val lines: List<StatementLineEntity>,
    val openingBalancePaise: Long,
    val closingBalancePaise: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val totalDebitsPaise: Long,
    val totalCreditsPaise: Long
)

data class PdfStatementResult(
    val lines: List<StatementLineEntity>,
    val detectedOpeningBalancePaise: Long?,
    val detectedClosingBalancePaise: Long?,
    val detectedPeriodStart: Long?,
    val detectedPeriodEnd: Long?,
    val detectedMonths: List<YearMonth> = emptyList(),
    val monthlyBreakdown: List<StatementMonthData> = emptyList(),
    val rawText: String
)

/**
 * Robust on-device parser for Indian bank PDF statements (HDFC, SBI, ICICI, Axis, Federal Bank, etc.)
 * conforming to Flow.md §11.1 & IMPLEMENTATION_PLAN.md §24.
 *
 * Runs 100% offline without network access.
 * Supports password-protected PDF statements (passwords are used strictly for decryption
 * and never persisted).
 */
object StatementPdfParser {

    private val DATE_PREFIX_REGEX = Pattern.compile(
        "^(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[-\\s/][A-Za-z]{3}[-\\s/]\\d{2,4})\\b"
    )

    private val EMBEDDED_DATE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[-\\s/][A-Za-z]{3}[-\\s/]\\d{2,4})\\b"
    )

    // Strictly matches monetary amounts formatted with 2 decimal places, thousands commas,
    // or explicit currency / Cr / Dr indicators. Prevents matching year numbers (e.g. 26 from 01-10-26)
    // or reference numbers.
    private val STRICT_AMOUNT_PATTERN = Pattern.compile(
        "(?:₹|Rs\\.?|INR\\s*)?[-+]?\\d{1,3}(?:,\\d{2,3})*\\.\\d{2}\\b|(?:₹|Rs\\.?|INR\\s*)?[-+]?\\d{1,3}(?:,\\d{2,3})+\\b|[-+]?\\d+(?:\\.\\d{2})?\\s*(?:[Cc][Rr]|[Dd][Rr])\\b|(?:₹|Rs\\.?|INR\\s*)[-+]?\\d+\\b"
    )

    private val OPENING_BALANCE_REGEX = Pattern.compile(
        "(?i)(?:opening\\s*balance|balance\\s*b/?f|brought\\s*forward|open\\s*bal)[\\s:=-]*([₹RsINR\\s]*\\d[\\d,.]*)"
    )

    private val CLOSING_BALANCE_REGEX = Pattern.compile(
        "(?i)(?:closing\\s*balance|balance\\s*c/?f|carried\\s*forward|close\\s*bal|ending\\s*balance)[\\s:=-]*([₹RsINR\\s]*\\d[\\d,.]*)"
    )

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

            val lines = parseTextToLines(rawText, importId)

            val detectedOpening = detectOpeningBalance(rawText) ?: lines.firstOrNull()?.let { first ->
                first.balanceAfterPaise?.let { bal ->
                    bal + first.debitPaise - first.creditPaise
                }
            }
            val detectedClosing = detectClosingBalance(rawText) ?: lines.lastOrNull()?.balanceAfterPaise

            val minDate = lines.minOfOrNull { it.transactionDate }
            val maxDate = lines.maxOfOrNull { it.transactionDate }

            val detectedMonths = lines.map { DateTimeUtils.toYearMonth(it.transactionDate) }.distinct().sorted()
            val monthlyBreakdown = splitLinesIntoMonthlyBreakdown(
                lines = lines,
                overallOpeningPaise = detectedOpening,
                overallClosingPaise = detectedClosing
            )

            return PdfStatementResult(
                lines = lines,
                detectedOpeningBalancePaise = detectedOpening,
                detectedClosingBalancePaise = detectedClosing,
                detectedPeriodStart = minDate,
                detectedPeriodEnd = maxDate,
                detectedMonths = detectedMonths,
                monthlyBreakdown = monthlyBreakdown,
                rawText = rawText
            )
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Splits statement lines chronologically by calendar month (YearMonth) conforming to Flow.md §10 & §11.
     * Computes exact monthly opening and closing balances preserving the statement chain invariant:
     * Opening(Month_n) == Closing(Month_{n-1})
     */
    fun splitLinesIntoMonthlyBreakdown(
        lines: List<StatementLineEntity>,
        overallOpeningPaise: Long?,
        overallClosingPaise: Long?
    ): List<StatementMonthData> {
        if (lines.isEmpty()) return emptyList()

        val sortedLines = lines.sortedBy { it.transactionDate }
        val grouped = sortedLines.groupBy { DateTimeUtils.toYearMonth(it.transactionDate) }.toSortedMap()

        val result = mutableListOf<StatementMonthData>()
        var runningBalance = overallOpeningPaise ?: sortedLines.firstOrNull()?.let { first ->
            first.balanceAfterPaise?.let { it + first.debitPaise - first.creditPaise }
        } ?: 0L

        val monthKeys = grouped.keys.toList()
        for (i in monthKeys.indices) {
            val ym = monthKeys[i]
            val monthLines = grouped[ym] ?: emptyList()
            val totalDebits = monthLines.sumOf { it.debitPaise }
            val totalCredits = monthLines.sumOf { it.creditPaise }

            val firstLine = monthLines.firstOrNull()
            val lastLine = monthLines.lastOrNull()

            val monthOpening = if (i == 0 && overallOpeningPaise != null) {
                overallOpeningPaise
            } else if (firstLine?.balanceAfterPaise != null) {
                firstLine.balanceAfterPaise!! + firstLine.debitPaise - firstLine.creditPaise
            } else {
                runningBalance
            }

            val monthClosing = if (i == monthKeys.lastIndex && overallClosingPaise != null) {
                overallClosingPaise
            } else if (lastLine?.balanceAfterPaise != null) {
                lastLine.balanceAfterPaise!!
            } else {
                monthOpening + totalCredits - totalDebits
            }

            runningBalance = monthClosing

            val startMillis = DateTimeUtils.getStartOfMonthEpochMillis(ym.year, ym.monthValue)
            val endMillis = DateTimeUtils.getEndOfMonthEpochMillis(ym.year, ym.monthValue) - 1L

            result.add(
                StatementMonthData(
                    yearMonth = ym,
                    lines = monthLines,
                    openingBalancePaise = monthOpening,
                    closingBalancePaise = monthClosing,
                    periodStart = startMillis,
                    periodEnd = endMillis,
                    totalDebitsPaise = totalDebits,
                    totalCreditsPaise = totalCredits
                )
            )
        }

        return result
    }

    internal fun detectOpeningBalance(text: String): Long? {
        val matcher = OPENING_BALANCE_REGEX.matcher(text)
        if (matcher.find()) {
            val amtStr = matcher.group(1)
            val parsed = StatementCsvParser.parseAmountOrNull(amtStr)
            if (parsed != null && parsed != 0L) return abs(parsed)
        }
        return null
    }

    internal fun detectClosingBalance(text: String): Long? {
        val matcher = CLOSING_BALANCE_REGEX.matcher(text)
        if (matcher.find()) {
            val amtStr = matcher.group(1)
            val parsed = StatementCsvParser.parseAmountOrNull(amtStr)
            if (parsed != null && parsed != 0L) return abs(parsed)
        }
        return null
    }

    fun parseTextToLines(
        rawText: String,
        importId: Long
    ): List<StatementLineEntity> {
        val rawLines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<StatementLineEntity>()
        var currentEntity: StatementLineEntity? = null
        var isAcceptingContinuation = true

        for (line in rawLines) {
            val parsedLine = tryParseTransactionLine(line, importId)
            if (parsedLine != null) {
                if (currentEntity != null) {
                    result.add(currentEntity)
                }
                currentEntity = parsedLine
                isAcceptingContinuation = true
            } else if (currentEntity != null && isAcceptingContinuation) {
                // Continuation line for narration / description
                if (isHeaderOrNoiseLine(line)) {
                    // Stop accepting further continuation lines once page headers/footers/disclaimers/addresses are reached
                    isAcceptingContinuation = false
                } else {
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

        var remainder = line.substring(dateMatcher.end()).trim()
        if (remainder.isEmpty()) return null

        // 1. Strip Value Date if present in remainder to prevent date components/strings
        // (e.g. "03 Oct 2026") from interfering with amount parsing.
        var valDate = txDate
        val embeddedDateMatcher = EMBEDDED_DATE_PATTERN.matcher(remainder)
        val datesToRemove = mutableListOf<String>()
        while (embeddedDateMatcher.find()) {
            val embeddedStr = embeddedDateMatcher.group()
            val parsedVal = StatementCsvParser.parseDate(embeddedStr)
            if (parsedVal != null) {
                valDate = parsedVal
                datesToRemove.add(embeddedStr)
            }
        }
        for (d in datesToRemove) {
            remainder = remainder.replace(d, " ").trim()
        }

        // 2. Try tabular splitting first if 2 or more spaces/tabs separate columns
        val tabularTokens = remainder.split(Regex("\\s{2,}|\t")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tabularTokens.size >= 2) {
            val tabularParsed = extractFromTabularTokens(txDate, valDate, tabularTokens, importId)
            if (tabularParsed != null) return tabularParsed
        }

        // 3. Single-space or non-tabular format fallback
        return extractFromSingleSpaceLine(txDate, valDate, remainder, importId)
    }

    private fun extractFromTabularTokens(
        txDate: Long,
        valDate: Long,
        tokens: List<String>,
        importId: Long
    ): StatementLineEntity? {
        val desc = tokens[0]
        val otherTokens = tokens.subList(1, tokens.size)
        if (otherTokens.isEmpty()) return null

        val amounts = otherTokens.mapNotNull { StatementCsvParser.parseAmountOrNull(it) }.map { abs(it) }
        if (amounts.isEmpty()) return null

        var ref: String? = null
        val nonAmountTokens = otherTokens.filter { StatementCsvParser.parseAmountOrNull(it) == null }
        if (nonAmountTokens.isNotEmpty()) {
            ref = nonAmountTokens.firstOrNull { it.matches(Regex("[A-Za-z0-9/-]{4,}")) }
        }

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
                val isCredit = otherTokens.any { it.contains("CR", ignoreCase = true) } ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep|deposit)\\b"))
                val isDebit = otherTokens.any { it.contains("DR", ignoreCase = true) } ||
                        desc.contains(Regex("(?i)\\b(debit|dr|upi|pos|atm|wdl|withdrawal|paid|transfer to|bill|tax|fee|purchase)\\b"))

                if (isCredit && !isDebit) {
                    credit = amounts[0]
                    balance = amounts[1]
                } else {
                    debit = amounts[0]
                    balance = amounts[1]
                }
            }
            1 -> {
                val isCredit = otherTokens.any { it.contains("CR", ignoreCase = true) } ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep|deposit)\\b"))
                if (isCredit) {
                    credit = amounts[0]
                } else {
                    debit = amounts[0]
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
            valueDate = valDate,
            description = desc,
            reference = ref,
            debitPaise = debit,
            creditPaise = credit,
            balanceAfterPaise = balance,
            matchedTransactionId = null,
            matchStatus = StatementMatchStatus.MISSING_IN_APP
        )
    }

    private fun extractFromSingleSpaceLine(
        txDate: Long,
        valDate: Long,
        remainder: String,
        importId: Long
    ): StatementLineEntity? {
        // Find matches of STRICT_AMOUNT_PATTERN
        val amountMatches = mutableListOf<MatchResult>()
        val matcher = STRICT_AMOUNT_PATTERN.matcher(remainder)
        while (matcher.find()) {
            amountMatches.add(MatchResult(matcher.start(), matcher.end(), matcher.group()))
        }

        if (amountMatches.isEmpty()) return null

        val parsedAmounts = amountMatches.mapNotNull { StatementCsvParser.parseAmountOrNull(it.group) }.map { abs(it) }
        if (parsedAmounts.isEmpty()) return null

        // Description and Ref are everything BEFORE the first amount match
        val firstAmountStart = amountMatches.first().start
        val textBeforeAmounts = remainder.substring(0, firstAmountStart).trim()

        var desc = textBeforeAmounts
        var ref: String? = null

        // Extract reference number token if present at the end of textBeforeAmounts or in text
        val refMatcher = Pattern.compile("\\b([A-Za-z0-9/-]{6,25})\\b").matcher(textBeforeAmounts)
        var lastRefCandidate: String? = null
        while (refMatcher.find()) {
            val candidate = refMatcher.group(1) ?: continue
            if (candidate.matches(Regex("(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)$"))) continue
            if (candidate.matches(Regex("^\\d{6,}$")) || candidate.contains("-") || candidate.contains("/")) {
                lastRefCandidate = candidate
            }
        }
        if (lastRefCandidate != null) {
            ref = lastRefCandidate
            if (desc.endsWith(lastRefCandidate)) {
                desc = desc.substring(0, desc.length - lastRefCandidate.length).trim()
            }
        }

        var debit = 0L
        var credit = 0L
        var balance: Long? = null

        when (parsedAmounts.size) {
            3 -> {
                debit = parsedAmounts[0]
                credit = parsedAmounts[1]
                balance = parsedAmounts[2]
            }
            2 -> {
                val hasCr = remainder.contains("CR", ignoreCase = true) ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep|deposit)\\b"))
                val hasDr = remainder.contains("DR", ignoreCase = true) ||
                        desc.contains(Regex("(?i)\\b(debit|dr|upi|pos|atm|wdl|withdrawal|paid|transfer to|bill|tax|fee|purchase)\\b"))

                if (hasCr && !hasDr) {
                    credit = parsedAmounts[0]
                    balance = parsedAmounts[1]
                } else {
                    debit = parsedAmounts[0]
                    balance = parsedAmounts[1]
                }
            }
            1 -> {
                val hasCr = remainder.contains("CR", ignoreCase = true) ||
                        desc.contains(Regex("(?i)\\b(credit|cr|salary|refund|interest|cashback|reversal|dep|deposit)\\b"))
                if (hasCr) {
                    credit = parsedAmounts[0]
                } else {
                    debit = parsedAmounts[0]
                }
            }
            else -> {
                debit = parsedAmounts[parsedAmounts.size - 3]
                credit = parsedAmounts[parsedAmounts.size - 2]
                balance = parsedAmounts[parsedAmounts.size - 1]
            }
        }

        if (debit == 0L && credit == 0L) return null

        return StatementLineEntity(
            importId = importId,
            transactionDate = txDate,
            valueDate = valDate,
            description = desc.ifBlank { remainder.trim() },
            reference = ref,
            debitPaise = debit,
            creditPaise = credit,
            balanceAfterPaise = balance,
            matchedTransactionId = null,
            matchStatus = StatementMatchStatus.MISSING_IN_APP
        )
    }

    private data class MatchResult(val start: Int, val end: Int, val group: String)

    private fun isHeaderOrNoiseLine(line: String): Boolean {
        val lower = line.lowercase(Locale.ENGLISH)

        // 1. Page numbers and statement period headers
        if (lower.startsWith("page ") || lower.contains("page 1") || lower.contains("page 2") ||
            lower.contains("statement summary") || lower.contains("account summary") ||
            lower.contains("statement of account") || lower.contains("statement period")
        ) return true

        // 2. Table column headers
        if (lower.contains("cheque no.") || lower.contains("chq./ref") ||
            lower.contains("withdrawal amt") || lower.contains("deposit amt") ||
            lower.contains("closing balance") || lower.contains("value dt") ||
            lower.contains("tran date") || lower.contains("txn date") ||
            lower.contains("particulars") || lower.contains("narration")
        ) return true

        // 3. Opening / Closing balance lines
        if (lower.contains("opening balance") || lower.contains("closing balance") ||
            lower.contains("balance b/f") || lower.contains("balance c/f") ||
            lower.contains("brought forward") || lower.contains("carried forward")
        ) return true

        // 4. Legal disclaimers & notice text
        if (lower.contains("contents of this statement") ||
            lower.contains("considered correct if no error") ||
            lower.contains("receipt of statement") ||
            lower.contains("address on this statement") ||
            lower.contains("record with the bank") ||
            lower.contains("registered office")
        ) return true

        // 5. GST, Bank Office, and Web URLs
        if (lower.contains("gstn:") || lower.contains("gstin") ||
            lower.contains("goods-and-service-tax") ||
            lower.contains("http:") || lower.contains("https:") ||
            lower.contains(".com/") || lower.contains(".co.in/") ||
            lower.contains("account branch :") || lower.contains("branch code :")
        ) return true

        // 6. Customer profile, address & account metadata
        if (lower.contains("customer care") || lower.contains("account number") ||
            lower.contains("cust id :") || lower.contains("customer id") ||
            lower.contains("account no :") || lower.contains("joint holders :") ||
            lower.contains("rtgs/neft ifsc") || lower.contains("micr :") ||
            lower.contains("nomination :") || lower.contains("account type :") ||
            lower.contains("od limit :") || lower.contains("open date :") ||
            lower.contains("city :") || lower.contains("state :") ||
            lower.contains("phone no.") || lower.contains("email :") ||
            lower.contains("currency : inr")
        ) return true

        return false
    }
}
