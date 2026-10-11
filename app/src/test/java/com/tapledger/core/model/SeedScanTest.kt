package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Validates that forbidden monetary seed data and seeder methods
 * audited in docs/SEED_AUDIT.md NEVER reappear in src/main production code.
 */
class SeedScanTest {

    private val forbiddenPatterns = listOf(
        Regex("""\bHDFC\s+Savings\b""", RegexOption.IGNORE_CASE),
        Regex("""\bSBI\s+Savings\b""", RegexOption.IGNORE_CASE),
        Regex("""\bHDFC\s+Credit\s+Card\b""", RegexOption.IGNORE_CASE),
        Regex("""50_000_00"""),
        Regex("""10_000_00"""),
        Regex("""-8_000_00"""),
        Regex("""52_000_00"""),
        Regex("""\bDatabaseSeeder\b"""),
        Regex("""\bseedInitialDataIfEmpty\b""")
    )

    @Test
    fun testNoMonetarySeedLiteralsInSrcMain() {
        val srcMain = File("src/main")
        val candidateDir = if (srcMain.exists()) srcMain else File("app/src/main")
        assertThat(candidateDir.exists()).isTrue()

        val violations = mutableListOf<String>()

        candidateDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "kt" || file.extension == "java")) {
                val relativePath = file.relativeTo(candidateDir).path.replace('\\', '/')
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                        for (pat in forbiddenPatterns) {
                            if (pat.containsMatchIn(line)) {
                                violations.add("$relativePath:${index + 1}: matches forbidden '${pat.pattern}': $trimmed")
                            }
                        }
                    }
                }
            }
        }

        com.google.common.truth.Truth.assertWithMessage("Forbidden monetary seed literals found in src/main").that(violations).isEmpty()
    }
}
