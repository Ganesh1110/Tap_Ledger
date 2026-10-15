package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["period_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["period_id", "goal_id"], unique = true),
        Index(value = ["goal_id"])
    ]
)
data class SavingsPlanEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "period_id")
    val periodId: Long,
    @ColumnInfo(name = "goal_id")
    val goalId: Long,
    @ColumnInfo(name = "planned_paise")
    val plannedPaise: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
