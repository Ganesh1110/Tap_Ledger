package com.tapledger.domain.onboarding

import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket

/**
 * Draft models and validators for the first-run onboarding stepper (Flow.md §15.1 O1–O6).
 */
data class OnboardingDraft(
    val monthlyIncomePaise: Long = 0L,
    val asOfDateEpochMillis: Long = 0L,
    val bankAccounts: List<StagedAccount> = emptyList(),
    val cashBalancePaise: Long = 0L,
    val creditCards: List<StagedCreditCard> = emptyList(),
    val categoryAllocations: Map<Long, Long> = emptyMap() // categoryId -> allocatedPaise
)

data class StagedAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val type: AccountType, // BANK or WALLET
    val identifierLast4: String? = null,
    val balancePaise: Long = 0L,
    val isSavings: Boolean = false
)

data class StagedCreditCard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val identifierLast4: String? = null,
    val outstandingOwedPaise: Long = 0L, // Entered as positive number >= 0
    val creditLimitPaise: Long? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null
)

object OnboardingValidator {
    fun validateIncome(incomePaise: Long): String? {
        if (incomePaise <= 0L) return "Monthly income must be strictly greater than ₹0"
        return null
    }

    fun validateAccountName(name: String, existingNames: List<String>): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Account name is required"
        if (trimmed.length > 40) return "Account name must be at most 40 characters"
        if (existingNames.any { it.equals(trimmed, ignoreCase = true) }) {
            return "Account name must be unique"
        }
        return null
    }

    fun validateLast4(last4: String?): String? {
        if (last4.isNullOrEmpty()) return null
        val trimmed = last4.trim()
        if (trimmed.length != 4 || !trimmed.all { it in '0'..'9' }) {
            return "Last 4 digits must be exactly 4 numeric digits"
        }
        return null
    }

    fun validateDayOfMonth(day: Int?): String? {
        if (day == null) return null
        if (day !in 1..31) return "Day must be between 1 and 31"
        return null
    }
}

object OnboardingDistribution {
    /**
     * O4: Calculates the 50/30/20 target split using integer math (Flow.md §8.1).
     * Needs = 50%, Wants = 30%, Savings = remainder.
     */
    fun calculateBucketTargets(incomePaise: Long): Triple<Long, Long, Long> {
        if (incomePaise <= 0L) return Triple(0L, 0L, 0L)
        val needs = Math.multiplyExact(incomePaise, 50L) / 100L
        val wants = Math.multiplyExact(incomePaise, 30L) / 100L
        val savings = incomePaise - needs - wants
        return Triple(needs, wants, savings)
    }

    /**
     * O4: Within each bucket, split the bucket target equally across that bucket's active categories
     * using integer division, with remainder paise allocated to the category with lowest default_sort_order.
     * Σ per bucket must equal the bucket target exactly (Flow.md §15.1 O4).
     */
    fun distributeBucket(bucketTarget: Long, categories: List<CategoryEntity>): Map<Long, Long> {
        if (categories.isEmpty() || bucketTarget <= 0L) return emptyMap()
        val sorted = categories.sortedBy { it.defaultSortOrder }
        val n = sorted.size
        val base = bucketTarget / n
        val rem = bucketTarget % n

        val result = mutableMapOf<Long, Long>()
        sorted.forEachIndexed { index, cat ->
            val amount = if (index == 0) base + rem else base
            result[cat.id] = amount
        }
        return result
    }
}
