package com.tapledger.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class SecurityConfigurationTest {

    @Test
    fun testSecurityBoundaries() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val manifestFile = File(projectDir, "src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File(projectDir, "app/src/main/AndroidManifest.xml")
        }

        assertThat(manifestFile.exists()).isTrue()
        val manifestContent = manifestFile.readText()

        // 1. Verify android:allowBackup="false" per Flow.md §14.1
        assertThat(manifestContent).contains("android:allowBackup=\"false\"")

        // 2. Verify NO android.permission.INTERNET declared
        assertThat(manifestContent).doesNotContain("android.permission.INTERNET")

        // 3. Verify data_extraction_rules.xml excludes database transport
        val dataExtractionFile = File(manifestFile.parentFile, "res/xml/data_extraction_rules.xml")
        assertThat(dataExtractionFile.exists()).isTrue()
        val rulesContent = dataExtractionFile.readText()
        assertThat(rulesContent).contains("<cloud-backup>")
        assertThat(rulesContent).contains("<device-transfer>")
        assertThat(rulesContent).contains("<exclude domain=\"database\" />")

        // 4. Verify FLAG_SECURE set in MainActivity
        val mainActivityFile = File(manifestFile.parentFile, "java/com/tapledger/ui/MainActivity.kt")
        assertThat(mainActivityFile.exists()).isTrue()
        val mainActivityContent = mainActivityFile.readText()
        assertThat(mainActivityContent).contains("FLAG_SECURE")
    }
}
