package com.tapledger.domain.model

enum class MilestoneType {
    FIRST_GOAL,
    FIRST_DEPOSIT,
    EMERGENCY_FUND_STARTER, // 3.0 months
    EMERGENCY_FUND_HERO,    // 6.0 months
    SAVED_10K,              // ₹10,000
    SAVED_50K,              // ₹50,000
    SAVED_100K,             // ₹1,00,000
    GOAL_ACHIEVER           // at least 1 completed goal
}

data class SavingsMilestoneBadge(
    val type: MilestoneType,
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val progressBp: Int // 0 to 10,000 basis points
)
