package com.tapledger.ui.screens.monthclose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.MonthCloseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MonthCloseViewModel @Inject constructor(
    private val monthCloseRepository: MonthCloseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonthCloseUiState())
    val uiState: StateFlow<MonthCloseUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                val today = LocalDate.now(DateTimeUtils.IST_ZONE)
                val currentYear = today.year
                val currentMonth = today.monthValue

                val summary = monthCloseRepository.getPeriodSummary(currentYear, currentMonth)

                val periodId = summary?.period?.id ?: 0L
                if (periodId > 0L) {
                    monthCloseRepository.observeMonthCloseForPeriod(periodId).collect { monthClose ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentYear = currentYear,
                                currentMonth = currentMonth,
                                summary = summary,
                                monthCloseRecord = monthClose,
                                errorMessage = null
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            currentYear = currentYear,
                            currentMonth = currentMonth,
                            summary = summary,
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load month close details"
                    )
                }
            }
        }
    }

    fun closeMonth(reconciliationStatus: String = "RECONCILED", overrideReason: String? = null) {
        viewModelScope.launch {
            try {
                val summary = _uiState.value.summary ?: return@launch
                monthCloseRepository.closeMonth(summary, reconciliationStatus, overrideReason)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to close month") }
            }
        }
    }

    fun reopenMonth() {
        viewModelScope.launch {
            try {
                val periodId = _uiState.value.summary?.period?.id ?: return@launch
                monthCloseRepository.reopenMonth(periodId)
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to reopen month") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
