package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket

@Entity(
    tableName = "budget_allocations",
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
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["budget_period_id", "category_id"], unique = true),
        Index(value = ["category_id"])
    ]
)
data class BudgetAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "budget_period_id")
    val budgetPeriodId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "category_name_snapshot")
    val categoryNameSnapshot: String,
    @ColumnInfo(name = "bucket_snapshot")
    val bucketSnapshot: BudgetBucket,
    @ColumnInfo(name = "sort_order_snapshot")
    val sortOrderSnapshot: Int,
    @ColumnInfo(name = "advance_policy_snapshot")
    val advancePolicySnapshot: AdvancePolicy,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
