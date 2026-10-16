package com.tapledger.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SavingsGuideConfig(
    val spendingAccountId: Long? = null,
    val savingsAccountId: Long? = null,
    val bufferPaise: Long = 0L,
    val paydayThresholdPct: Int = 50,
    val sweepWindowDays: Int = 3,
    val minSweepPaise: Long = 50000L, // ₹500
    val preferredPayApp: String? = null,
    val notificationsEnabled: Boolean = true
)

@Singleton
class SavingsGuidePreferences(
    private val context: Context? = null,
    private val customPrefs: SharedPreferences? = null
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context = context, customPrefs = null)

    // Secondary constructor for testing with in-memory or fake SharedPreferences
    constructor(prefs: SharedPreferences) : this(context = null, customPrefs = prefs)

    companion object {
        const val PREFS_NAME = "tapledger_savings_guide_prefs"
        private const val KEY_SPENDING_ACCOUNT_ID = "spending_account_id"
        private const val KEY_SAVINGS_ACCOUNT_ID = "savings_account_id"
        private const val KEY_BUFFER_PAISE = "buffer_paise"
        private const val KEY_PAYDAY_THRESHOLD_PCT = "payday_threshold_pct"
        private const val KEY_SWEEP_WINDOW_DAYS = "sweep_window_days"
        private const val KEY_MIN_SWEEP_PAISE = "min_sweep_paise"
        private const val KEY_PREFERRED_PAY_APP = "preferred_pay_app"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

        const val DEFAULT_BUFFER_PAISE = 0L
        const val DEFAULT_PAYDAY_THRESHOLD_PCT = 50
        const val DEFAULT_SWEEP_WINDOW_DAYS = 3
        const val DEFAULT_MIN_SWEEP_PAISE = 50000L // ₹500
    }

    private val prefs: SharedPreferences by lazy {
        customPrefs ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: throw IllegalStateException("SharedPreferences not available")
    }

    private val _configFlow = MutableStateFlow(readConfig())
    val configFlow: StateFlow<SavingsGuideConfig> = _configFlow.asStateFlow()

    private fun readConfig(): SavingsGuideConfig {
        val spendingId = if (prefs.contains(KEY_SPENDING_ACCOUNT_ID)) {
            val v = prefs.getLong(KEY_SPENDING_ACCOUNT_ID, -1L)
            if (v != -1L) v else null
        } else null

        val savingsId = if (prefs.contains(KEY_SAVINGS_ACCOUNT_ID)) {
            val v = prefs.getLong(KEY_SAVINGS_ACCOUNT_ID, -1L)
            if (v != -1L) v else null
        } else null

        val buffer = prefs.getLong(KEY_BUFFER_PAISE, DEFAULT_BUFFER_PAISE)
        val threshold = prefs.getInt(KEY_PAYDAY_THRESHOLD_PCT, DEFAULT_PAYDAY_THRESHOLD_PCT)
        val window = prefs.getInt(KEY_SWEEP_WINDOW_DAYS, DEFAULT_SWEEP_WINDOW_DAYS)
        val minSweep = prefs.getLong(KEY_MIN_SWEEP_PAISE, DEFAULT_MIN_SWEEP_PAISE)
        val payApp = prefs.getString(KEY_PREFERRED_PAY_APP, null)
        val notifs = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

        return SavingsGuideConfig(
            spendingAccountId = spendingId,
            savingsAccountId = savingsId,
            bufferPaise = buffer,
            paydayThresholdPct = threshold,
            sweepWindowDays = window,
            minSweepPaise = minSweep,
            preferredPayApp = payApp,
            notificationsEnabled = notifs
        )
    }

    fun getConfig(): SavingsGuideConfig = _configFlow.value

    fun updateConfig(config: SavingsGuideConfig) {
        prefs.edit().apply {
            if (config.spendingAccountId != null) {
                putLong(KEY_SPENDING_ACCOUNT_ID, config.spendingAccountId)
            } else {
                remove(KEY_SPENDING_ACCOUNT_ID)
            }

            if (config.savingsAccountId != null) {
                putLong(KEY_SAVINGS_ACCOUNT_ID, config.savingsAccountId)
            } else {
                remove(KEY_SAVINGS_ACCOUNT_ID)
            }

            putLong(KEY_BUFFER_PAISE, config.bufferPaise)
            putInt(KEY_PAYDAY_THRESHOLD_PCT, config.paydayThresholdPct)
            putInt(KEY_SWEEP_WINDOW_DAYS, config.sweepWindowDays)
            putLong(KEY_MIN_SWEEP_PAISE, config.minSweepPaise)

            if (config.preferredPayApp != null) {
                putString(KEY_PREFERRED_PAY_APP, config.preferredPayApp)
            } else {
                remove(KEY_PREFERRED_PAY_APP)
            }

            putBoolean(KEY_NOTIFICATIONS_ENABLED, config.notificationsEnabled)
            apply()
        }
        _configFlow.value = config
    }

    fun setSpendingAccountId(accountId: Long?) {
        updateConfig(getConfig().copy(spendingAccountId = accountId))
    }

    fun setSavingsAccountId(accountId: Long?) {
        updateConfig(getConfig().copy(savingsAccountId = accountId))
    }

    fun setBufferPaise(bufferPaise: Long) {
        updateConfig(getConfig().copy(bufferPaise = bufferPaise))
    }

    fun setPaydayThresholdPct(pct: Int) {
        updateConfig(getConfig().copy(paydayThresholdPct = pct))
    }

    fun setSweepWindowDays(days: Int) {
        updateConfig(getConfig().copy(sweepWindowDays = days))
    }

    fun setMinSweepPaise(minSweepPaise: Long) {
        updateConfig(getConfig().copy(minSweepPaise = minSweepPaise))
    }

    fun setPreferredPayApp(payApp: String?) {
        updateConfig(getConfig().copy(preferredPayApp = payApp))
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        updateConfig(getConfig().copy(notificationsEnabled = enabled))
    }
}
