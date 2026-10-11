package com.tapledger.ui.charts.model

import androidx.compose.runtime.Immutable

/**
 * Universal Presentation States for all Visual Analytics components per Flow.md §17.3.
 * All state models are @Immutable to ensure zero unnecessary recompositions.
 */
@Immutable
sealed interface ChartState<out T> {
    @Immutable
    object Loading : ChartState<Nothing>

    @Immutable
    data class Empty(val message: String) : ChartState<Nothing>

    @Immutable
    data class Error(val message: String) : ChartState<Nothing>

    @Immutable
    data class Data<T>(val data: T) : ChartState<T>
}
