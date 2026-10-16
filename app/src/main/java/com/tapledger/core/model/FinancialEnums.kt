package com.tapledger.core.model

enum class AccountType {
    BANK,
    CREDIT_CARD,
    CASH,
    INVESTMENT,
    LOAN,
    WALLET
}

enum class BudgetBucket {
    NEEDS,
    WANTS,
    SAVINGS
}

enum class AdvancePolicy {
    CARRY_TO_ADVANCE,
    SWEEP_TO_SAVINGS,
    EXPIRE
}

enum class AdvanceDirection {
    POSITIVE, // Unused budget carried forward
    NEGATIVE  // Overspending to be covered
}

enum class BudgetPeriodStatus {
    OPEN,
    CLOSED
}

enum class TransactionKind {
    EXPENSE,
    INCOME,
    REFUND,
    TRANSFER,
    FEE,
    INTEREST_CHARGED,
    INTEREST_EARNED,
    ADJUSTMENT
}

enum class TransactionDirection {
    OUT, // Money leaving account
    IN   // Money entering account
}

typealias TransferDirection = TransactionDirection

enum class TransactionStatus {
    PENDING,     // Financial event occurred, budget classification incomplete; AFFECTS account balance!
    CONFIRMED,   // Income and standard paired transfers; AFFECTS account balance
    CATEGORIZED, // Fully classified with allocations summing to total; AFFECTS account balance
    IGNORED,     // Duplicate notification, OTP, promotional, non-financial; EXCLUDED from balance
    VOIDED       // Canceled/reversed manually; EXCLUDED from balance
}

enum class TransactionSource {
    SMS,
    NOTIFICATION,
    STATEMENT,
    MANUAL
}

enum class CheckpointType {
    AVAILABLE_BALANCE,
    AVAILABLE_CREDIT_LIMIT,
    TOTAL_OUTSTANDING
}

enum class MonthCloseReconciliationStatus {
    RECONCILED,
    RECONCILED_WITH_OVERRIDE,
    UNRECONCILED
}

enum class StatementImportStatus {
    IMPORTED,
    RECONCILED,
    ARITHMETIC_ERROR,
    CANCELLED
}

enum class StatementMatchStatus {
    MATCHED,
    MATCHED_WITH_DISCREPANCY,
    MISSING_IN_APP,
    MISSING_IN_STATEMENT,
    TIMING_DIFFERENCE,
    RESOLVED
}

enum class AuditAction {
    CREATED,
    UPDATED,
    CATEGORIZED,
    UNCATEGORIZED,
    SPLIT,
    TRANSFER_LINKED,
    REFUND_LINKED,
    VOIDED,
    IGNORED,
    RESTORED,
    DUPLICATE_DROPPED,
    PERIOD_CLOSED,
    PERIOD_REOPENED,
    DB_RESTORED,
    PLAN_ADOPTED,
    PLAN_CREATED,
    PLAN_MOVED,
    PLAN_DISMISSED
}

enum class SavingsGoalStatus {
    ACTIVE,
    PAUSED,
    DONE,
    ARCHIVED
}

enum class SavingsContributionSource {
    MANUAL,
    TRANSACTION,
    MONTH_CLOSE,
    RULE
}

enum class GoalTrackStatus {
    ON_TRACK,
    BEHIND,
    DONE,
    OVERDUE
}

enum class RecurringCommitmentType {
    SIP,
    RD,
    CHIT,
    GOLD
}

enum class CommitmentPaymentStatus {
    PENDING,
    PAID,
    SKIPPED
}

enum class CompoundingFrequency {
    SIMPLE,
    QUARTERLY,
    MONTHLY,
    ANNUAL
}

enum class TransferPlanKind {
    PAYDAY,
    SWEEP,
    MANUAL
}

enum class TransferPlanStatus {
    SUGGESTED,
    MARKED_MOVED,
    VERIFIED,
    DISMISSED,
    EXPIRED
}


