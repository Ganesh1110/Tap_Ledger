package com.tapledger.ui.charts.util

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.tapledger.ui.theme.ChartPalette
import com.tapledger.ui.theme.DarkSurface
import com.tapledger.ui.theme.DarkSurfaceContainerLowest
import com.tapledger.ui.theme.LightSurface
import com.tapledger.ui.theme.LightSurfaceContainerLowest
import org.junit.Test
import kotlin.math.pow

/**
 * Verifies WCAG 2.1 contrast ratios for chart palette tokens per Flow.md §17.1 D9.
 * Requirements:
 * - Text labels >= 4.5:1 against surface.
 * - Graphical objects (bars, arcs, lines) >= 3.0:1 against surface.
 * Floating-point arithmetic is allowed in src/test only.
 */
class ContrastAccessibilityTest {

    private fun relativeLuminance(color: Color): Double {
        fun channelLuminance(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) {
                v / 12.92
            } else {
                ((v + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = channelLuminance(color.red)
        val g = channelLuminance(color.green)
        val b = channelLuminance(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(c1: Color, c2: Color): Double {
        val l1 = relativeLuminance(c1)
        val l2 = relativeLuminance(c2)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testLightTheme_textContrast_atLeast4_5() {
        val surfaces = listOf(LightSurface, LightSurfaceContainerLowest)
        for (surface in surfaces) {
            assertThat(contrastRatio(ChartPalette.LightNeedsText, surface)).isAtLeast(4.5)
            assertThat(contrastRatio(ChartPalette.LightWantsText, surface)).isAtLeast(4.5)
            assertThat(contrastRatio(ChartPalette.LightSavingsText, surface)).isAtLeast(4.5)
        }
    }

    @Test
    fun testLightTheme_graphicalObjectContrast_atLeast3_0() {
        val surfaces = listOf(LightSurface, LightSurfaceContainerLowest)
        for (surface in surfaces) {
            assertThat(contrastRatio(ChartPalette.LightNeeds, surface)).isAtLeast(3.0)
            assertThat(contrastRatio(ChartPalette.LightWants, surface)).isAtLeast(3.0)
            assertThat(contrastRatio(ChartPalette.LightSavings, surface)).isAtLeast(3.0)
        }
    }

    @Test
    fun testDarkTheme_textContrast_atLeast4_5() {
        val surfaces = listOf(DarkSurface, DarkSurfaceContainerLowest)
        for (surface in surfaces) {
            assertThat(contrastRatio(ChartPalette.DarkNeedsText, surface)).isAtLeast(4.5)
            assertThat(contrastRatio(ChartPalette.DarkWantsText, surface)).isAtLeast(4.5)
            assertThat(contrastRatio(ChartPalette.DarkSavingsText, surface)).isAtLeast(4.5)
        }
    }

    @Test
    fun testDarkTheme_graphicalObjectContrast_atLeast3_0() {
        val surfaces = listOf(DarkSurface, DarkSurfaceContainerLowest)
        for (surface in surfaces) {
            assertThat(contrastRatio(ChartPalette.DarkNeeds, surface)).isAtLeast(3.0)
            assertThat(contrastRatio(ChartPalette.DarkWants, surface)).isAtLeast(3.0)
            assertThat(contrastRatio(ChartPalette.DarkSavings, surface)).isAtLeast(3.0)
        }
    }
}
