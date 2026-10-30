package com.tapledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapledger.core.model.TransactionStatus
import com.tapledger.ui.theme.ExpenseRed
import com.tapledger.ui.theme.ExpenseRedContainer
import com.tapledger.ui.theme.IncomeGreen
import com.tapledger.ui.theme.IncomeGreenContainer
import com.tapledger.ui.theme.Spacing
import com.tapledger.ui.theme.WarningAmber
import com.tapledger.ui.theme.WarningAmberContainer

@Composable
fun StatusBadge(
    status: TransactionStatus,
    modifier: Modifier = Modifier
) {
    val (label, icon, containerColor, contentColor) = when (status) {
        TransactionStatus.CATEGORIZED -> Quad(
            "Categorized",
            Icons.Default.CheckCircle,
            IncomeGreenContainer.copy(alpha = 0.7f),
            IncomeGreen
        )
        TransactionStatus.CONFIRMED -> Quad(
            "Confirmed",
            Icons.Default.CheckCircle,
            IncomeGreenContainer.copy(alpha = 0.7f),
            IncomeGreen
        )
        TransactionStatus.PENDING -> Quad(
            "Pending",
            Icons.Default.HourglassTop,
            WarningAmberContainer.copy(alpha = 0.7f),
            WarningAmber
        )
        TransactionStatus.VOIDED -> Quad(
            "Voided",
            Icons.Default.Cancel,
            ExpenseRedContainer.copy(alpha = 0.7f),
            ExpenseRed
        )
        TransactionStatus.IGNORED -> Quad(
            "Ignored",
            Icons.Default.RemoveCircleOutline,
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, contentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.xs, vertical = 3.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Status: $label"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = contentColor
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
