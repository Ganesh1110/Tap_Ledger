package com.tapledger.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Returns whether the software keyboard (IME) is currently open / visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isKeyboardVisible(): Boolean {
    return WindowInsets.isImeVisible
}

/**
 * Extension modifier that applies [imePadding] and [imeNestedScroll] to make any
 * composable layout keyboard-aware.
 *
 * @param imePadding Whether to pad the layout by the IME height so content isn't occluded.
 * @param imeNestedScroll Whether to enable nested scrolling to dismiss/collapse the IME.
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.keyboardAware(
    imePadding: Boolean = true,
    imeNestedScroll: Boolean = true
): Modifier = this
    .then(if (imePadding) Modifier.imePadding() else Modifier)
    .then(if (imeNestedScroll) Modifier.imeNestedScroll() else Modifier)

/**
 * A composable wrapper that ensures its content is aware of the software keyboard.
 * Automatically adds [imePadding] and [imeNestedScroll] so that forms, lists,
 * and bottom actions remain visible and scrollable when the IME appears.
 */
@Composable
fun KeyboardAware(
    modifier: Modifier = Modifier,
    imePadding: Boolean = true,
    imeNestedScroll: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.keyboardAware(
            imePadding = imePadding,
            imeNestedScroll = imeNestedScroll
        ),
        content = content
    )
}
