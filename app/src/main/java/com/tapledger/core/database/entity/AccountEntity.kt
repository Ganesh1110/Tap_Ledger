package com.tapledger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tapledger.core.model.AccountType

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["identifier_last4"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    @ColumnInfo(name = "identifier_last4")
    val identifierLast4: String?,
    @ColumnInfo(name = "opening_balance_paise")
    val openingBalancePaise: Long,
    @ColumnInfo(name = "opening_balance_date")
    val openingBalanceDate: Long,
    @ColumnInfo(name = "credit_limit_paise")
    val creditLimitPaise: Long? = null,
    @ColumnInfo(name = "statement_day")
    val statementDay: Int? = null,
    @ColumnInfo(name = "due_day")
    val dueDay: Int? = null,
    @ColumnInfo(name = "is_savings")
    val isSavings: Boolean = false,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
