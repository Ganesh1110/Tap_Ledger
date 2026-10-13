package com.tapledger.ui.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapledger.core.security.AppLockCoordinator
import com.tapledger.core.security.MpinManager
import com.tapledger.data.backup.BackupManager
import com.tapledger.data.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    onboardingRepository: OnboardingRepository,
    val mpinManager: MpinManager,
    private val appLockCoordinator: AppLockCoordinator,
    private val backupManager: BackupManager
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("tapledger_app_state_prefs", Context.MODE_PRIVATE)
    }

    val isOnboarded: StateFlow<Boolean?> = onboardingRepository.observeIsOnboarded()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val isAppLocked: StateFlow<Boolean> = appLockCoordinator.isAppLocked
    val lockMessage: StateFlow<String?> = appLockCoordinator.lockMessage

    private val _shouldShowUpgradePrompt = MutableStateFlow(false)
    val shouldShowUpgradePrompt: StateFlow<Boolean> = _shouldShowUpgradePrompt.asStateFlow()

    init {
        checkUpgradePrompt()
    }

    private fun checkUpgradePrompt() {
        viewModelScope.launch {
            isOnboarded.collect { onboarded ->
                val alreadyDismissed = prefs.getBoolean("mpin_upgrade_prompt_dismissed", false)
                if (onboarded == true && !mpinManager.isMpinSet.value && !alreadyDismissed) {
                    _shouldShowUpgradePrompt.value = true
                }
            }
        }
    }

    fun dismissUpgradePrompt() {
        prefs.edit().putBoolean("mpin_upgrade_prompt_dismissed", true).apply()
        _shouldShowUpgradePrompt.value = false
    }

    fun onAppBackgrounded() {
        appLockCoordinator.onAppBackgrounded()
    }

    fun onAppForegrounded() {
        appLockCoordinator.onAppForegrounded()
    }

    fun unlock() {
        appLockCoordinator.unlock()
    }

    fun eraseAllData() {
        viewModelScope.launch {
            backupManager.eraseAllDataAndReset()
            appLockCoordinator.unlock()
        }
    }
}
