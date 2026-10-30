package com.tapledger.core.preferences

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.security.FakeSharedPreferences
import org.junit.Before
import org.junit.Test

class LoanPreferencesTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var loanPreferences: LoanPreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        loanPreferences = LoanPreferences(prefs = prefs)
    }

    @Test
    fun testDefaultConfiguration() {
        val config = loanPreferences.getConfig()
        assertThat(config.loanTrackerEnabled).isTrue()
        assertThat(config.includeLoansInNetWorth).isTrue()
        assertThat(config.hideClosedLoans).isTrue()
        assertThat(config.autoDetectEmiSms).isTrue()
        assertThat(config.emiBalanceGuardEnabled).isTrue()
        assertThat(config.emiBalanceGuardDaysPrior).isEqualTo(2)
    }

    @Test
    fun testToggleLoanTrackerEnabled() {
        loanPreferences.setLoanTrackerEnabled(false)
        assertThat(loanPreferences.getConfig().loanTrackerEnabled).isFalse()

        loanPreferences.setLoanTrackerEnabled(true)
        assertThat(loanPreferences.getConfig().loanTrackerEnabled).isTrue()
    }

    @Test
    fun testToggleNetWorthInclusion() {
        loanPreferences.setIncludeLoansInNetWorth(false)
        assertThat(loanPreferences.getConfig().includeLoansInNetWorth).isFalse()

        loanPreferences.setIncludeLoansInNetWorth(true)
        assertThat(loanPreferences.getConfig().includeLoansInNetWorth).isTrue()
    }

    @Test
    fun testToggleHideClosedLoans() {
        loanPreferences.setHideClosedLoans(false)
        assertThat(loanPreferences.getConfig().hideClosedLoans).isFalse()

        loanPreferences.setHideClosedLoans(true)
        assertThat(loanPreferences.getConfig().hideClosedLoans).isTrue()
    }

    @Test
    fun testToggleAutoDetectEmiSms() {
        loanPreferences.setAutoDetectEmiSms(false)
        assertThat(loanPreferences.getConfig().autoDetectEmiSms).isFalse()

        loanPreferences.setAutoDetectEmiSms(true)
        assertThat(loanPreferences.getConfig().autoDetectEmiSms).isTrue()
    }

    @Test
    fun testToggleEmiBalanceGuardAndDaysPrior() {
        loanPreferences.setEmiBalanceGuardEnabled(false)
        assertThat(loanPreferences.getConfig().emiBalanceGuardEnabled).isFalse()

        loanPreferences.setEmiBalanceGuardDaysPrior(1)
        assertThat(loanPreferences.getConfig().emiBalanceGuardDaysPrior).isEqualTo(1)

        loanPreferences.setEmiBalanceGuardDaysPrior(2)
        assertThat(loanPreferences.getConfig().emiBalanceGuardDaysPrior).isEqualTo(2)
    }
}
