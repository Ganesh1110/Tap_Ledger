package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.SavingsGoalStatus

@Entity(
    tableName = "savings_goals",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_account_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["linked_account_id"]),
        Index(value = ["status"])
    ]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "target_paise")
    val targetPaise: Long,
    @ColumnInfo(name = "deadline_epoch_ms")
    val deadlineEpochMs: Long? = null,
    @ColumnInfo(name = "priority")
    val priority: Int = 1,
    @ColumnInfo(name = "status")
    val status: SavingsGoalStatus = SavingsGoalStatus.ACTIVE,
    @ColumnInfo(name = "linked_account_id")
    val linkedAccountId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
