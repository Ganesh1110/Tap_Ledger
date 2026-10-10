package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

import com.tapledger.core.model.MonthCloseReconciliationStatus

@Entity(
    tableName = "month_closes",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_period_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["budget_period_id"], unique = true)
    ]
)
data class MonthCloseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "budget_period_id")
    val budgetPeriodId: Long,
    @ColumnInfo(name = "closed_at")
    val closedAt: Long,
    @ColumnInfo(name = "planned_income_paise")
    val plannedIncomePaise: Long,
    @ColumnInfo(name = "actual_income_paise")
    val actualIncomePaise: Long,
    @ColumnInfo(name = "total_budgeted_paise")
    val totalBudgetedPaise: Long,
    @ColumnInfo(name = "total_spent_paise")
    val totalSpentPaise: Long,
    @ColumnInfo(name = "total_saved_paise")
    val totalSavedPaise: Long,
    @ColumnInfo(name = "reconciliation_status")
    val reconciliationStatus: MonthCloseReconciliationStatus,
    @ColumnInfo(name = "override_reason")
    val overrideReason: String? = null,
    @ColumnInfo(name = "closing_balances_json")
    val closingBalancesJson: String
)
