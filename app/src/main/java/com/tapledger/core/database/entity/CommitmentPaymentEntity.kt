package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.CommitmentPaymentStatus

@Entity(
    tableName = "commitment_payments",
    foreignKeys = [
        ForeignKey(
            entity = RecurringCommitmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["commitment_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["period_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["paid_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["commitment_id"]),
        Index(value = ["period_id"]),
        Index(value = ["paid_transaction_id"])
    ]
)
data class CommitmentPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "commitment_id")
    val commitmentId: Long,
    @ColumnInfo(name = "period_id")
    val periodId: Long,
    @ColumnInfo(name = "paid_transaction_id")
    val paidTransactionId: Long? = null,
    @ColumnInfo(name = "status")
    val status: CommitmentPaymentStatus = CommitmentPaymentStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
