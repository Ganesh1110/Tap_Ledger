package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.RecurringCommitmentType

@Entity(
    tableName = "recurring_commitments",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["goal_id"])
    ]
)
data class RecurringCommitmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "type")
    val type: RecurringCommitmentType,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,
    @ColumnInfo(name = "due_day")
    val dueDay: Int,
    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,
    @ColumnInfo(name = "goal_id")
    val goalId: Long? = null,
    @ColumnInfo(name = "active")
    val active: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
