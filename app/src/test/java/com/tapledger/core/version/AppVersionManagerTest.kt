package com.tapledger.core.version

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.security.FakeSharedPreferences
import org.junit.Before
import org.junit.Test

class AppVersionManagerTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var releaseNotesRepo: ReleaseNotesRepository

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        releaseNotesRepo = ReleaseNotesRepositoryImpl()
    }

    @Test
    fun `first install detects launch type and registers install version`() {
        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 1L,
            injectedVersionName = "1.0.0",
            injectedDatabaseVersion = 2
        )

        assertThat(manager.lastSeenVersionCode).isEqualTo(-1L)
        assertThat(manager.getLaunchType()).isEqualTo(LaunchType.FirstInstall)
        assertThat(prefs.contains(AppVersionManagerImpl.PREF_FIRST_INSTALL_VERSION_CODE)).isTrue()
        assertThat(prefs.getLong(AppVersionManagerImpl.PREF_FIRST_INSTALL_VERSION_CODE, 0L)).isEqualTo(1L)
    }

    @Test
    fun `shouldShowWhatsNew is false while user is not onboarded`() {
        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 1L,
            injectedVersionName = "1.0.0",
            injectedDatabaseVersion = 2
        )

        // Do not interrupt onboarding
        assertThat(manager.shouldShowWhatsNew(isOnboarded = false)).isFalse()
        // Once onboarded, first time users should see What's New
        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isTrue()
    }

    @Test
    fun `markWhatsNewSeen updates lastSeenVersionCode and dismisses popup`() {
        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 1L,
            injectedVersionName = "1.0.0",
            injectedDatabaseVersion = 2
        )

        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isTrue()

        manager.markWhatsNewSeen()

        assertThat(manager.lastSeenVersionCode).isEqualTo(1L)
        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isFalse()
        assertThat(manager.getLaunchType()).isEqualTo(LaunchType.Normal)
    }

    @Test
    fun `upgrade detection triggers WhatsNew popup`() {
        // User had previously seen version 1
        prefs.storage[AppVersionManagerImpl.PREF_LAST_SEEN_VERSION_CODE] = 1L
        prefs.storage[AppVersionManagerImpl.PREF_FIRST_INSTALL_VERSION_CODE] = 1L

        // App updated to version 2
        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 2L,
            injectedVersionName = "1.1.0",
            injectedDatabaseVersion = 2
        )

        assertThat(manager.getLaunchType()).isEqualTo(LaunchType.Upgrade(fromVersionCode = 1L, toVersionCode = 2L))
        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isTrue()

        // After user sees it and clicks "Got It"
        manager.markWhatsNewSeen()

        assertThat(manager.lastSeenVersionCode).isEqualTo(2L)
        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isFalse()
        assertThat(manager.getLaunchType()).isEqualTo(LaunchType.Normal)
    }

    @Test
    fun `downgrade detection works safely`() {
        prefs.storage[AppVersionManagerImpl.PREF_LAST_SEEN_VERSION_CODE] = 3L

        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 2L,
            injectedVersionName = "1.1.0",
            injectedDatabaseVersion = 2
        )

        assertThat(manager.getLaunchType()).isEqualTo(LaunchType.Downgrade(fromVersionCode = 3L, toVersionCode = 2L))
        assertThat(manager.shouldShowWhatsNew(isOnboarded = true)).isFalse()
    }

    @Test
    fun `version formatting and details contain complete metadata`() {
        val manager = AppVersionManagerImpl(
            context = null,
            releaseNotesRepository = releaseNotesRepo,
            customPrefs = prefs,
            injectedVersionCode = 1L,
            injectedVersionName = "1.0.0",
            injectedDatabaseVersion = 2
        )

        assertThat(manager.getFormattedVersionString()).isEqualTo("v1.0.0 (Build 1)")

        val details = manager.getVersionDetails()
        assertThat(details.versionName).isEqualTo("1.0.0")
        assertThat(details.versionCode).isEqualTo(1L)
        assertThat(details.databaseSchemaVersion).isEqualTo(2)
        assertThat(details.launchType).isEqualTo(LaunchType.FirstInstall)
    }

    @Test
    fun `release notes repository provides rich changelog highlights`() {
        val latest = releaseNotesRepo.getLatestReleaseNotes()
        assertThat(latest.versionCode).isAtLeast(1L)
        assertThat(latest.versionName).isNotEmpty()
        assertThat(latest.releaseDate).isNotEmpty()
        assertThat(latest.title).isNotEmpty()
        assertThat(latest.summary).isNotEmpty()
        assertThat(latest.highlights).isNotEmpty()

        // Verify each highlight has valid content and categories
        for (highlight in latest.highlights) {
            assertThat(highlight.title).isNotEmpty()
            assertThat(highlight.description).isNotEmpty()
            assertThat(highlight.category).isNotNull()
        }

        val allNotes = releaseNotesRepo.getAllReleaseNotes()
        assertThat(allNotes).isNotEmpty()
        val versionCodes = allNotes.map { it.versionCode }
        assertThat(versionCodes).isEqualTo(versionCodes.sortedDescending())
    }
}
