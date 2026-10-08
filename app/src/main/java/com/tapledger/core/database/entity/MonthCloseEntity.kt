package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    @ColumnInfo(name = "closed_by")
    val closedBy: String = "USER",
    @ColumnInfo(name = "reconciliation_status")
    val reconciliationStatus: String,
    @ColumnInfo(name = "override_reason")
    val overrideReason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
