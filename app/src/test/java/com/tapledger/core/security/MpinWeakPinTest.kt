package com.tapledger.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MpinWeakPinTest {

    @Test
    fun rejectsIdenticalDigits() {
        val weakPins = listOf("0000", "1111", "2222", "9999", "000000", "777777")
        for (pin in weakPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isInstanceOf(MpinValidationResult.Invalid::class.java)
            val invalid = result as MpinValidationResult.Invalid
            assertThat(invalid.message).contains("repeated")
        }
    }

    @Test
    fun rejectsRepeatingPatterns() {
        val repeatingPins = listOf("1212", "3434", "121212", "123123")
        for (pin in repeatingPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isInstanceOf(MpinValidationResult.Invalid::class.java)
            val invalid = result as MpinValidationResult.Invalid
            assertThat(invalid.message).contains("repeating")
        }
    }

    @Test
    fun rejectsSequentialAscending() {
        val ascendingPins = listOf("1234", "2345", "6789", "012345", "345678")
        for (pin in ascendingPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isInstanceOf(MpinValidationResult.Invalid::class.java)
            val invalid = result as MpinValidationResult.Invalid
            assertThat(invalid.message).contains("sequential")
        }
    }

    @Test
    fun rejectsSequentialDescending() {
        val descendingPins = listOf("4321", "5432", "9876", "987654", "543210")
        for (pin in descendingPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isInstanceOf(MpinValidationResult.Invalid::class.java)
            val invalid = result as MpinValidationResult.Invalid
            assertThat(invalid.message).contains("sequential")
        }
    }

    @Test
    fun rejectsInvalidLengthsAndCharacters() {
        val invalidPins = listOf("12", "123", "1234567", "12a4", "abcd", "!@#$")
        for (pin in invalidPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isInstanceOf(MpinValidationResult.Invalid::class.java)
        }
    }

    @Test
    fun acceptsStrongPins() {
        val validPins = listOf("2951", "8402", "7391", "491823", "820519")
        for (pin in validPins) {
            val result = MpinValidator.validate(pin)
            assertThat(result).isEqualTo(MpinValidationResult.Valid)
        }
    }
}
