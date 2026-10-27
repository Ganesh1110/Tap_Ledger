package com.tapledger.parser

import com.tapledger.core.model.CheckpointType
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource

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
    val direction: TransactionDirection,
    val amountPaise: Long,
    val accountLast4: String? = null,
    val isCardMentioned: Boolean = false,
    val merchantRaw: String? = null,
    val merchantNormalized: String? = null,
    val upiReference: String? = null,
    val reportedAvailableBalancePaise: Long? = null,
    val checkpointType: CheckpointType? = null,
    val occurredAt: Long,
    val isReversalOrRefund: Boolean = false,
    val rawPreviewRedacted: String,
    val isSipOrAutoDebit: Boolean = false,
    val isLoanEmi: Boolean = false
)
