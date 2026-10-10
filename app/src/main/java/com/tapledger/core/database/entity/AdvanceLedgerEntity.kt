package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.AdvanceDirection

@Entity(
    tableName = "advance_ledger",
    foreignKeys = [
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_period_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = BudgetPeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["applied_to_period_id"],
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
        Index(value = ["source_period_id"]),
        Index(value = ["applied_to_period_id"]),
        Index(value = ["category_id"])
    ]
)
data class AdvanceLedgerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "source_period_id")
    val sourcePeriodId: Long,
    @ColumnInfo(name = "applied_to_period_id")
    val appliedToPeriodId: Long? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    val direction: AdvanceDirection,
    val reason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
