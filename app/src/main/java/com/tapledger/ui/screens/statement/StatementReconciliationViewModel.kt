package com.tapledger.ui.screens.statement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.StatementImportEntity
import com.tapledger.core.database.entity.StatementLineEntity
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.data.repository.StatementRepository
import com.tapledger.data.statement.StatementReconciliationEngine
import com.tapledger.ui.charts.builder.ReconciliationChartBuilder
import com.tapledger.ui.charts.model.ChartState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatementReconciliationViewModel @Inject constructor(
    private val statementRepository: StatementRepository,
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatementReconciliationUiState())
    val uiState: StateFlow<StatementReconciliationUiState> = _uiState.asStateFlow()

    private var linesJob: Job? = null

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                combine(
                    statementRepository.observeAllAccounts(),
                    budgetRepository.getAllActiveCategories(),
                    statementRepository.observeAllImports()
                ) { accounts, categories, imports ->
                    val selected = _uiState.value.selectedImport ?: imports.firstOrNull()
                    Triple(accounts, categories, Pair(imports, selected))
                }.collect { (accounts, categories, importsPair) ->
                    val (imports, selected) = importsPair
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            accounts = accounts,
                            categories = categories,
                            imports = imports,
                            selectedImport = selected
                        )
                    }
                    if (selected != null) {
                        observeLinesForImport(selected)
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load statement data"
                    )
                }
            }
        }
    }

    fun selectImport(importEntity: StatementImportEntity) {
        _uiState.update { it.copy(selectedImport = importEntity) }
        observeLinesForImport(importEntity)
    }

    private fun observeLinesForImport(importEntity: StatementImportEntity) {
        linesJob?.cancel()
        linesJob = viewModelScope.launch {
            statementRepository.observeLinesForImport(importEntity.id).collect { lines ->
                val isConsistent = StatementReconciliationEngine.verifyStatementArithmetic(
                    openingBalancePaise = importEntity.openingBalancePaise,
                    lines = lines,
                    reportedClosingBalancePaise = importEntity.closingBalancePaise
                )
                val bridgeState = try {
                    val proof = statementRepository.getReconciliationProof(importEntity.id)
                    if (proof != null) {
                        ReconciliationChartBuilder.buildReconciliationBridge(proof)
                    } else {
                        ChartState.Empty("No reconciliation proof available")
                    }
                } catch (e: Exception) {
                    ChartState.Error(e.message ?: "Failed to compute reconciliation bridge")
                }

                _uiState.update {
                    it.copy(
                        selectedImportLines = lines,
                        isArithmeticConsistent = isConsistent,
                        reconciliationBridgeState = bridgeState
                    )
                }
            }
        }
    }

    fun importCsvStatement(
        accountId: Long,
        csvContent: String,
        openingBalanceRupees: String,
        closingBalanceRupees: String,
        periodStart: Long,
        periodEnd: Long
    ) {
        viewModelScope.launch {
            try {
                if (csvContent.isBlank()) {
                    _uiState.update { it.copy(errorMessage = "Statement CSV content cannot be empty") }
                    return@launch
                }
                val openingPaise = try {
                    MoneyFormatter.toPaise(openingBalanceRupees)
                } catch (e: Exception) {
                    0L
                }
                val closingPaise = try {
                    MoneyFormatter.toPaise(closingBalanceRupees)
                } catch (e: Exception) {
                    0L
                }

                val importId = statementRepository.importStatement(
                    accountId = accountId,
                    csvContent = csvContent,
                    format = "CSV",
                    openingBalancePaise = openingPaise,
                    closingBalancePaise = closingPaise,
                    periodStart = periodStart,
                    periodEnd = periodEnd
                )

                val newImport = statementRepository.getImportById(importId)
                if (newImport != null) {
                    selectImport(newImport)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to import CSV statement") }
            }
        }
    }

    fun importPdfStatement(
        accountId: Long,
        inputStream: java.io.InputStream,
        password: String? = null,
        openingBalanceRupees: String = "",
        closingBalanceRupees: String = "",
        periodStart: Long? = null,
        periodEnd: Long? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val openingPaise = if (openingBalanceRupees.isNotBlank()) {
                    try { MoneyFormatter.toPaise(openingBalanceRupees) } catch (_: Exception) { null }
                } else null

                val closingPaise = if (closingBalanceRupees.isNotBlank()) {
                    try { MoneyFormatter.toPaise(closingBalanceRupees) } catch (_: Exception) { null }
                } else null

                val importId = statementRepository.importPdfStatement(
                    accountId = accountId,
                    inputStream = inputStream,
                    password = password?.ifBlank { null },
                    openingBalancePaise = openingPaise,
                    closingBalancePaise = closingPaise,
                    periodStart = periodStart,
                    periodEnd = periodEnd
                )

                val newImport = statementRepository.getImportById(importId)
                if (newImport != null) {
                    selectImport(newImport)
                }
                onSuccess()
            } catch (e: com.tapledger.data.statement.PdfPasswordRequiredException) {
                _uiState.update { it.copy(errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to parse and import PDF statement") }
            }
        }
    }

    fun createAppTransactionFromLine(line: StatementLineEntity, categoryId: Long) {
        viewModelScope.launch {
            try {
                val currentImport = _uiState.value.selectedImport ?: return@launch
                statementRepository.createAppTransactionFromLine(
                    line = line,
                    accountId = currentImport.accountId,
                    categoryId = categoryId
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to create transaction from statement line") }
            }
        }
    }

    fun resolveLine(line: StatementLineEntity) {
        viewModelScope.launch {
            try {
                statementRepository.resolveLine(line)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to resolve statement line") }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
