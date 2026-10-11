package com.tapledger.core.database

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.data.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Validates that "Is onboarded" is strictly DERIVED from database state:
 * - At least one active account AND at least one budget period.
 * - Restoring a backup automatically skips onboarding without any standalone flag.
 * - Inactive accounts or missing periods prevent skipping onboarding.
 */
class RestoredBackupSkipsOnboardingTest {

    private class MemoryAccountDao : AccountDao {
        val accounts = mutableListOf<AccountEntity>()
        private val listFlow = MutableStateFlow<List<AccountEntity>>(emptyList())

        fun syncFlow() {
            listFlow.value = accounts.filter { it.isActive }
        }

        override suspend fun insert(account: AccountEntity): Long {
            accounts.add(account)
            syncFlow()
            return account.id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> {
            this.accounts.addAll(accounts)
            syncFlow()
            return accounts.map { it.id }
        }

        override suspend fun update(account: AccountEntity) {
            val idx = accounts.indexOfFirst { it.id == account.id }
            if (idx >= 0) accounts[idx] = account
            syncFlow()
        }

        override suspend fun getById(id: Long): AccountEntity? = accounts.find { it.id == id }
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = listFlow
        override suspend fun getAllAccountsSync(): List<AccountEntity> = accounts
        override suspend fun findByLast4(last4: String): AccountEntity? = accounts.find { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = accounts.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = accounts.filter { it.type == AccountType.BANK && it.isActive }
    }

    private class MemoryBudgetPeriodDao : BudgetPeriodDao {
        val periods = mutableListOf<BudgetPeriodEntity>()
        private val listFlow = MutableStateFlow<List<BudgetPeriodEntity>>(emptyList())

        fun syncFlow() {
            listFlow.value = periods
        }

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            periods.add(budgetPeriod)
            syncFlow()
            return budgetPeriod.id
        }

        override suspend fun update(budgetPeriod: BudgetPeriodEntity) {
            val idx = periods.indexOfFirst { it.id == budgetPeriod.id }
            if (idx >= 0) periods[idx] = budgetPeriod
            syncFlow()
        }

        override suspend fun getById(id: Long): BudgetPeriodEntity? = periods.find { it.id == id }
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = periods.find { it.year == year && it.month == month }
        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = MutableStateFlow(periods.find { it.year == year && it.month == month })
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = listFlow
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = periods
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.find { timestamp in it.startAt until it.endAt }
        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = periods.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = periods.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.find { it.status == BudgetPeriodStatus.OPEN && timestamp in it.startAt until it.endAt }
        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = periods.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }
        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? = periods.find { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }
        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = periods.find { it.status == BudgetPeriodStatus.CLOSED && timestamp in it.startAt until it.endAt }
    }

    @Test
    fun testRestoredBackup_withActiveAccountsAndPeriod_skipsOnboarding() = runBlocking {
        val accountDao = MemoryAccountDao()
        val budgetPeriodDao = MemoryBudgetPeriodDao()
        val repository = OnboardingRepository(accountDao, budgetPeriodDao)

        // Initially empty -> not onboarded
        assertThat(repository.isOnboarded()).isFalse()

        // Simulate restoring backup containing accounts and periods
        accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "Restored Checking",
                type = AccountType.BANK,
                identifierLast4 = "9876",
                openingBalancePaise = 2500000L,
                openingBalanceDate = 1000L,
                isActive = true,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1L,
                year = 2026,
                month = 10,
                startAt = 1000L,
                endAt = 2000L,
                incomePaise = 7500000L,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Derived isOnboarded evaluates to true immediately
        assertThat(repository.isOnboarded()).isTrue()
    }

    @Test
    fun testOnboardingCondition_inactiveAccountsOnly_doesNotSkipOnboarding() = runBlocking {
        val accountDao = MemoryAccountDao()
        val budgetPeriodDao = MemoryBudgetPeriodDao()
        val repository = OnboardingRepository(accountDao, budgetPeriodDao)

        accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "Archived Account",
                type = AccountType.BANK,
                identifierLast4 = "1111",
                openingBalancePaise = 0L,
                openingBalanceDate = 1000L,
                isActive = false, // Deactivated
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        budgetPeriodDao.insert(
            BudgetPeriodEntity(
                id = 1L,
                year = 2026,
                month = 10,
                startAt = 1000L,
                endAt = 2000L,
                incomePaise = 5000000L,
                status = BudgetPeriodStatus.OPEN,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        assertThat(repository.isOnboarded()).isFalse()
    }

    @Test
    fun testOnboardingCondition_missingBudgetPeriod_doesNotSkipOnboarding() = runBlocking {
        val accountDao = MemoryAccountDao()
        val budgetPeriodDao = MemoryBudgetPeriodDao()
        val repository = OnboardingRepository(accountDao, budgetPeriodDao)

        accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "Active Checking",
                type = AccountType.BANK,
                identifierLast4 = "2222",
                openingBalancePaise = 1000000L,
                openingBalanceDate = 1000L,
                isActive = true,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        // budget periods is empty

        assertThat(repository.isOnboarded()).isFalse()
    }
}
