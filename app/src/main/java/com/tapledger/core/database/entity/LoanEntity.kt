package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loans",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_bank_account_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["linked_bank_account_id"])
    ]
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "principal_paise")
    val principalPaise: Long,
    @ColumnInfo(name = "annual_interest_rate_bp")
    val annualInterestRateBp: Int,
    @ColumnInfo(name = "tenure_months")
    val tenureMonths: Int,
    @ColumnInfo(name = "emi_day")
    val emiDay: Int,
    @ColumnInfo(name = "emi_amount_paise")
    val emiAmountPaise: Long,
    @ColumnInfo(name = "linked_bank_account_id")
    val linkedBankAccountId: Long? = null,
    @ColumnInfo(name = "start_date_ms")
    val startDateMs: Long,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
