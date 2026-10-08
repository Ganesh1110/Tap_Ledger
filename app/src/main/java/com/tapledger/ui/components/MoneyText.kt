package com.tapledger.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.tapledger.core.model.MoneyFormatter

@Composable
fun MoneyText(
    amountPaise: Long,
    modifier: Modifier = Modifier,
    isPositiveGood: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified
) {
    val formatted = MoneyFormatter.formatRupees(amountPaise)
    val resolvedColor = when {
        color != Color.Unspecified -> color
        amountPaise > 0L -> if (isPositiveGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        amountPaise < 0L -> if (isPositiveGood) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = formatted,
        modifier = modifier,
        style = style,
        color = resolvedColor,
        fontFamily = FontFamily.Monospace
    )
}
