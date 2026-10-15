package com.tapledger.core.database.converter

import androidx.room.TypeConverter
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.AuditAction
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.CheckpointType
import com.tapledger.core.model.MonthCloseReconciliationStatus
import com.tapledger.core.model.StatementImportStatus
import com.tapledger.core.model.StatementMatchStatus
import com.tapledger.core.model.TransactionDirection
import com.tapledger.core.model.TransactionKind
import com.tapledger.core.model.TransactionSource
import com.tapledger.core.model.TransactionStatus
import com.tapledger.core.model.TransferDirection

class Converters {

    @TypeConverter
    fun fromTransactionDirection(value: TransactionDirection?): String? = value?.name

    @TypeConverter
    fun toTransactionDirection(value: String?): TransactionDirection? = value?.let { TransactionDirection.valueOf(it) }

    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = value?.let { AccountType.valueOf(it) }

    @TypeConverter
    fun fromBudgetBucket(value: BudgetBucket?): String? = value?.name

    @TypeConverter
    fun toBudgetBucket(value: String?): BudgetBucket? = value?.let { BudgetBucket.valueOf(it) }

    @TypeConverter
    fun fromAdvancePolicy(value: AdvancePolicy?): String? = value?.name

    @TypeConverter
    fun toAdvancePolicy(value: String?): AdvancePolicy? = value?.let { AdvancePolicy.valueOf(it) }

    @TypeConverter
    fun fromAdvanceDirection(value: AdvanceDirection?): String? = value?.name

    @TypeConverter
    fun toAdvanceDirection(value: String?): AdvanceDirection? = value?.let { AdvanceDirection.valueOf(it) }

    @TypeConverter
    fun fromBudgetPeriodStatus(value: BudgetPeriodStatus?): String? = value?.name

    @TypeConverter
    fun toBudgetPeriodStatus(value: String?): BudgetPeriodStatus? = value?.let { BudgetPeriodStatus.valueOf(it) }

    @TypeConverter
    fun fromTransactionKind(value: TransactionKind?): String? = value?.name

    @TypeConverter
    fun toTransactionKind(value: String?): TransactionKind? = value?.let { TransactionKind.valueOf(it) }

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus?): String? = value?.name

    @TypeConverter
    fun toTransactionStatus(value: String?): TransactionStatus? = value?.let { TransactionStatus.valueOf(it) }

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource?): String? = value?.name

    @TypeConverter
    fun toTransactionSource(value: String?): TransactionSource? = value?.let { TransactionSource.valueOf(it) }

    @TypeConverter
    fun fromStatementImportStatus(value: StatementImportStatus?): String? = value?.name

    @TypeConverter
    fun toStatementImportStatus(value: String?): StatementImportStatus? = value?.let { StatementImportStatus.valueOf(it) }

    @TypeConverter
    fun fromStatementMatchStatus(value: StatementMatchStatus?): String? = value?.name

    @TypeConverter
    fun toStatementMatchStatus(value: String?): StatementMatchStatus? = value?.let { StatementMatchStatus.valueOf(it) }

    @TypeConverter
    fun fromAuditAction(value: AuditAction?): String? = value?.name

    @TypeConverter
    fun toAuditAction(value: String?): AuditAction? = value?.let { AuditAction.valueOf(it) }

    @TypeConverter
    fun fromCheckpointType(value: CheckpointType?): String? = value?.name

    @TypeConverter
    fun toCheckpointType(value: String?): CheckpointType? = value?.let { CheckpointType.valueOf(it) }

    @TypeConverter
    fun fromMonthCloseReconciliationStatus(value: MonthCloseReconciliationStatus?): String? = value?.name

    @TypeConverter
    fun toMonthCloseReconciliationStatus(value: String?): MonthCloseReconciliationStatus? = value?.let { MonthCloseReconciliationStatus.valueOf(it) }

    @TypeConverter
    fun fromSavingsGoalStatus(value: com.tapledger.core.model.SavingsGoalStatus?): String? = value?.name

    @TypeConverter
    fun toSavingsGoalStatus(value: String?): com.tapledger.core.model.SavingsGoalStatus? = value?.let { com.tapledger.core.model.SavingsGoalStatus.valueOf(it) }

    @TypeConverter
    fun fromSavingsContributionSource(value: com.tapledger.core.model.SavingsContributionSource?): String? = value?.name

    @TypeConverter
    fun toSavingsContributionSource(value: String?): com.tapledger.core.model.SavingsContributionSource? = value?.let { com.tapledger.core.model.SavingsContributionSource.valueOf(it) }

    @TypeConverter
    fun fromRecurringCommitmentType(value: com.tapledger.core.model.RecurringCommitmentType?): String? = value?.name

    @TypeConverter
    fun toRecurringCommitmentType(value: String?): com.tapledger.core.model.RecurringCommitmentType? = value?.let { com.tapledger.core.model.RecurringCommitmentType.valueOf(it) }

    @TypeConverter
    fun fromCommitmentPaymentStatus(value: com.tapledger.core.model.CommitmentPaymentStatus?): String? = value?.name

    @TypeConverter
    fun toCommitmentPaymentStatus(value: String?): com.tapledger.core.model.CommitmentPaymentStatus? = value?.let { com.tapledger.core.model.CommitmentPaymentStatus.valueOf(it) }

    @TypeConverter
    fun fromCompoundingFrequency(value: com.tapledger.core.model.CompoundingFrequency?): String? = value?.name

    @TypeConverter
    fun toCompoundingFrequency(value: String?): com.tapledger.core.model.CompoundingFrequency? = value?.let { com.tapledger.core.model.CompoundingFrequency.valueOf(it) }
}

