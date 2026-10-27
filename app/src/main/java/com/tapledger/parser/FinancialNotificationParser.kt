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
        "(?:(?:debited|credited|paid|payment|spent|sent|received|transferred|withdrawn|refunded)\\s+(?:by|for|of|with)?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?(?:/-)?))",
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
        "(?:A/C|A/c|Acct|Account|Card(?:\\s*ending)?|Bank|ending(?:\\s*in)?|ending\\s*with)\\s*(?:no\\.?)?\\s*[xX*•.·\\-]*\\s*([0-9]{4})\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val MASKED_ACCOUNT_FALLBACK_PATTERN = Pattern.compile(
        "(?:^|\\s|[a-zA-Z:])[xX*•.·\\-]{2,}\\s*([0-9]{4})\\b"
    )

    private val CARD_MENTION_PATTERN = Pattern.compile(
        "\\b(?:credit\\s*card|debit\\s*card|card)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Filtering non-financial alerts (Section 33)
    private val PURE_OTP_PATTERN = Pattern.compile(
        "\\b(?:OTP|one\\s*time\\s*password|verification\\s*code|auth\\s*code|login\\s*code)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val NON_FINANCIAL_PATTERNS = listOf(
        Pattern.compile("\\b(?:pre-approved|apply\\s*now|claim\\s*your\\s*offer|exclusive\\s*deal|congratulations!\\s*you\\s*won|instant\\s*loan)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:bill\\s*(?:is\\s*)?due|statement\\s*is\\s*ready|minimum\\s*(?:amount\\s*)?due)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:mandate\\s*(?:registered|requested)|pre-debit\\s*notification)\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:credit\\s*limit\\s*(?:increased|is))\\b", Pattern.CASE_INSENSITIVE)
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

        // 1. Unconditional check: OTP messages are never financial records
        if (PURE_OTP_PATTERN.matcher(combinedText).find()) {
            return null
        }

        // 2. Amount extraction using zero-float MoneyFormatter
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

        // 3. Debit vs Credit vs Reversal detection
        val lower = combinedText.lowercase()
        val isReversal = lower.contains("reversed") || lower.contains("credited back") || lower.contains("refund")
        val isDebit = lower.contains("debited") ||
            lower.contains("debit") ||
            lower.contains("spent") ||
            lower.contains("paid") ||
            (lower.contains("payment") && !lower.contains("received")) ||
            lower.contains("withdrawn") ||
            lower.contains("sent") ||
            lower.contains("transferred") ||
            lower.contains("purchase")

        // Disambiguate credit: phrases like "credit card" or "credit limit" should not falsely classify expenses as credits
        val textWithoutCardMentions = lower
            .replace("credit card", "")
            .replace("credit limit", "")
            .replace("credit line", "")

        val isCredit = textWithoutCardMentions.contains("credited") ||
            textWithoutCardMentions.contains("deposited") ||
            textWithoutCardMentions.contains("received") ||
            textWithoutCardMentions.contains("added") ||
            textWithoutCardMentions.contains("credit to") ||
            textWithoutCardMentions.contains("credit a/c") ||
            (!lower.contains("credit card") && textWithoutCardMentions.contains("credit")) ||
            isReversal

        if (!isCredit && !isDebit) {
            return null
        }

        // 4. Non-financial advisory check: drop pure promotional or bill reminders (unless confirmed transaction)
        for (pattern in NON_FINANCIAL_PATTERNS) {
            if (pattern.matcher(combinedText).find()) {
                if (lower.contains("pre-debit") || lower.contains("is due") || lower.contains("statement is ready") || lower.contains("apply now")) {
                    return null
                }
            }
        }

        // Account last 4
        val acctMatcher = ACCOUNT_LAST4_PATTERN.matcher(combinedText)
        var last4 = if (acctMatcher.find()) acctMatcher.group(1) else null
        if (last4 == null) {
            val fallbackMatcher = MASKED_ACCOUNT_FALLBACK_PATTERN.matcher(combinedText)
            if (fallbackMatcher.find()) {
                last4 = fallbackMatcher.group(1)
            }
        }

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

        val kind = when {
            isReversal -> TransactionKind.REFUND
            isDebit && !isCredit -> TransactionKind.EXPENSE
            isCredit && !isDebit -> TransactionKind.INCOME
            isDebit -> TransactionKind.EXPENSE // Debit indicators ("spent", "paid", etc.) beat credit card mentions
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
        val boundary = "(?:\\s+(?:was|is|successful|completed|on|via|ref|upi|for|dated|towards|avl|bal|a/c|from|using|with|in)|\\.|,|$)"
        val patterns = listOf(
            Pattern.compile("(?:to|at|info:)\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)$boundary", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Paid\\s+(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+(?:/-)?\\s+to\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)$boundary", Pattern.CASE_INSENSITIVE),
            Pattern.compile("spent\\s+(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+(?:/-)?\\s+at\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)$boundary", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Payment\\s+(?:of\\s+)?(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+(?:/-)?\\s+to\\s+([A-Za-z0-9&._-]+(?:\\s+[A-Za-z0-9&._-]+)*?)$boundary", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) {
                val candidate = m.group(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("your", ignoreCase = true) && !candidate.equals("account", ignoreCase = true) && !candidate.equals("a/c", ignoreCase = true) && !candidate.equals("vpa", ignoreCase = true)) {
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
