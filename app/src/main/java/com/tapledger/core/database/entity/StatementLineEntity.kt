package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.StatementMatchStatus

@Entity(
    tableName = "statement_lines",
    foreignKeys = [
        ForeignKey(
            entity = StatementImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["matched_transaction_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["import_id"]),
        Index(value = ["matched_transaction_id"]),
        Index(value = ["match_status"])
    ]
)
data class StatementLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "import_id")
    val importId: Long,
    @ColumnInfo(name = "transaction_date")
    val transactionDate: Long,
    @ColumnInfo(name = "value_date")
    val valueDate: Long? = null,
    val description: String,
    val reference: String? = null,
    @ColumnInfo(name = "debit_paise")
    val debitPaise: Long = 0L,
    @ColumnInfo(name = "credit_paise")
    val creditPaise: Long = 0L,
    @ColumnInfo(name = "balance_after_paise")
    val balanceAfterPaise: Long? = null,
    @ColumnInfo(name = "matched_transaction_id")
    val matchedTransactionId: Long? = null,
    @ColumnInfo(name = "match_status")
    val matchStatus: StatementMatchStatus = StatementMatchStatus.MISSING_IN_APP
)
