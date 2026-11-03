package com.tapledger.core.model

/**
 * Operating mode of the application (Flow.md §18, D012).
 * Application operates in unified [MANAGER] mode.
 */
enum class AppMode {
    MANAGER,
    @Deprecated("Savings mode removed in favor of single unified mode")
    SAVING;

    companion object {
        fun fromNameOrDefault(name: String?): AppMode {
            return MANAGER
        }
    }
}
