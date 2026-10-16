package com.tapledger.ui.screens.saving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.preferences.SavingsGuideConfig
import com.tapledger.core.preferences.SavingsGuidePreferences
import com.tapledger.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SavingsGuideSettingsUiState(
    val config: SavingsGuideConfig = SavingsGuideConfig(),
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SavingsGuideSettingsViewModel @Inject constructor(
    private val savingsGuidePreferences: SavingsGuidePreferences,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<SavingsGuideSettingsUiState> = combine(
        savingsGuidePreferences.configFlow,
        accountRepository.getAllActiveAccounts()
    ) { config, accounts ->
        SavingsGuideSettingsUiState(
            config = config,
            accounts = accounts,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsGuideSettingsUiState()
    )

    fun updateConfig(config: SavingsGuideConfig) {
        viewModelScope.launch {
            savingsGuidePreferences.updateConfig(config)
        }
    }

    fun setSpendingAccount(accountId: Long?) {
        savingsGuidePreferences.setSpendingAccountId(accountId)
    }

    fun setSavingsAccount(accountId: Long?) {
        savingsGuidePreferences.setSavingsAccountId(accountId)
    }

    fun setBufferRupees(rupees: Long) {
        savingsGuidePreferences.setBufferPaise(rupees * 100L)
    }

    fun setPaydayThresholdPct(pct: Int) {
        savingsGuidePreferences.setPaydayThresholdPct(pct)
    }

    fun setSweepWindowDays(days: Int) {
        savingsGuidePreferences.setSweepWindowDays(days)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        savingsGuidePreferences.setNotificationsEnabled(enabled)
    }

    fun setPreferredPayApp(pkg: String?) {
        savingsGuidePreferences.setPreferredPayApp(pkg)
    }
}
