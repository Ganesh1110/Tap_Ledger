package com.tapledger.parser

import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransferDirection

data class NotificationEvent(
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String? = null,
    val messages: List<String> = emptyList(), // EXTRA_MESSAGES for grouped notifications
    val postTime: Long
)

data class ParsedFinancialEvent(
    val source: TransactionSource,
    val sourceFingerprint: String,
    val kind: TransactionKind,
    val amountPaise: Long,
    val accountLast4: String? = null,
    val merchantRaw: String? = null,
    val merchantNormalized: String? = null,
    val upiReference: String? = null,
    val transferDirection: TransferDirection? = null,
    val reportedAvailableBalancePaise: Long? = null, // Section 40: Avl Bal cross-check
    val occurredAt: Long,
    val isReversalOrRefund: Boolean = false,
    val rawPreviewRedacted: String
)
