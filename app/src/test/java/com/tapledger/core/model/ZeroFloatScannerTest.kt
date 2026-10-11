package com.tapledger.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Static analysis unit test that scans `src/main` to guarantee zero floating-point arithmetic.
 * Conforms to Flow.md §1.1 Hard Invariant and §17.7.
 *
 * Rules:
 * 1. Zero `Double`, `toDouble()`, or `BigDecimal` anywhere in the codebase (including render).
 * 2. Zero `Float` or `toFloat()` anywhere in the codebase EXCEPT strictly within
 *    `com/tapledger/ui/charts/render/` where Compose Canvas drawing requires it.
 */
class ZeroFloatScannerTest {

    data class ScanViolation(val path: String, val lineNum: Int, val message: String)

    private val doubleRegex = Regex("""\bDouble\b""")
    private val toDoubleRegex = Regex("""\btoDouble\s*\(""")
    private val bigDecimalRegex = Regex("""\bBigDecimal\b""")
    private val floatRegex = Regex("""\bFloat\b""")
    private val toFloatRegex = Regex("""\btoFloat\s*\(""")

    fun scanLine(relativePath: String, lineNum: Int, line: String): List<ScanViolation> {
        val violations = mutableListOf<ScanViolation>()
        val trimmed = line.trim()

        // Skip pure comments
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            return violations
        }

        val normalizedPath = relativePath.replace('\\', '/')
        val isRenderPackage = normalizedPath.startsWith("com/tapledger/ui/charts/render/")

        // In code lines, skip string literal contents (e.g. "Double-entry balances")
        val codeWithoutStrings = line.replace(Regex("""".*?""""), "")

        // Rule 1: No Double, toDouble(), or BigDecimal ANYWHERE
        if (codeWithoutStrings.contains(doubleRegex)) {
            violations.add(ScanViolation(normalizedPath, lineNum, "forbidden 'Double': $trimmed"))
        }
        if (codeWithoutStrings.contains(toDoubleRegex)) {
            violations.add(ScanViolation(normalizedPath, lineNum, "forbidden 'toDouble()': $trimmed"))
        }
        if (codeWithoutStrings.contains(bigDecimalRegex)) {
            violations.add(ScanViolation(normalizedPath, lineNum, "forbidden 'BigDecimal': $trimmed"))
        }

        // Rule 2: Strictly NO Float or toFloat() outside com/tapledger/ui/charts/render/**
        if (!isRenderPackage) {
            if (codeWithoutStrings.contains(floatRegex)) {
                violations.add(ScanViolation(normalizedPath, lineNum, "forbidden 'Float' outside render: $trimmed"))
            }
            if (codeWithoutStrings.contains(toFloatRegex)) {
                violations.add(ScanViolation(normalizedPath, lineNum, "forbidden 'toFloat()' outside render: $trimmed"))
            }
        }

        return violations
    }

    @Test
    fun testNoFloatingPointInCodebaseExceptRenderPrimitives() {
        val projectDir = File(System.getProperty("user.dir") ?: ".")
        val srcMainJava = File(projectDir, "src/main/java")
        val effectiveDir = if (srcMainJava.exists()) srcMainJava else File(projectDir, "app/src/main/java")

        com.google.common.truth.Truth.assertWithMessage("Source directory $effectiveDir exists")
            .that(effectiveDir.exists()).isTrue()

        val violations = mutableListOf<String>()

        effectiveDir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.forEach { file ->
            val relativePath = file.relativeTo(effectiveDir).path
            val lines = file.readLines()

            lines.forEachIndexed { index, line ->
                val lineViolations = scanLine(relativePath, index + 1, line)
                lineViolations.forEach {
                    violations.add("${it.path}:${it.lineNum} uses ${it.message}")
                }
            }
        }

        com.google.common.truth.Truth.assertWithMessage("Zero Floating-Point Violations in src/main")
            .that(violations).isEmpty()
    }

    @Test
    fun testFloatIsRejectedOutsideRenderPackage() {
        // Test that Float is rejected in core/financial packages
        val coreViolations = scanLine(
            relativePath = "com/tapledger/core/model/Budget.kt",
            lineNum = 10,
            line = "val amount: Float = 100.0f"
        )
        assertThat(coreViolations).hasSize(1)
        assertThat(coreViolations[0].message).contains("forbidden 'Float' outside render")

        // Test that toFloat() is rejected in core
        val coreToFloatViolations = scanLine(
            relativePath = "com/tapledger/core/engine/BudgetCalculator.kt",
            lineNum = 15,
            line = "val ratio = paise.toFloat() / 100"
        )
        assertThat(coreToFloatViolations).hasSize(1)
        assertThat(coreToFloatViolations[0].message).contains("forbidden 'toFloat()' outside render")

        // Test that Float is rejected in UI packages outside render (e.g. ViewModel or Screen)
        val uiViolations = scanLine(
            relativePath = "com/tapledger/ui/screens/dashboard/DashboardViewModel.kt",
            lineNum = 42,
            line = "val progress: Float = 0.5f"
        )
        assertThat(uiViolations).hasSize(1)
        assertThat(uiViolations[0].message).contains("forbidden 'Float' outside render")

        // Test that Float is allowed inside render package
        val renderFloatViolations = scanLine(
            relativePath = "com/tapledger/ui/charts/render/RingChartRender.kt",
            lineNum = 20,
            line = "val sweepAngle: Float = 180f"
        )
        assertThat(renderFloatViolations).isEmpty()

        // Test that Double is STILL rejected even inside render package
        val renderDoubleViolations = scanLine(
            relativePath = "com/tapledger/ui/charts/render/RingChartRender.kt",
            lineNum = 25,
            line = "val angle: Double = 180.0"
        )
        assertThat(renderDoubleViolations).hasSize(1)
        assertThat(renderDoubleViolations[0].message).contains("forbidden 'Double'")
    }
}

