package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_schedule_versions",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loan_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["trigger_transaction_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["loan_id"]),
        Index(value = ["trigger_transaction_id"])
    ]
)
data class LoanScheduleVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "loan_id")
    val loanId: Long,
    @ColumnInfo(name = "version_number")
    val versionNumber: Int,
    @ColumnInfo(name = "trigger_type")
    val triggerType: String, // ORIGINAL, PART_PREPAYMENT_REDUCE_TENURE, PART_PREPAYMENT_REDUCE_EMI, FORECLOSURE
    @ColumnInfo(name = "trigger_transaction_id")
    val triggerTransactionId: Long? = null,
    @ColumnInfo(name = "effective_date_ms")
    val effectiveDateMs: Long,
    @ColumnInfo(name = "principal_paise")
    val principalPaise: Long,
    @ColumnInfo(name = "emi_paise")
    val emiPaise: Long,
    @ColumnInfo(name = "tenure_months")
    val tenureMonths: Int,
    @ColumnInfo(name = "annual_interest_rate_bp")
    val annualInterestRateBp: Int,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
