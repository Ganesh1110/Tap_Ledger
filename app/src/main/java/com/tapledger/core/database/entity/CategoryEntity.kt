package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["sweep_target_category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["system_key"], unique = true),
        Index(value = ["sweep_target_category_id"])
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "system_key")
    val systemKey: String? = null,
    @ColumnInfo(name = "default_bucket")
    val defaultBucket: BudgetBucket,
    @ColumnInfo(name = "default_sort_order")
    val defaultSortOrder: Int,
    @ColumnInfo(name = "default_advance_policy")
    val defaultAdvancePolicy: AdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
    @ColumnInfo(name = "sweep_target_category_id")
    val sweepTargetCategoryId: Long? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
