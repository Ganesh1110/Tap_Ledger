package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Static analysis unit test that scans `src/main` to guarantee zero floating-point arithmetic.
 * Conforms to Flow.md §1.1 Hard Invariant.
 *
 * Rules:
 * 1. Zero `Double`, `toDouble()`, or `BigDecimal` anywhere in the codebase (including UI).
 * 2. Zero `Float`, `toFloat()`, `Double`, `toDouble()`, or `BigDecimal` in business logic,
 *    DAOs, entities, repositories, calculators, parsers, and services (all non-UI packages).
 */
class ZeroFloatScannerTest {

    @Test
    fun testNoFloatingPointInFinancialCode() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val srcMainJava = File(projectDir, "src/main/java")
        val effectiveDir = if (srcMainJava.exists()) srcMainJava else File(projectDir, "app/src/main/java")

        com.google.common.truth.Truth.assertWithMessage("Source directory $effectiveDir exists")
            .that(effectiveDir.exists()).isTrue()

        val violations = mutableListOf<String>()

        effectiveDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { file ->
            val relativePath = file.relativeTo(effectiveDir).path
            val isUiFile = relativePath.startsWith("com/tapledger/ui")
            val lines = file.readLines()

            lines.forEachIndexed { index, line ->
                val lineNum = index + 1
                val trimmed = line.trim()

                // Skip pure comments
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    return@forEachIndexed
                }

                // Rule 1: No Double, toDouble(), or BigDecimal anywhere in the codebase
                val doubleRegex = Regex("""\bDouble\b""")
                val toDoubleRegex = Regex("""\btoDouble\s*\(""")
                val bigDecimalRegex = Regex("""\bBigDecimal\b""")

                // In UI files, skip string literal matches (e.g. "Double-entry balances")
                val codeWithoutStrings = line.replace(Regex("""".*?""""), "")

                if (codeWithoutStrings.contains(doubleRegex)) {
                    violations.add("$relativePath:$lineNum uses forbidden 'Double': $trimmed")
                }
                if (codeWithoutStrings.contains(toDoubleRegex)) {
                    violations.add("$relativePath:$lineNum uses forbidden 'toDouble()': $trimmed")
                }
                if (codeWithoutStrings.contains(bigDecimalRegex)) {
                    violations.add("$relativePath:$lineNum uses forbidden 'BigDecimal': $trimmed")
                }

                // Rule 2: In financial & business logic files (non-UI), strictly NO Float or toFloat()
                if (!isUiFile) {
                    val floatRegex = Regex("""\bFloat\b""")
                    val toFloatRegex = Regex("""\btoFloat\s*\(""")

                    if (codeWithoutStrings.contains(floatRegex)) {
                        violations.add("$relativePath:$lineNum uses forbidden 'Float' in financial logic: $trimmed")
                    }
                    if (codeWithoutStrings.contains(toFloatRegex)) {
                        violations.add("$relativePath:$lineNum uses forbidden 'toFloat()' in financial logic: $trimmed")
                    }
                }
            }
        }

        com.google.common.truth.Truth.assertWithMessage("Zero Floating-Point Violations in src/main")
            .that(violations).isEmpty()
    }
}
