package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "default_bucket")
    val defaultBucket: BudgetBucket,
    @ColumnInfo(name = "default_sort_order")
    val defaultSortOrder: Int,
    @ColumnInfo(name = "default_advance_policy")
    val defaultAdvancePolicy: AdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
