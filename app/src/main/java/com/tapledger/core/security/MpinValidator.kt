package com.tapledger.core.security

sealed interface MpinValidationResult {
    data object Valid : MpinValidationResult
    data class Invalid(val message: String) : MpinValidationResult
}

/**
 * Validates MPIN length and rejects trivially weak or sequential combinations.
 * Flow.md §14.1 & Requirement 2.
 */
object MpinValidator {

    fun validate(mpin: String): MpinValidationResult {
        if (mpin.length !in 4..6) {
            return MpinValidationResult.Invalid("MPIN must be 4 to 6 digits")
        }
        if (!mpin.all { it.isDigit() }) {
            return MpinValidationResult.Invalid("MPIN must contain digits only")
        }

        // 1. All identical digits (e.g., 0000, 1111, 999999)
        if (mpin.all { it == mpin[0] }) {
            return MpinValidationResult.Invalid("Avoid repeated digits (e.g., ${mpin[0].toString().repeat(4)})")
        }

        // 2. Repeating 2-digit pairs (e.g. 1212, 121212)
        if (mpin.length == 4 && mpin.substring(0, 2) == mpin.substring(2, 4)) {
            return MpinValidationResult.Invalid("Avoid repeating patterns (e.g., ${mpin.substring(0, 2).repeat(2)})")
        }
        if (mpin.length == 6 && mpin.substring(0, 2) == mpin.substring(2, 4) && mpin.substring(2, 4) == mpin.substring(4, 6)) {
            return MpinValidationResult.Invalid("Avoid repeating patterns (e.g., ${mpin.substring(0, 2).repeat(3)})")
        }
        if (mpin.length == 6 && mpin.substring(0, 3) == mpin.substring(3, 6)) {
            return MpinValidationResult.Invalid("Avoid repeating patterns (e.g., ${mpin.substring(0, 3).repeat(2)})")
        }

        // 3. Sequential ascending or descending digits (e.g., 1234, 4321, 012345, 987654)
        var isAscending = true
        var isDescending = true
        for (i in 0 until mpin.length - 1) {
            val curr = mpin[i] - '0'
            val next = mpin[i + 1] - '0'
            if (next != curr + 1) isAscending = false
            if (next != curr - 1) isDescending = false
        }

        if (isAscending) {
            return MpinValidationResult.Invalid("Avoid sequential numbers (e.g., 1234)")
        }
        if (isDescending) {
            return MpinValidationResult.Invalid("Avoid sequential numbers (e.g., 4321)")
        }

        return MpinValidationResult.Valid
    }
}
