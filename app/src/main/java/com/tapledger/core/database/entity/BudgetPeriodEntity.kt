package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.BudgetPeriodStatus

@Entity(
    tableName = "budget_periods",
    indices = [
        Index(value = ["year", "month"], unique = true)
    ]
)
data class BudgetPeriodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val year: Int,
    val month: Int,
    @ColumnInfo(name = "start_at")
    val startAt: Long,
    @ColumnInfo(name = "end_at")
    val endAt: Long,
    @ColumnInfo(name = "income_paise")
    val incomePaise: Long,
    val status: BudgetPeriodStatus = BudgetPeriodStatus.OPEN,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
