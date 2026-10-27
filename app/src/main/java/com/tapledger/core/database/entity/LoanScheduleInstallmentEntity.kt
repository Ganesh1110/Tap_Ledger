package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_schedule_installments",
    foreignKeys = [
        ForeignKey(
            entity = LoanScheduleVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["schedule_version_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["schedule_version_id"])
    ]
)
data class LoanScheduleInstallmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "schedule_version_id")
    val scheduleVersionId: Long,
    @ColumnInfo(name = "month_index")
    val monthIndex: Int,
    @ColumnInfo(name = "due_date_ms")
    val dueDateMs: Long,
    @ColumnInfo(name = "opening_principal_paise")
    val openingPrincipalPaise: Long,
    @ColumnInfo(name = "emi_paise")
    val emiPaise: Long,
    @ColumnInfo(name = "interest_paise")
    val interestPaise: Long,
    @ColumnInfo(name = "principal_paid_paise")
    val principalPaidPaise: Long,
    @ColumnInfo(name = "closing_principal_paise")
    val closingPrincipalPaise: Long
)
