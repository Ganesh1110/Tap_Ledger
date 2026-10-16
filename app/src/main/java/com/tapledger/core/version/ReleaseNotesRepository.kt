package com.tapledger.core.version

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import javax.inject.Inject
import javax.inject.Singleton

interface ReleaseNotesRepository {
    fun getLatestReleaseNotes(): AppVersionInfo
    fun getReleaseNotesForVersion(versionCode: Long): AppVersionInfo?
    fun getAllReleaseNotes(): List<AppVersionInfo>
}

@Singleton
class ReleaseNotesRepositoryImpl @Inject constructor() : ReleaseNotesRepository {

    private val releases: List<AppVersionInfo> = listOf(
        AppVersionInfo(
            versionCode = 2L,
            versionName = "1.1.0",
            releaseDate = "October 2026",
            title = "Savings Transfer Guide & Dip Guard",
            summary = "Intelligent savings guidance with conservative spare math, automated payday and month-end sweep triggers, bank alert verification, and Dip Guard protection.",
            highlights = listOf(
                ReleaseHighlight(
                    title = "Savings Transfer Guide",
                    description = "Smart transfer guidance calculating conservative spare funds without risking budget needs, card dues, commitments, or buffer. 1-tap UPI app launch.",
                    category = ReleaseCategory.NEW_FEATURE,
                    icon = Icons.Default.Savings
                ),
                ReleaseHighlight(
                    title = "Bank Alert Auto-Verification",
                    description = "Mark a transfer as moved instantly. When the matching bank debit or credit arrives, TapLedger auto-adopts the alert and upgrades status to Verified.",
                    category = ReleaseCategory.NEW_FEATURE,
                    icon = Icons.Default.CheckCircle
                ),
                ReleaseHighlight(
                    title = "Dip Guard Protection",
                    description = "Detects outbound debits from your savings account and prompts you to attribute the spending to an active goal or record an audited withdrawal.",
                    category = ReleaseCategory.SECURITY,
                    icon = Icons.Default.Security
                ),
                ReleaseHighlight(
                    title = "Backed by Savings Goal Bars",
                    description = "Visual progress bars displaying real bank balance backing for each goal in strict priority order, with soft earmark warnings.",
                    category = ReleaseCategory.IMPROVEMENT,
                    icon = Icons.Default.AccountBalance
                ),
                ReleaseHighlight(
                    title = "Automated Payday & Sweep Triggers",
                    description = "Background WorkManager worker evaluates month-end sweeps in the last N days of the period and reminds you of unverified transfers.",
                    category = ReleaseCategory.NEW_FEATURE,
                    icon = Icons.Default.NotificationsActive
                ),
                ReleaseHighlight(
                    title = "D015 Saving Mode Isolation",
                    description = "Strict separation ensuring saving mode displays only savings account balances and verified totals, with zero leakage of spending math or card dues.",
                    category = ReleaseCategory.SECURITY,
                    icon = Icons.Default.Lock
                )
            )
        ),
        AppVersionInfo(
            versionCode = 1L,
            versionName = "1.0.0",
            releaseDate = "October 2026",
            title = "Welcome to TapLedger",
            summary = "Your private, offline-first personal finance ledger with zero floating-point drift and local-only intelligence.",
            highlights = listOf(
                ReleaseHighlight(
                    title = "100% Offline Financial Vault",
                    description = "Zero cloud dependencies, zero external analytics. All transaction logs and backups reside strictly encrypted on your device.",
                    category = ReleaseCategory.SECURITY,
                    icon = Icons.Default.CloudOff
                ),
                ReleaseHighlight(
                    title = "Zero Floating-Point Precision",
                    description = "Calculations use 64-bit integer cents. Impossible for 0.10 + 0.20 to drift into 0.30000000000000004 across account balance reconciliation.",
                    category = ReleaseCategory.IMPROVEMENT,
                    icon = Icons.Default.Calculate
                ),
                ReleaseHighlight(
                    title = "Automated Transaction Ingestion",
                    description = "Background listener parses bank SMS alerts & push notifications in real time with intelligent merchant categorization.",
                    category = ReleaseCategory.NEW_FEATURE,
                    icon = Icons.Default.NotificationsActive
                ),
                ReleaseHighlight(
                    title = "Argon2id MPIN & App Lock",
                    description = "Secure your records with a 6-digit MPIN, biometric unlock, and progressive lockout protection against brute-force attacks.",
                    category = ReleaseCategory.SECURITY,
                    icon = Icons.Default.Lock
                ),
                ReleaseHighlight(
                    title = "Bank Statement Reconciliation",
                    description = "Import PDF and CSV statements directly on-device to cross-verify notification-captured records against official ledgers.",
                    category = ReleaseCategory.NEW_FEATURE,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong
                ),
                ReleaseHighlight(
                    title = "Envelope Budgeting & Projections",
                    description = "Live burn-up projections, multi-bucket envelope allocations, and 6-period historical trend analysis.",
                    category = ReleaseCategory.IMPROVEMENT,
                    icon = Icons.Default.Speed
                )
            )
        )
    )

    override fun getLatestReleaseNotes(): AppVersionInfo {
        return releases.maxByOrNull { it.versionCode } ?: releases.first()
    }

    override fun getReleaseNotesForVersion(versionCode: Long): AppVersionInfo? {
        return releases.find { it.versionCode == versionCode }
    }

    override fun getAllReleaseNotes(): List<AppVersionInfo> {
        return releases.sortedByDescending { it.versionCode }
    }
}
