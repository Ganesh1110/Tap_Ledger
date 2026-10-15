package com.tapledger.core.version

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
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
