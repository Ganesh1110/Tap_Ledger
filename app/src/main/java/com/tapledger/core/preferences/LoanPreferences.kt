package com.tapledger.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LoanPreferencesConfig(
    val loanTrackerEnabled: Boolean = true,
    val includeLoansInNetWorth: Boolean = true,
    val hideClosedLoans: Boolean = true,
    val autoDetectEmiSms: Boolean = true,
    val emiBalanceGuardEnabled: Boolean = true,
    val emiBalanceGuardDaysPrior: Int = 2
)

@Singleton
class LoanPreferences(
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context = context, customPrefs = null)

    constructor(prefs: SharedPreferences) : this(context = null, customPrefs = prefs)

    companion object {
        const val PREFS_NAME = "tapledger_loan_prefs"
        private const val KEY_LOAN_TRACKER_ENABLED = "loan_tracker_enabled"
        private const val KEY_INCLUDE_LOANS_IN_NET_WORTH = "include_loans_in_net_worth"
        private const val KEY_HIDE_CLOSED_LOANS = "hide_closed_loans"
        private const val KEY_AUTO_DETECT_EMI_SMS = "auto_detect_emi_sms"
        private const val KEY_EMI_BALANCE_GUARD_ENABLED = "emi_balance_guard_enabled"
        private const val KEY_EMI_BALANCE_GUARD_DAYS_PRIOR = "emi_balance_guard_days_prior"

        const val DEFAULT_LOAN_TRACKER_ENABLED = true
        const val DEFAULT_INCLUDE_LOANS_IN_NET_WORTH = true
        const val DEFAULT_HIDE_CLOSED_LOANS = true
        const val DEFAULT_AUTO_DETECT_EMI_SMS = true
        const val DEFAULT_EMI_BALANCE_GUARD_ENABLED = true
        const val DEFAULT_EMI_BALANCE_GUARD_DAYS_PRIOR = 2
    }

    private val prefs: SharedPreferences by lazy {
        customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("SharedPreferences not available")
    }

    private val _configFlow = MutableStateFlow(readConfig())
    val configFlow: StateFlow<LoanPreferencesConfig> = _configFlow.asStateFlow()

    private fun readConfig(): LoanPreferencesConfig {
        return LoanPreferencesConfig(
            loanTrackerEnabled = prefs.getBoolean(KEY_LOAN_TRACKER_ENABLED, DEFAULT_LOAN_TRACKER_ENABLED),
            includeLoansInNetWorth = prefs.getBoolean(KEY_INCLUDE_LOANS_IN_NET_WORTH, DEFAULT_INCLUDE_LOANS_IN_NET_WORTH),
            hideClosedLoans = prefs.getBoolean(KEY_HIDE_CLOSED_LOANS, DEFAULT_HIDE_CLOSED_LOANS),
            autoDetectEmiSms = prefs.getBoolean(KEY_AUTO_DETECT_EMI_SMS, DEFAULT_AUTO_DETECT_EMI_SMS),
            emiBalanceGuardEnabled = prefs.getBoolean(KEY_EMI_BALANCE_GUARD_ENABLED, DEFAULT_EMI_BALANCE_GUARD_ENABLED),
            emiBalanceGuardDaysPrior = prefs.getInt(KEY_EMI_BALANCE_GUARD_DAYS_PRIOR, DEFAULT_EMI_BALANCE_GUARD_DAYS_PRIOR)
        )
    }

    fun getConfig(): LoanPreferencesConfig = _configFlow.value

    fun setLoanTrackerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOAN_TRACKER_ENABLED, enabled).apply()
        _configFlow.value = readConfig()
    }

    fun setIncludeLoansInNetWorth(include: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_LOANS_IN_NET_WORTH, include).apply()
        _configFlow.value = readConfig()
    }

    fun setHideClosedLoans(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_CLOSED_LOANS, hide).apply()
        _configFlow.value = readConfig()
    }

    fun setAutoDetectEmiSms(autoDetect: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DETECT_EMI_SMS, autoDetect).apply()
        _configFlow.value = readConfig()
    }

    fun setEmiBalanceGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EMI_BALANCE_GUARD_ENABLED, enabled).apply()
        _configFlow.value = readConfig()
    }

    fun setEmiBalanceGuardDaysPrior(days: Int) {
        prefs.edit().putInt(KEY_EMI_BALANCE_GUARD_DAYS_PRIOR, days).apply()
        _configFlow.value = readConfig()
    }
}
