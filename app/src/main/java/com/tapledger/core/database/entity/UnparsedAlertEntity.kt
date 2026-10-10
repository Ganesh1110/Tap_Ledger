package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unparsed_alerts",
    indices = [
        Index(value = ["occurred_at"])
    ]
)
data class UnparsedAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    val title: String,
    @ColumnInfo(name = "raw_text")
    val rawText: String,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "error_reason")
    val errorReason: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
