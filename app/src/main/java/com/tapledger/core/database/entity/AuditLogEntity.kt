package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.AuditAction

@Entity(
    tableName = "transaction_audit_log",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["transaction_id"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    val action: AuditAction,
    @ColumnInfo(name = "old_value_json")
    val oldValuesJson: String? = null,
    @ColumnInfo(name = "new_value_json")
    val newValuesJson: String? = null,
    val reason: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
