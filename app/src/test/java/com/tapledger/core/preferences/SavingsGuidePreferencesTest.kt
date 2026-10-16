package com.tapledger.core.preferences

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.security.FakeSharedPreferences
import org.junit.Before
import org.junit.Test

class SavingsGuidePreferencesTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var guidePreferences: SavingsGuidePreferences

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        guidePreferences = SavingsGuidePreferences(prefs = prefs)
    }

    @Test
    fun testDefaultConfiguration() {
        val config = guidePreferences.getConfig()
        assertThat(config.spendingAccountId).isNull()
        assertThat(config.savingsAccountId).isNull()
        assertThat(config.bufferPaise).isEqualTo(0L)
        assertThat(config.paydayThresholdPct).isEqualTo(50)
        assertThat(config.sweepWindowDays).isEqualTo(3)
        assertThat(config.minSweepPaise).isEqualTo(50000L) // ₹500
        assertThat(config.preferredPayApp).isNull()
        assertThat(config.notificationsEnabled).isTrue()
    }

    @Test
    fun testUpdatingAccountPairings() {
        guidePreferences.setSpendingAccountId(101L)
        guidePreferences.setSavingsAccountId(202L)

        val config = guidePreferences.getConfig()
        assertThat(config.spendingAccountId).isEqualTo(101L)
        assertThat(config.savingsAccountId).isEqualTo(202L)

        // Reset to null
        guidePreferences.setSpendingAccountId(null)
        assertThat(guidePreferences.getConfig().spendingAccountId).isNull()
    }

    @Test
    fun testUpdatingThresholdsAndBuffer() {
        guidePreferences.setBufferPaise(2_000_00L) // ₹2,000
        guidePreferences.setPaydayThresholdPct(20)
        guidePreferences.setSweepWindowDays(5)
        guidePreferences.setMinSweepPaise(100_000L) // ₹1,000

        val config = guidePreferences.getConfig()
        assertThat(config.bufferPaise).isEqualTo(2_000_00L)
        assertThat(config.paydayThresholdPct).isEqualTo(20)
        assertThat(config.sweepWindowDays).isEqualTo(5)
        assertThat(config.minSweepPaise).isEqualTo(100_000L)
    }

    @Test
    fun testUpdatingPreferredAppAndNotifications() {
        guidePreferences.setPreferredPayApp("com.google.android.apps.nbu.paisa.user")
        guidePreferences.setNotificationsEnabled(false)

        val config = guidePreferences.getConfig()
        assertThat(config.preferredPayApp).isEqualTo("com.google.android.apps.nbu.paisa.user")
        assertThat(config.notificationsEnabled).isFalse()
    }

    @Test
    fun testFullConfigUpdate() {
        val newConfig = SavingsGuideConfig(
            spendingAccountId = 1L,
            savingsAccountId = 2L,
            bufferPaise = 5_000_00L,
            paydayThresholdPct = 30,
            sweepWindowDays = 4,
            minSweepPaise = 50_000L,
            preferredPayApp = "net.one97.paytm",
            notificationsEnabled = true
        )

        guidePreferences.updateConfig(newConfig)
        assertThat(guidePreferences.getConfig()).isEqualTo(newConfig)
    }
}
