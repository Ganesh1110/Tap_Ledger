package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.StatementImportStatus

@Entity(
    tableName = "statement_imports",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["account_id"])
    ]
)
data class StatementImportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "period_start")
    val periodStart: Long,
    @ColumnInfo(name = "period_end")
    val periodEnd: Long,
    @ColumnInfo(name = "source_uri_reference")
    val sourceUriReference: String? = null,
    val format: String,
    @ColumnInfo(name = "opening_balance_paise")
    val openingBalancePaise: Long,
    @ColumnInfo(name = "closing_balance_paise")
    val closingBalancePaise: Long,
    val status: StatementImportStatus = StatementImportStatus.IMPORTED,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
