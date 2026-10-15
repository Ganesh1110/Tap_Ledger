package com.tapledger.parser

import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import java.security.MessageDigest
import java.util.regex.Pattern

object FinancialNotificationParser {

    private val AMOUNT_PATTERN = Pattern.compile(
        "(?:(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?(?:/-)?))|" +
        "(?:(?:debited|credited|paid|spent|sent|received|transferred|withdrawn|refunded)\\s+(?:by|for|of)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?(?:/-)?))",
        Pattern.CASE_INSENSITIVE
    )

    private val UPI_REF_PATTERN = Pattern.compile(
        "(?:UPI\\s*(?:Ref|Txn|Ref\\s*no|Reference)?|Ref\\s*No\\.?|tran\\s*id|URN|RRN)\\s*[:\\s#]*([0-9]{9,16})",
        Pattern.CASE_INSENSITIVE
    )

    private val AVL_BAL_PATTERN = Pattern.compile(
        "(?:Avl\\s*(?:Bal|Balance)?|Available\\s*Bal(?:ance)?|Bal(?:ance)?\\s*(?:is|:))[:\\s]*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val OUTSTANDING_BAL_PATTERN = Pattern.compile(
        "(?:Outstanding|Total\\s*Due|Tot\\s*Due)[:\\s]*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val ACCOUNT_LAST4_PATTERN = Pattern.compile(
        "(?:A/C|A/c|Acct|Account|Card(?:\\s*ending)?)\\s*(?:no\\.?)?\\s*[xX*]*([0-9]{4})",
        Pattern.CASE_INSENSITIVE
    )

    private val CARD_MENTION_PATTERN = Pattern.compile(
        "\\b(?:credit\\s*card|debit\\s*card|card)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Filtering non-financial alerts (Section 33)
    private val NON_FINANCIAL_PATTERNS = listOf(
        Pattern.compile("OTP|one\\s*time\\s*password|verification\\s*code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("offer|discount|cashback\\s*up\\s*to|congratulations|deal\\s*of|pre-approved|apply\\s*now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bill\\s*due|statement\\s*is\\s*ready|due\\s*date|minimum\\s*due", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mandate\\s*(?:registered|requested)|pre-debit", Pattern.CASE_INSENSITIVE),
        Pattern.compile("credit\\s*limit\\s*(?:increased|is)", Pattern.CASE_INSENSITIVE)
    )

    private val FINANCIAL_HINT_PATTERNS = listOf(
        Pattern.compile("debited|credited|spent|paid|sent|received|transferred|refund|withdrawn", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bank|a/c|account|card|upi|vpa|wallet", Pattern.CASE_INSENSITIVE)
    )

    private val SIP_AUTO_DEBIT_PATTERN = Pattern.compile(
        "\\b(?:sip|auto[- ]?debit|ach|standing\\s*instruction|mandate|nach|mutual\\s*fund|mf\\s*investment|recurring\\s*deposit|rd\\s*installment)\\b",
        Pattern.CASE_INSENSITIVE
    )

    fun isPotentialFinancialAlert(text: String): Boolean {
        return FINANCIAL_HINT_PATTERNS.all { it.matcher(text).find() }
    }

    fun parse(event: NotificationEvent): ParsedFinancialEvent? {
        val combinedText = buildString {
            if (event.title.isNotBlank()) append(event.title).append(" ")
            if (event.text.isNotBlank()) append(event.text).append(" ")
            if (!event.subText.isNullOrBlank()) append(event.subText).append(" ")
            for (msg in event.messages) {
                append(msg).append(" ")
            }
        }.trim()

        if (combinedText.isEmpty()) return null

        // Check if message is non-financial (OTP, promo, bill-due)
        for (pattern in NON_FINANCIAL_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                return null
            }
        }

        // Amount extraction using zero-float MoneyFormatter
        val amountMatcher = AMOUNT_PATTERN.matcher(combinedText)
        if (!amountMatcher.find()) {
            return null
        }
        val amountStr = amountMatcher.group(1) ?: amountMatcher.group(2) ?: return null
        val amountPaise = try {
            MoneyFormatter.parseRupeesStringToPaise(amountStr)
        } catch (e: Exception) {
            return null
        }
        if (amountPaise <= 0L) return null

        // Account last 4
        val acctMatcher = ACCOUNT_LAST4_PATTERN.matcher(combinedText)
        val last4 = if (acctMatcher.find()) acctMatcher.group(1) else null

        val isCard = CARD_MENTION_PATTERN.matcher(combinedText).find()

        // UPI Reference
        val upiMatcher = UPI_REF_PATTERN.matcher(combinedText)
        val upiRef = if (upiMatcher.find()) upiMatcher.group(1) else null

        // Checkpoint Balance
        var checkpointBalance: Long? = null
        var checkpointType: com.tapledger.core.model.CheckpointType? = null

        val avlMatcher = AVL_BAL_PATTERN.matcher(combinedText)
        if (avlMatcher.find()) {
            checkpointBalance = try {
                avlMatcher.group(1)?.let { MoneyFormatter.parseRupeesStringToPaise(it, allowZero = true) }
            } catch (e: Exception) {
                null
            }
            if (checkpointBalance != null) {
                checkpointType = com.tapledger.core.model.CheckpointType.AVAILABLE_BALANCE
            }
        }

        if (checkpointBalance == null) {
            val dueMatcher = OUTSTANDING_BAL_PATTERN.matcher(combinedText)
            if (dueMatcher.find()) {
                checkpointBalance = try {
                    dueMatcher.group(1)?.let { MoneyFormatter.parseRupeesStringToPaise(it, allowZero = true) }
                } catch (e: Exception) {
                    null
                }
                if (checkpointBalance != null) {
                    checkpointType = com.tapledger.core.model.CheckpointType.TOTAL_OUTSTANDING
                }
            }
        }

        // Debit vs Credit vs Reversal detection
        val lower = combinedText.lowercase()
        val isReversal = lower.contains("reversed") || lower.contains("credited back") || lower.contains("refund")
        val isCredit = lower.contains("credited") || lower.contains("credit") || lower.contains("deposited") || lower.contains("received") || lower.contains("added") || isReversal
        val isDebit = lower.contains("debited") || lower.contains("debit") || lower.contains("spent") || lower.contains("paid") || lower.contains("withdrawn") || lower.contains("sent") || lower.contains("transferred") || lower.contains("purchase")

        if (!isCredit && !isDebit) {
            return null
        }

        val kind = when {
            isReversal -> TransactionKind.REFUND
            isCredit -> TransactionKind.INCOME
            else -> TransactionKind.EXPENSE
        }

        val direction = if (kind == TransactionKind.EXPENSE) TransactionDirection.OUT else TransactionDirection.IN

        // Merchant extraction
        val merchantRaw = extractMerchant(combinedText)
        val merchantNormalized = merchantRaw?.trim()?.uppercase()

        // Fingerprint generation: pkg:sha256
        val fingerprint = generateFingerprint(event.packageName, event.postTime, amountPaise, combinedText)

        return ParsedFinancialEvent(
            source = if (event.packageName.contains("messaging", ignoreCase = true) || event.packageName.contains("sms", ignoreCase = true)) {
                TransactionSource.SMS
            } else {
                TransactionSource.NOTIFICATION
            },
            sourceFingerprint = fingerprint,
            kind = kind,
            direction = direction,
            amountPaise = amountPaise,
            accountLast4 = last4,
            isCardMentioned = isCard,
            merchantRaw = merchantRaw,
            merchantNormalized = merchantNormalized,
            upiReference = upiRef,
            reportedAvailableBalancePaise = checkpointBalance,
            checkpointType = checkpointType,
            occurredAt = event.postTime,
            isReversalOrRefund = isReversal,
            rawPreviewRedacted = redactSensitiveData(combinedText),
            isSipOrAutoDebit = SIP_AUTO_DEBIT_PATTERN.matcher(combinedText).find()
        )
    }

    private fun extractMerchant(text: String): String? {
        // 1. Direct VPA handle extraction: e.g. "to VPA swiggy.bangalore@icici" or "to paytmqr123@paytm"
        val vpaPattern = Pattern.compile("(?:to\\s+VPA|to|at|vpa|paid\\s+to)\\s+([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE)
        val vpaMatcher = vpaPattern.matcher(text)
        if (vpaMatcher.find()) {
            val vpa = vpaMatcher.group(1)?.trim()
            if (!vpa.isNullOrBlank()) return vpa
        }

        // 2. Common descriptor patterns: "to <MERCHANT> on" or "at <MERCHANT> on"
        val patterns = listOf(
            Pattern.compile("(?:to|at|info:)\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)(?:\\s+(?:on|via|ref|upi|for|dated|towards|avl|bal|a/c|from|using|with|in)|\\.|,|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Paid\\s+(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+(?:/-)?\\s+to\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)(?:\\s+(?:on|via|ref|upi|for|dated|towards|avl|bal|a/c|from|using|with|in)|\\.|,|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("spent\\s+(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+(?:/-)?\\s+at\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)(?:\\s+(?:on|via|ref|upi|for|dated|towards|avl|bal|a/c|from|using|with|in)|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) {
                val candidate = m.group(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("your", ignoreCase = true) && !candidate.equals("account", ignoreCase = true)) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun generateFingerprint(pkg: String, postTime: Long, amountPaise: Long, text: String): String {
        val input = "$pkg:$postTime:$amountPaise:${text.trim().lowercase()}"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray())
        val hashHex = bytes.joinToString("") { "%02x".format(it) }
        return "$pkg:$hashHex"
    }

    private fun redactSensitiveData(text: String): String {
        return text.replace(Pattern.compile("[0-9]{9,16}").toRegex(), "********")
    }
}
