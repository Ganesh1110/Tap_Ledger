package com.tapledger.core.model

enum class AccountType {
    BANK,
    CREDIT_CARD,
    CASH
}

enum class BudgetBucket {
    NEEDS,
    WANTS,
    SAVINGS,
    UNALLOCATED
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
    INTEREST,
    ADJUSTMENT
}

enum class TransactionStatus {
    PENDING,     // Financial event occurred, budget classification incomplete; AFFECTS account balance!
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

enum class TransferDirection {
    OUT, // Money leaving account
    IN   // Money entering account
}

enum class StatementImportStatus {
    IMPORTED,
    RECONCILED,
    CANCELLED
}

enum class StatementMatchStatus {
    MATCHED,
    MISSING_IN_APP,
    MISSING_IN_STATEMENT,
    AMOUNT_MISMATCH,
    DATE_MISMATCH,
    DUPLICATE_CANDIDATE,
    RESOLVED
}

enum class AuditAction {
    CREATED,
    UPDATED,
    CATEGORIZED,
    SPLIT,
    TRANSFER_LINKED,
    REFUND_LINKED,
    VOIDED,
    RESTORED
}
