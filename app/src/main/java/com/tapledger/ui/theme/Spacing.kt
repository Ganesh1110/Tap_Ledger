package com.tapledger.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global spacing design tokens: 4 / 8 / 12 / 16 / 24 / 32 dp.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp

    // Touch target minimum for accessibility (WCAG AA: 48dp)
    val minTouchTarget: Dp = 48.dp

    // Bottom list padding so elements aren't hidden behind the FAB / NavigationBar
    val listBottomPadding: Dp = 96.dp
}
