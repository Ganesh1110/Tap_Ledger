package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unparsed_alerts")
data class UnparsedAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    @ColumnInfo(name = "sender_or_package")
    val senderOrPackage: String,
    @ColumnInfo(name = "raw_redacted_preview")
    val rawRedactedPreview: String,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    val reason: String,
    val status: String = "NEW",
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
