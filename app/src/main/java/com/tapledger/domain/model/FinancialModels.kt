package com.tapledger.domain.model

import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.BudgetBucket

data class AccountBalanceSummary(
    val account: AccountEntity,
    val openingBalancePaise: Long,
    val totalIncomingPaise: Long,
    val totalOutgoingPaise: Long,
    val currentBalancePaise: Long,
    val creditCardUtilizationPercent: Int = 0
)

data class NetWorthSummary(
    val totalAssetsPaise: Long,
    val totalLiabilitiesPaise: Long,
    val netWorthPaise: Long
)

data class BudgetCategorySummary(
    val allocation: BudgetAllocationEntity,
    val baseBudgetPaise: Long,
    val adjustedBudgetPaise: Long,
    val expensePaise: Long,
    val refundPaise: Long,
    val netSpentPaise: Long,
    val remainingPaise: Long,
    val percentageUsed: Int, // 0..100+
    val isWarning: Boolean,  // >= 80%
    val isExceeded: Boolean  // > 100%
)

data class BudgetPeriodSummary(
    val period: BudgetPeriodEntity,
    val totalIncomePaise: Long,
    val totalBudgetedPaise: Long,
    val unallocatedBudgetPaise: Long,
    val totalSpentPaise: Long,
    val totalRemainingPaise: Long,
    val bucketTotals: Map<BudgetBucket, Long>,
    val bucketSpent: Map<BudgetBucket, Long>,
    val categorySummaries: List<BudgetCategorySummary>
)

enum class IntegritySeverity {
    WARNING,
    ERROR,
    CRITICAL
}

data class IntegrityIssue(
    val ruleId: String,
    val message: String,
    val entityType: String,
    val entityId: String,
    val severity: IntegritySeverity
)
