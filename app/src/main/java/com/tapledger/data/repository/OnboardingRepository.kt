package com.tapledger.data.repository

import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository to derive onboarding status dynamically from the database.
 * Per Flow.md §15.1:
 * "Is onboarded" is DERIVED from the database (at least one active account AND at least one budget period),
 * never from a standalone flag. A restored backup must skip onboarding.
 */
@Singleton
class OnboardingRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val budgetPeriodDao: BudgetPeriodDao
) {
    /**
     * Synchronously checks if the user has completed onboarding.
     */
    suspend fun isOnboarded(): Boolean {
        val hasActiveAccount = accountDao.getAllAccountsSync().any { it.isActive }
        val hasBudgetPeriod = budgetPeriodDao.getAllPeriodsSync().isNotEmpty()
        return hasActiveAccount && hasBudgetPeriod
    }

    /**
     * Observes whether the application has completed onboarding.
     */
    fun observeIsOnboarded(): Flow<Boolean> {
        return combine(
            accountDao.getAllActiveAccounts(),
            budgetPeriodDao.getAllPeriods()
        ) { accounts, periods ->
            accounts.isNotEmpty() && periods.isNotEmpty()
        }
    }
}
