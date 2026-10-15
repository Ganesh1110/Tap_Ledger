package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.SavingsContributionSource

@Entity(
    tableName = "savings_contributions",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["period_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["goal_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["period_id"])
    ]
)
data class SavingsContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "goal_id")
    val goalId: Long,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "source")
    val source: SavingsContributionSource,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long? = null,
    @ColumnInfo(name = "period_id")
    val periodId: Long? = null,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
