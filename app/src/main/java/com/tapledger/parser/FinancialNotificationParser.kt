package com.tapledger.parser

import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransferDirection
import java.security.MessageDigest
import java.util.regex.Pattern

object FinancialNotificationParser {

    private val AMOUNT_PATTERN = Pattern.compile("(?:Rs\\.?|INR|₹|by)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val UPI_REF_PATTERN = Pattern.compile("(?:UPI\\s*(?:Ref|Txn|Ref\\s*no|Reference)|Ref\\s*No\\.?|tran\\s*id|Ref)\\s*[:\\s]*([0-9]{9,16})", Pattern.CASE_INSENSITIVE)
    private val AVL_BAL_PATTERN = Pattern.compile("(?:Avl\\s*Bal|Available\\s*Bal(?:ance)?|Bal(?:ance)?\\s*is)[:\\s]*(?:Rs\\.?|INR|₹)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE)
    private val ACCOUNT_LAST4_PATTERN = Pattern.compile("(?:A/C|A/c|Acct|Card(?:\\s*ending)?)\\s*(?:no\\.?)?\\s*[xX*]*([0-9]{4})", Pattern.CASE_INSENSITIVE)

    // Filtering non-financial alerts (Section 33)
    private val NON_FINANCIAL_PATTERNS = listOf(
        Pattern.compile("OTP|one\\s*time\\s*password|verification\\s*code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("offer|discount|cashback\\s*up\\s*to|congratulations|deal\\s*of", Pattern.CASE_INSENSITIVE),
        Pattern.compile("bill\\s*due|statement\\s*is\\s*ready|due\\s*date|minimum\\s*due", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mandate\\s*(?:registered|requested)|pre-debit", Pattern.CASE_INSENSITIVE),
        Pattern.compile("credit\\s*limit\\s*(?:increased|is)", Pattern.CASE_INSENSITIVE)
    )

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
                // If it's an OTP or pure promo, discard (return null)
                return null
            }
        }

        // Amount extraction
        val amountMatcher = AMOUNT_PATTERN.matcher(combinedText)
        if (!amountMatcher.find()) {
            return null // No financial amount found
        }
        val amountStr = amountMatcher.group(1) ?: return null
        val amountPaise = try {
            MoneyFormatter.toPaise(amountStr)
        } catch (e: Exception) {
            return null
        }
        if (amountPaise <= 0L) return null

        // Account last 4
        val acctMatcher = ACCOUNT_LAST4_PATTERN.matcher(combinedText)
        val last4 = if (acctMatcher.find()) acctMatcher.group(1) else null

        // UPI Reference
        val upiMatcher = UPI_REF_PATTERN.matcher(combinedText)
        val upiRef = if (upiMatcher.find()) upiMatcher.group(1) else null

        // Reported Available Balance (Section 40)
        val balMatcher = AVL_BAL_PATTERN.matcher(combinedText)
        val avlBalPaise = if (balMatcher.find()) {
            try {
                balMatcher.group(1)?.let { MoneyFormatter.toPaise(it) }
            } catch (e: Exception) {
                null
            }
        } else null

        // Debit vs Credit vs Reversal detection
        val lower = combinedText.lowercase()
        val isReversal = lower.contains("reversed") || lower.contains("credited back") || lower.contains("refund")
        val isCredit = lower.contains("credited") || lower.contains("credit") || lower.contains("deposited") || lower.contains("received") || lower.contains("added") || isReversal
        val isDebit = lower.contains("debited") || lower.contains("debit") || lower.contains("spent") || lower.contains("paid") || lower.contains("withdrawn") || lower.contains("sent") || lower.contains("transferred") || lower.contains("purchase")

        if (!isCredit && !isDebit) {
            // Amount present without financial movement verb -> discard to prevent false alerts
            return null
        }

        val kind = when {
            isReversal -> TransactionKind.REFUND
            isCredit -> TransactionKind.INCOME
            else -> TransactionKind.EXPENSE
        }

        // Merchant extraction
        val merchantRaw = extractMerchant(combinedText)
        val merchantNormalized = merchantRaw?.trim()?.uppercase()

        // Fingerprint generation (Section 20)
        val fingerprint = generateFingerprint(event.packageName, event.postTime, amountPaise, combinedText)

        return ParsedFinancialEvent(
            source = if (event.packageName.contains("messaging", ignoreCase = true) || event.packageName.contains("sms", ignoreCase = true)) {
                TransactionSource.SMS
            } else {
                TransactionSource.NOTIFICATION
            },
            sourceFingerprint = fingerprint,
            kind = kind,
            amountPaise = amountPaise,
            accountLast4 = last4,
            merchantRaw = merchantRaw,
            merchantNormalized = merchantNormalized,
            upiReference = upiRef,
            transferDirection = if (kind == TransactionKind.INCOME) TransferDirection.IN else TransferDirection.OUT,
            reportedAvailableBalancePaise = avlBalPaise,
            occurredAt = event.postTime,
            isReversalOrRefund = isReversal,
            rawPreviewRedacted = redactSensitiveData(combinedText)
        )
    }

    private fun extractMerchant(text: String): String? {
        val lower = text.lowercase()
        // Common pattern: "to <MERCHANT> on" or "at <MERCHANT> on"
        val patterns = listOf(
            Pattern.compile("(?:to|at|vpa)\\s+([A-Za-z0-9&\\s]{3,25})(?:\\s+on|\\s+via|\\s+ref|\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Paid\\s+(?:Rs\\.?|INR|₹)?\\s*[0-9,.]+\\s+to\\s+([A-Za-z0-9&\\s]{3,25})", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(text)
            if (m.find()) {
                val candidate = m.group(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("your", ignoreCase = true)) {
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
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun redactSensitiveData(text: String): String {
        return text
            .replace(Pattern.compile("[0-9]{9,16}").toRegex(), "********")
    }
}
