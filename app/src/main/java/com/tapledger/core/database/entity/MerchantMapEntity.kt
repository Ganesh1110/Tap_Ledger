package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "merchant_map",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["merchant_normalized", "category_id"], unique = true),
        Index(value = ["category_id"])
    ]
)
data class MerchantMapEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "merchant_normalized")
    val merchantNormalized: String,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "times_confirmed")
    val timesConfirmed: Int = 0,
    @ColumnInfo(name = "times_overridden")
    val timesOverridden: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long
)
