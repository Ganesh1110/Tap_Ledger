package com.tapledger.core.model

/**
 * Operating mode of the application (Flow.md §18, D012).
 * - [MANAGER]: Full financial access (accounts, budget periods, transactions, backup/restore).
 * - [SAVING]: Dedicated savings & goals focus (goals, contributions, emergency fund, commitments, fixed deposits).
 */
enum class AppMode {
    MANAGER,
    SAVING;

    companion object {
        fun fromNameOrDefault(name: String?): AppMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MANAGER
        }
    }
}
