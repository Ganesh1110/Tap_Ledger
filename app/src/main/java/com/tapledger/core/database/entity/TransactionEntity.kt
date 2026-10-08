package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection

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
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["refund_of_transaction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["source_fingerprint"], unique = true),
        Index(value = ["occurred_at"]),
        Index(value = ["status"]),
        Index(value = ["transfer_group_id"]),
        Index(value = ["upi_reference"]),
        Index(value = ["refund_of_transaction_id"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    val kind: TransactionKind,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "merchant_raw")
    val merchantRaw: String? = null,
    @ColumnInfo(name = "merchant_normalized")
    val merchantNormalized: String? = null,
    @ColumnInfo(name = "upi_reference")
    val upiReference: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val status: TransactionStatus,
    val source: TransactionSource,
    @ColumnInfo(name = "source_fingerprint")
    val sourceFingerprint: String? = null,
    @ColumnInfo(name = "transfer_group_id")
    val transferGroupId: Long? = null,
    @ColumnInfo(name = "transfer_direction")
    val transferDirection: TransferDirection? = null,
    @ColumnInfo(name = "refund_of_transaction_id")
    val refundOfTransactionId: Long? = null,
    val note: String? = null
)
