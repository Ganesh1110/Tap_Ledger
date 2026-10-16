package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.TransferPlanKind
import com.tapledger.core.model.TransferPlanStatus

@Entity(
    tableName = "savings_transfer_plans",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["period_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["dest_account_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["trigger_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["period_id", "kind"], unique = true),
        Index(value = ["source_account_id"]),
        Index(value = ["dest_account_id"]),
        Index(value = ["transfer_group_id"]),
        Index(value = ["trigger_transaction_id"]),
        Index(value = ["status"])
    ]
)
data class SavingsTransferPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "period_id")
    val periodId: Long,
    @ColumnInfo(name = "kind")
    val kind: TransferPlanKind,
    @ColumnInfo(name = "source_account_id")
    val sourceAccountId: Long,
    @ColumnInfo(name = "dest_account_id")
    val destAccountId: Long,
    @ColumnInfo(name = "suggested_paise")
    val suggestedPaise: Long,
    @ColumnInfo(name = "moved_paise")
    val movedPaise: Long? = null,
    @ColumnInfo(name = "status")
    val status: TransferPlanStatus = TransferPlanStatus.SUGGESTED,
    @ColumnInfo(name = "transfer_group_id")
    val transferGroupId: String? = null,
    @ColumnInfo(name = "trigger_transaction_id")
    val triggerTransactionId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "moved_at")
    val movedAt: Long? = null,
    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long? = null
)
