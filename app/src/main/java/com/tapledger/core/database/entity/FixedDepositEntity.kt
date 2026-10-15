package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.CompoundingFrequency

@Entity(
    tableName = "fixed_deposits",
    foreignKeys = [
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["goal_id"])
    ]
)
data class FixedDepositEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "principal_paise")
    val principalPaise: Long,
    @ColumnInfo(name = "rate_bp")
    val rateBp: Int,
    @ColumnInfo(name = "start_ms")
    val startMs: Long,
    @ColumnInfo(name = "maturity_ms")
    val maturityMs: Long,
    @ColumnInfo(name = "compounding")
    val compounding: CompoundingFrequency,
    @ColumnInfo(name = "goal_id")
    val goalId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
