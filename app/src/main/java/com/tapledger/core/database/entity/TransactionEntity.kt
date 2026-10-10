package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.CheckpointType
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["counterparty_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["refund_of_transaction_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["possible_duplicate_of_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["occurred_at"]),
        Index(value = ["status"]),
        Index(value = ["transfer_group_id"]),
        Index(value = ["upi_reference"]),
        Index(value = ["counterparty_account_id"]),
        Index(value = ["refund_of_transaction_id"]),
        Index(value = ["possible_duplicate_of_id"]),
        Index(value = ["source_fingerprint"], unique = true),
        Index(value = ["account_id", "upi_reference", "direction", "amount_paise"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,
    val kind: TransactionKind,
    val direction: TransactionDirection,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "merchant_raw")
    val merchantRaw: String? = null,
    @ColumnInfo(name = "merchant_normalized")
    val merchantNormalized: String? = null,
    val status: TransactionStatus,
    @ColumnInfo(name = "previous_status")
    val previousStatus: TransactionStatus? = null,
    val source: TransactionSource,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "original_occurred_at")
    val originalOccurredAt: Long? = null,
    @ColumnInfo(name = "upi_reference")
    val upiReference: String? = null,
    @ColumnInfo(name = "transfer_group_id")
    val transferGroupId: String? = null,
    @ColumnInfo(name = "counterparty_account_id")
    val counterpartyAccountId: Long? = null,
    @ColumnInfo(name = "refund_of_transaction_id")
    val refundOfTransactionId: Long? = null,
    @ColumnInfo(name = "possible_duplicate_of_id")
    val possibleDuplicateOfId: Long? = null,
    @ColumnInfo(name = "checkpoint_balance_paise")
    val checkpointBalancePaise: Long? = null,
    @ColumnInfo(name = "checkpoint_type")
    val checkpointType: CheckpointType? = null,
    val notes: String? = null,
    @ColumnInfo(name = "source_fingerprint")
    val sourceFingerprint: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
