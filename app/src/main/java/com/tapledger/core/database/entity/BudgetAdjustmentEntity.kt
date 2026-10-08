package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budget_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_period_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_category_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["budget_period_id"]),
        Index(value = ["from_category_id"]),
        Index(value = ["to_category_id"])
    ]
)
data class BudgetAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "budget_period_id")
    val budgetPeriodId: Long,
    @ColumnInfo(name = "from_category_id")
    val fromCategoryId: Long,
    @ColumnInfo(name = "to_category_id")
    val toCategoryId: Long,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    val reason: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
