package com.tapledger.core.version

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

enum class ReleaseCategory(val displayName: String) {
    NEW_FEATURE("New Feature"),
    IMPROVEMENT("Improvement"),
    SECURITY("Security"),
    BUG_FIX("Fix")
}

data class ReleaseHighlight(
    val title: String,
    val description: String,
    val category: ReleaseCategory,
    val icon: ImageVector = Icons.Default.AutoAwesome
)

data class AppVersionInfo(
    val versionCode: Long,
    val versionName: String,
    val releaseDate: String,
    val title: String,
    val summary: String,
    val highlights: List<ReleaseHighlight>
)

sealed interface LaunchType {
    object FirstInstall : LaunchType
    data class Upgrade(val fromVersionCode: Long, val toVersionCode: Long) : LaunchType
    object Normal : LaunchType
    data class Downgrade(val fromVersionCode: Long, val toVersionCode: Long) : LaunchType
}

data class VersionDetails(
    val versionName: String,
    val versionCode: Long,
    val databaseSchemaVersion: Int,
    val launchType: LaunchType,
    val lastSeenVersionCode: Long
)
