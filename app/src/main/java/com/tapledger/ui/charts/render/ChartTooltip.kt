package com.tapledger.ui.charts.render

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Immutable data payload for chart tooltip / detail strip per Flow.md §17.1 D10.
 */
data class ChartTooltipItem(
    val id: String,
    val title: String,
    val valueFormatted: String,
    val context: String? = null,
    val statusIcon: ImageVector? = null,
    val statusColor: Color? = null,
    val patternDescription: String? = null
)

/**
 * State manager for chart selection and tooltip rendering.
 * Provides tap-to-select, tap-again/tap-outside to clear, long-press scrubbing,
 * auto-dismiss after ~4s idle, and haptic feedback on selection change.
 */
@Stable
class ChartTooltipState(
    private val hapticFeedback: HapticFeedback? = null,
    private val autoDismissDelayMs: Long = 4000L
) {
    var selectedItem by mutableStateOf<ChartTooltipItem?>(null)
        private set

    var touchOffset by mutableStateOf<Offset?>(null)
        private set

    private var lastInteractionTime by mutableStateOf(0L)

    fun select(item: ChartTooltipItem, offset: Offset? = null) {
        if (selectedItem?.id == item.id && offset == null) {
            clear()
        } else {
            selectedItem = item
            touchOffset = offset
            lastInteractionTime = System.currentTimeMillis()
            hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun clear() {
        selectedItem = null
        touchOffset = null
    }

    suspend fun runAutoDismissLoop() {
        while (true) {
            delay(500L)
            val current = selectedItem
            if (current != null) {
                val elapsed = System.currentTimeMillis() - lastInteractionTime
                if (elapsed >= autoDismissDelayMs) {
                    clear()
                }
            }
        }
    }
}

@Composable
fun rememberChartTooltipState(
    autoDismissDelayMs: Long = 4000L
): ChartTooltipState {
    val haptic = LocalHapticFeedback.current
    val state = remember(haptic, autoDismissDelayMs) {
        ChartTooltipState(hapticFeedback = haptic, autoDismissDelayMs = autoDismissDelayMs)
    }

    LaunchedEffect(state) {
        state.runAutoDismissLoop()
    }

    return state
}

/**
 * Positioning calculations and screen compact thresholds for chart tooltips.
 */
object ChartTooltipPositioner {
    fun isCompactScreen(screenWidthDp: Int, fontScale: Float): Boolean {
        return screenWidthDp < 360 || fontScale > 1.15f
    }

    fun calculateTooltipOffset(
        touchX: Float,
        touchY: Float,
        containerWidth: Float,
        containerHeight: Float,
        tooltipWidth: Float,
        tooltipHeight: Float,
        margin: Float,
        verticalOffset: Float,
        flipDownOffset: Float
    ): Pair<Float, Float> {
        val rawX = touchX - (tooltipWidth / 2f)
        val clampedX = rawX.coerceIn(margin, (containerWidth - tooltipWidth - margin).coerceAtLeast(margin))

        val fitsAbove = (touchY - verticalOffset - tooltipHeight) >= margin
        val clampedY = if (fitsAbove) {
            touchY - verticalOffset - tooltipHeight
        } else {
            (touchY + flipDownOffset).coerceAtMost(containerHeight - tooltipHeight - margin)
        }
        return Pair(clampedX, clampedY)
    }
}

/**
 * Helper to determine if the device is a small screen or large font scale.
 * When true, compact layouts, larger touch hit targets, and detail strip modes are preferred.
 */
@Composable
fun rememberIsCompactChart(): Boolean {
    val config = LocalConfiguration.current
    return remember(config.screenWidthDp, config.fontScale) {
        ChartTooltipPositioner.isCompactScreen(config.screenWidthDp, config.fontScale)
    }
}

/**
 * Shared Floating Tooltip Composable.
 * Appears ABOVE the touch point (offset >= 56dp), flips below if insufficient room,
 * and clamps to screen bounds so it never clips.
 */
@Composable
fun ChartTooltipOverlay(
    state: ChartTooltipState,
    containerWidthPx: Float,
    containerHeightPx: Float,
    modifier: Modifier = Modifier
) {
    val item = state.selectedItem ?: return
    val touch = state.touchOffset ?: return

    val density = LocalDensity.current
    val tooltipWidthDp = 180.dp
    val tooltipHeightDp = 64.dp
    val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
    val tooltipHeightPx = with(density) { tooltipHeightDp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val verticalOffsetPx = with(density) { 56.dp.toPx() }
    val flipDownOffsetPx = with(density) { 24.dp.toPx() }

    val (clampedX, clampedY) = ChartTooltipPositioner.calculateTooltipOffset(
        touchX = touch.x,
        touchY = touch.y,
        containerWidth = containerWidthPx,
        containerHeight = containerHeightPx,
        tooltipWidth = tooltipWidthPx,
        tooltipHeight = tooltipHeightPx,
        margin = marginPx,
        verticalOffset = verticalOffsetPx,
        flipDownOffset = flipDownOffsetPx
    )

    Box(
        modifier = modifier
            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            .widthIn(min = 140.dp, max = tooltipWidthDp)
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = "${item.title}: ${item.valueFormatted}, ${item.context.orEmpty()}"
            }
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.statusIcon != null) {
                    Icon(
                        imageVector = item.statusIcon,
                        contentDescription = null,
                        tint = item.statusColor ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = item.valueFormatted,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.context.isNullOrBlank()) {
                Text(
                    text = item.context,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Shared Detail Strip Composable.
 * A fixed row rendered directly beneath or above a chart.
 * Shows selected item details or fallback summary text, providing an accessible,
 * comfortable experience for users who dislike floating popups.
 */
@Composable
fun ChartDetailStrip(
    item: ChartTooltipItem?,
    defaultSummary: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (item != null) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (item != null) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (item != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (item.statusIcon != null) {
                        Icon(
                            imageVector = item.statusIcon,
                            contentDescription = null,
                            tint = item.statusColor ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!item.context.isNullOrBlank()) {
                                Text(
                                    text = " • ${item.context}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = item.valueFormatted,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear selection",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = defaultSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
