package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_allocations",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
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
        Index(value = ["transaction_id", "category_id"], unique = true),
        Index(value = ["category_id"])
    ]
)
data class TransactionAllocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long
)
