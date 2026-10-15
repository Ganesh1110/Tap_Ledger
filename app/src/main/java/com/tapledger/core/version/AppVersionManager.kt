package com.tapledger.core.version

import android.content.Context
import android.content.SharedPreferences
import com.tapledger.BuildConfig
import com.tapledger.core.database.TapLedgerDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AppVersionManager {
    val currentVersionCode: Long
    val currentVersionName: String
    val lastSeenVersionCode: Long
    val databaseSchemaVersion: Int

    fun getLaunchType(): LaunchType
    fun shouldShowWhatsNew(isOnboarded: Boolean): Boolean
    fun markWhatsNewSeen()
    fun getFormattedVersionString(): String
    fun getVersionDetails(): VersionDetails
    fun getReleaseNotes(): AppVersionInfo
    fun getAllReleaseNotes(): List<AppVersionInfo>
    fun setLastSeenVersionCodeForTesting(versionCode: Long)
}

@Singleton
class AppVersionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context?,
    private val releaseNotesRepository: ReleaseNotesRepository,
    customPrefs: SharedPreferences? = null,
    injectedVersionCode: Long? = null,
    injectedVersionName: String? = null,
    injectedDatabaseVersion: Int? = null
) : AppVersionManager {

    companion object {
        const val PREFS_NAME = "tapledger_version_prefs"
        const val PREF_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
        const val PREF_FIRST_INSTALL_VERSION_CODE = "first_install_version_code"
        const val PREF_FIRST_INSTALL_TIMESTAMP = "first_install_timestamp"
        const val PREF_LAST_UPDATE_TIMESTAMP = "last_update_timestamp"
    }

    private val prefs: SharedPreferences = customPrefs
        ?: context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        ?: error("Neither customPrefs nor Context was provided to AppVersionManager")

    override val currentVersionCode: Long = injectedVersionCode ?: try {
        BuildConfig.VERSION_CODE.toLong()
    } catch (_: Throwable) {
        1L
    }

    override val currentVersionName: String = injectedVersionName ?: try {
        BuildConfig.VERSION_NAME
    } catch (_: Throwable) {
        "1.0.0"
    }

    override val databaseSchemaVersion: Int = injectedDatabaseVersion ?: TapLedgerDatabase.DATABASE_VERSION

    override val lastSeenVersionCode: Long
        get() = prefs.getLong(PREF_LAST_SEEN_VERSION_CODE, -1L)

    init {
        recordInstallOrUpdate()
    }

    private fun recordInstallOrUpdate() {
        val hasRecordedFirstInstall = prefs.contains(PREF_FIRST_INSTALL_VERSION_CODE)
        val now = System.currentTimeMillis()

        if (!hasRecordedFirstInstall) {
            prefs.edit()
                .putLong(PREF_FIRST_INSTALL_VERSION_CODE, currentVersionCode)
                .putLong(PREF_FIRST_INSTALL_TIMESTAMP, now)
                .apply()
        } else {
            val firstInstallVersion = prefs.getLong(PREF_FIRST_INSTALL_VERSION_CODE, currentVersionCode)
            if (firstInstallVersion < currentVersionCode) {
                prefs.edit()
                    .putLong(PREF_LAST_UPDATE_TIMESTAMP, now)
                    .apply()
            }
        }
    }

    override fun getLaunchType(): LaunchType {
        val lastSeen = lastSeenVersionCode
        return when {
            lastSeen == -1L -> LaunchType.FirstInstall
            lastSeen < currentVersionCode -> LaunchType.Upgrade(fromVersionCode = lastSeen, toVersionCode = currentVersionCode)
            lastSeen > currentVersionCode -> LaunchType.Downgrade(fromVersionCode = lastSeen, toVersionCode = currentVersionCode)
            else -> LaunchType.Normal
        }
    }

    override fun shouldShowWhatsNew(isOnboarded: Boolean): Boolean {
        if (!isOnboarded) return false
        val lastSeen = lastSeenVersionCode
        // Show if first time on an onboarded install or upgraded to a higher version
        return lastSeen < currentVersionCode
    }

    override fun markWhatsNewSeen() {
        prefs.edit()
            .putLong(PREF_LAST_SEEN_VERSION_CODE, currentVersionCode)
            .apply()
    }

    override fun getFormattedVersionString(): String {
        return "v$currentVersionName (Build $currentVersionCode)"
    }

    override fun getVersionDetails(): VersionDetails {
        return VersionDetails(
            versionName = currentVersionName,
            versionCode = currentVersionCode,
            databaseSchemaVersion = databaseSchemaVersion,
            launchType = getLaunchType(),
            lastSeenVersionCode = lastSeenVersionCode
        )
    }

    override fun getReleaseNotes(): AppVersionInfo {
        return releaseNotesRepository.getReleaseNotesForVersion(currentVersionCode)
            ?: releaseNotesRepository.getLatestReleaseNotes()
    }

    override fun getAllReleaseNotes(): List<AppVersionInfo> {
        return releaseNotesRepository.getAllReleaseNotes()
    }

    override fun setLastSeenVersionCodeForTesting(versionCode: Long) {
        prefs.edit().putLong(PREF_LAST_SEEN_VERSION_CODE, versionCode).commit()
    }
}
