package com.tapledger.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.ExpenseRedDark
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.IncomeGreenDark
import com.tapledger.ui.theme.WarningAmber

enum class MoneySemantic {
    Automatic,
    Income,
    Expense,
    Debt,
    Neutral
}

enum class MoneyIndicator {
    None,
    CrDr,
    Owed
}

/**
 * Universal MoneyText composable for tapLedger.
 * - Uses proportional system fonts with tabular figures ("tnum") for stable alignment.
 * - Lakh / Indian numbering format strictly through MoneyFormatter.
 * - Displays signs and textual indicators ("Cr", "Dr", "owed") to never rely on color alone.
 * - Merges accessibility semantics for TalkBack (WCAG AA).
 */
@Composable
fun MoneyText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    semantic: MoneySemantic = MoneySemantic.Automatic,
    isPositiveGood: Boolean = true,
    showSign: Boolean = false,
    includeSymbol: Boolean = true,
    indicator: MoneyIndicator = MoneyIndicator.None
) {
    val isDark = MaterialTheme.colorScheme.surface.red < 0.5f
    val incomeColor = if (isDark) IncomeGreenDark else IncomeGreen
    val expenseColor = if (isDark) ExpenseRedDark else ExpenseRed

    val resolvedColor = when {
        color != Color.Unspecified -> color
        semantic == MoneySemantic.Income -> incomeColor
        semantic == MoneySemantic.Expense -> expenseColor
        semantic == MoneySemantic.Debt -> expenseColor
        semantic == MoneySemantic.Neutral -> MaterialTheme.colorScheme.onSurface
        amountPaise > 0L -> if (isPositiveGood) incomeColor else expenseColor
        amountPaise < 0L -> if (isPositiveGood) expenseColor else incomeColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    val formatted = MoneyFormatter.formatRupees(
        paise = amountPaise,
        showSign = showSign,
        includeSymbol = includeSymbol
    )

    val indicatorText = when (indicator) {
        MoneyIndicator.None -> null
        MoneyIndicator.CrDr -> if (amountPaise >= 0L) "Cr" else "Dr"
        MoneyIndicator.Owed -> if (amountPaise < 0L) "owed" else null
    }

    // Accessible description: never rely on color alone
    val accessibilityLabel = buildString {
        append(MoneyFormatter.formatRupees(kotlin.math.abs(amountPaise)))
        when {
            indicator == MoneyIndicator.Owed -> append(" owed")
            indicator == MoneyIndicator.CrDr -> append(if (amountPaise >= 0) " credited" else " debited")
            amountPaise < 0 -> append(" negative")
        }
    }

    val tabularStyle = style.copy(
        fontFeatureSettings = "tnum"
    )

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = accessibilityLabel
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatted,
            style = tabularStyle,
            color = resolvedColor
        )

        indicatorText?.let { tag ->
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = resolvedColor
            )
        }
    }
}
