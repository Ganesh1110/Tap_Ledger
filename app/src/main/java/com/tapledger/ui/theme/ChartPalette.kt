package com.tapledger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color-blind-safe Okabe-Ito chart palette per Flow.md §17.1 D9.
 * Needs = #0072B2 (Blue), Wants = #E69F00 (Orange), Savings = #009E73 (Bluish Green).
 * Provides light and dark variants ensuring WCAG contrast >= 4.5:1 for text
 * and >= 3:1 for graphical objects against their respective surfaces.
 */
object ChartPalette {
    // Canonical Okabe-Ito Base Tokens
    val NeedsBase = Color(0xFF0072B2)
    val WantsBase = Color(0xFFE69F00)
    val SavingsBase = Color(0xFF009E73)

    // Light Theme Palette
    val LightNeeds = Color(0xFF0072B2)         // Graphical object >= 3:1
    val LightNeedsText = Color(0xFF004D7A)     // Text label >= 4.5:1
    val LightWants = Color(0xFFC77800)         // Graphical object >= 3:1 against white
    val LightWantsText = Color(0xFF8A5100)     // Text label >= 4.5:1
    val LightSavings = Color(0xFF00875A)       // Graphical object >= 3:1
    val LightSavingsText = Color(0xFF005E3C)   // Text label >= 4.5:1

    // Dark Theme Palette
    val DarkNeeds = Color(0xFF56B4E9)          // Graphical object >= 3:1 & text >= 4.5:1
    val DarkNeedsText = Color(0xFF70C5F5)
    val DarkWants = Color(0xFFE69F00)          // Graphical object >= 3:1 & text >= 4.5:1
    val DarkWantsText = Color(0xFFFFB319)
    val DarkSavings = Color(0xFF009E73)        // Graphical object >= 3:1
    val DarkSavingsText = Color(0xFF33C293)    // Text label >= 4.5:1

    // Neutral / Structural Chart Colors
    val LightPaceLine = Color(0xFF777680)
    val DarkPaceLine = Color(0xFF91909A)
    val LightProjectionDashed = Color(0xFF5A5D72)
    val DarkProjectionDashed = Color(0xFFC3C5DD)

    val LightGridLine = Color(0xFFE0E0E6)
    val DarkGridLine = Color(0xFF2E3038)

    // Overflow & Alert Elements
    val OverflowHatch = Color(0xFFBA1A1A)
    val OverflowHatchDark = Color(0xFFFFB4AB)
}
