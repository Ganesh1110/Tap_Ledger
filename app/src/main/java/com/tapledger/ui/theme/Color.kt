package com.tapledger.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ============================================================================
// Gradient Glass Light Palette
// ============================================================================

// Brand Primary: Radiant Indigo to Violet
val GlassPrimary = Color(0xFF4F46E5)
val GlassPrimaryVariant = Color(0xFF7C3AED)
val GlassOnPrimary = Color(0xFFFFFFFF)
val GlassPrimaryContainer = Color(0xFFEEF2FF)
val GlassOnPrimaryContainer = Color(0xFF312E81)

val GlassDarkPrimary = Color(0xFF818CF8)
val GlassDarkOnPrimary = Color(0xFF1E1B4B)
val GlassDarkPrimaryContainer = Color(0xFF3730A3)
val GlassDarkOnPrimaryContainer = Color(0xFFE0E7FF)

// Secondary: Radiant Sky to Cyan
val GlassSecondary = Color(0xFF0284C7)
val GlassOnSecondary = Color(0xFFFFFFFF)
val GlassSecondaryContainer = Color(0xFFE0F2FE)
val GlassOnSecondaryContainer = Color(0xFF0369A1)

val GlassDarkSecondary = Color(0xFF38BDF8)
val GlassDarkOnSecondary = Color(0xFF082F49)
val GlassDarkSecondaryContainer = Color(0xFF075985)
val GlassDarkOnSecondaryContainer = Color(0xFFBAE6FD)

// Semantic Financial Gradients & Colors
// Income: Emerald Teal
val IncomeGreen = Color(0xFF059669)
val IncomeGreenLight = Color(0xFF10B981)
val IncomeGreenDark = Color(0xFF34D399)
val IncomeGreenContainer = Color(0xFFD1FAE5)
val IncomeGreenOnContainer = Color(0xFF064E3B)
val IncomeGreenDarkContainer = Color(0xFF064E3B)
val IncomeGreenDarkOnContainer = Color(0xFFA7F3D0)

// Expense: Rose Coral
val ExpenseRed = Color(0xFFE11D48)
val ExpenseRedLight = Color(0xFFF43F5E)
val ExpenseRedDark = Color(0xFFFB7185)
val ExpenseRedContainer = Color(0xFFFFE4E6)
val ExpenseRedOnContainer = Color(0xFF881337)
val ExpenseRedDarkContainer = Color(0xFF881337)
val ExpenseRedDarkOnContainer = Color(0xFFFECDD3)

// Warning: Amber Gold
val WarningAmber = Color(0xFFD97706)
val WarningAmberLight = Color(0xFFF59E0B)
val WarningAmberDark = Color(0xFFFBBF24)
val WarningAmberContainer = Color(0xFFFEF3C7)
val WarningAmberOnContainer = Color(0xFF78350F)
val WarningAmberDarkContainer = Color(0xFF78350F)
val WarningAmberDarkOnContainer = Color(0xFFFDE68A)

// Frosted Glass Light Surfaces & Luminous Canvas
val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF8FAFC)
val LightSurfaceContainer = Color(0xFFF1F5F9)
val LightSurfaceContainerHigh = Color(0xFFE2E8F0)
val LightSurfaceContainerHighest = Color(0xFFCBD5E1)
val LightOutline = Color(0xFFE2E8F0)
val LightOutlineVariant = Color(0xFFF1F5F9)

// Translucent Glass Surfaces
val GlassSurfaceWhite = Color(0xFFFFFFFF).copy(alpha = 0.82f)
val GlassSurfaceFrost = Color(0xFFF8FAFC).copy(alpha = 0.75f)
val GlassBorderHighlight = Color(0xFFFFFFFF).copy(alpha = 0.90f)
val GlassBorderSubtle = Color(0xFFE2E8F0).copy(alpha = 0.65f)

// Dark Surfaces (Deep Glass Night)
val DarkBackground = Color(0xFF0B0F19)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceContainerLowest = Color(0xFF070B12)
val DarkSurfaceContainerLow = Color(0xFF161F30)
val DarkSurfaceContainer = Color(0xFF1E293B)
val DarkSurfaceContainerHigh = Color(0xFF283548)
val DarkSurfaceContainerHighest = Color(0xFF334155)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)

// Radiant Mesh Gradient Brushes
val RadiantPrimaryGradient = Brush.linearGradient(
    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFF06B6D4))
)
val RadiantHeroGradient = Brush.linearGradient(
    listOf(Color(0xFFEEF2FF), Color(0xFFF5F3FF), Color(0xFFECFEFF))
)
val RadiantIncomeGradient = Brush.linearGradient(
    listOf(Color(0xFF10B981), Color(0xFF059669))
)
val RadiantExpenseGradient = Brush.linearGradient(
    listOf(Color(0xFFF43F5E), Color(0xFFE11D48))
)
val RadiantSavingsGradient = Brush.linearGradient(
    listOf(Color(0xFF0EA5E9), Color(0xFF6366F1))
)
val RadiantAmberGradient = Brush.linearGradient(
    listOf(Color(0xFFF59E0B), Color(0xFFD97706))
)
val RadiantGlassCardBackground = Brush.linearGradient(
    listOf(Color(0xFFFFFFFF).copy(alpha = 0.88f), Color(0xFFF8FAFC).copy(alpha = 0.72f))
)
val RadiantGlassBorder = Brush.linearGradient(
    listOf(Color(0xFFFFFFFF).copy(alpha = 0.95f), Color(0xFFE2E8F0).copy(alpha = 0.40f))
)

// App Mesh Canvas Gradient
val RadiantAppCanvasGradient = Brush.verticalGradient(
    listOf(
        Color(0xFFEEF2FF), // soft indigo mist at top
        Color(0xFFF8FAFC), // luminous crisp pearl center
        Color(0xFFF5F3FF)  // subtle violet dawn at bottom
    )
)

// Editorial / Legacy Aliases for seamless backward compatibility
val InkPrimary = GlassPrimary
val InkOnPrimary = GlassOnPrimary
val InkPrimaryContainer = GlassPrimaryContainer
val InkOnPrimaryContainer = GlassOnPrimaryContainer
val InkDarkPrimary = GlassDarkPrimary
val InkDarkOnPrimary = GlassDarkOnPrimary
val InkDarkPrimaryContainer = GlassDarkPrimaryContainer
val InkDarkOnPrimaryContainer = GlassDarkOnPrimaryContainer

val SlateSecondary = GlassSecondary
val SlateOnSecondary = GlassOnSecondary
val SlateSecondaryContainer = GlassSecondaryContainer
val SlateOnSecondaryContainer = GlassOnSecondaryContainer
val SlateDarkSecondary = GlassDarkSecondary
val SlateDarkOnSecondary = GlassDarkOnSecondary
val SlateDarkSecondaryContainer = GlassDarkSecondaryContainer
val SlateDarkOnSecondaryContainer = GlassDarkOnSecondaryContainer

val IndigoPrimary = GlassPrimary
val IndigoOnPrimary = GlassOnPrimary
val IndigoPrimaryContainer = GlassPrimaryContainer
val IndigoOnPrimaryContainer = GlassOnPrimaryContainer
val IndigoDarkPrimary = GlassDarkPrimary
val IndigoDarkOnPrimary = GlassDarkOnPrimary
val IndigoDarkPrimaryContainer = GlassDarkPrimaryContainer
val IndigoDarkOnPrimaryContainer = GlassDarkOnPrimaryContainer

val EmeraldPrimary = IncomeGreen
val RoseError = ExpenseRed
val AmberWarning = WarningAmber
