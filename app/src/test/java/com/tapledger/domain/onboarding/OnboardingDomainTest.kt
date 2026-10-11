package com.tapledger.domain.onboarding

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.model.MoneyFormatter
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.domain.calculator.AccountBalanceCalculator
import com.tapledger.domain.validator.DataIntegrityChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class OnboardingDomainTest {

    private lateinit var fakeAccountDao: FakeAccountDao
    private lateinit var fakeCategoryDao: FakeCategoryDao
    private lateinit var fakeBudgetPeriodDao: FakeBudgetPeriodDao
    private lateinit var fakeBudgetAllocationDao: FakeBudgetAllocationDao
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var committer: OnboardingCommitter
    private val dataIntegrityChecker = DataIntegrityChecker()

    private class DirectTransactionRunner : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R = block()
    }

    private class RollbackTransactionRunner(
        private val onRollback: () -> Unit
    ) : DatabaseTransactionRunner {
        override suspend fun <R> run(block: suspend () -> R): R {
            try {
                return block()
            } catch (e: Throwable) {
                onRollback()
                throw e
            }
        }
    }

    class FakeAccountDao : AccountDao {
        val storage = mutableMapOf<Long, AccountEntity>()
        private var nextId = 1L

        override suspend fun insert(account: AccountEntity): Long {
            val id = if (account.id == 0L) nextId++ else account.id
            storage[id] = account.copy(id = id)
            return id
        }

        override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> =
            accounts.map { insert(it) }

        override suspend fun update(account: AccountEntity) {
            storage[account.id] = account
        }

        override suspend fun getById(id: Long): AccountEntity? = storage[id]
        override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
        override suspend fun getAllAccountsSync(): List<AccountEntity> = storage.values.toList()
        override suspend fun findByLast4(last4: String): AccountEntity? = storage.values.firstOrNull { it.identifierLast4 == last4 && it.isActive }
        override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = storage.values.filter { it.identifierLast4 == last4 && it.isActive }
        override suspend fun getActiveBankAccounts(): List<AccountEntity> = storage.values.filter { it.type == AccountType.BANK && it.isActive }
    }

    class FakeCategoryDao : CategoryDao {
        val storage = mutableMapOf<Long, CategoryEntity>()
        private var nextId = 1L

        override suspend fun insert(category: CategoryEntity): Long {
            val id = if (category.id == 0L) nextId++ else category.id
            storage[id] = category.copy(id = id)
            return id
        }

        override suspend fun insertAll(categories: List<CategoryEntity>): List<Long> =
            categories.map { insert(it) }

        override suspend fun update(category: CategoryEntity) {
            storage[category.id] = category
        }

        override suspend fun getById(id: Long): CategoryEntity? = storage[id]
        override suspend fun getAllCategoriesSync(): List<CategoryEntity> = storage.values.toList()
        override fun getAllActiveCategories(): Flow<List<CategoryEntity>> = emptyFlow()
        override suspend fun getByName(name: String): CategoryEntity? = storage.values.firstOrNull { it.name == name }
        override suspend fun getMaxSortOrder(): Int? = storage.values.maxOfOrNull { it.defaultSortOrder }
    }

    class FakeBudgetPeriodDao : BudgetPeriodDao {
        val storage = mutableMapOf<Long, BudgetPeriodEntity>()
        private var nextId = 1L

        override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long {
            val id = if (budgetPeriod.id == 0L) nextId++ else budgetPeriod.id
            storage[id] = budgetPeriod.copy(id = id)
            return id
        }

        override suspend fun update(budgetPeriod: BudgetPeriodEntity) {
            storage[budgetPeriod.id] = budgetPeriod
        }

        override suspend fun getById(id: Long): BudgetPeriodEntity? = storage[id]
        override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.year == year && it.month == month }

        override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
        override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
        override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = storage.values.toList()
        override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { timestamp in it.startAt until it.endAt }

        override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = storage.values.minByOrNull { it.startAt }
        override suspend fun getLatestPeriod(): BudgetPeriodEntity? = storage.values.maxByOrNull { it.startAt }
        override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.OPEN && timestamp in it.startAt until it.endAt }

        override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? =
            storage.values.filter { it.status == BudgetPeriodStatus.OPEN }.minByOrNull { it.startAt }

        override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && it.endAt > startOfDayTimestamp }

        override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? =
            storage.values.firstOrNull { it.status == BudgetPeriodStatus.CLOSED && timestamp in it.startAt until it.endAt }
    }

    class FakeBudgetAllocationDao : BudgetAllocationDao() {
        val storage = mutableMapOf<Pair<Long, Long>, BudgetAllocationEntity>()
        private var nextId = 1L

        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            val id = if (allocation.id == 0L) nextId++ else allocation.id
            val copy = allocation.copy(id = id)
            storage[Pair(allocation.budgetPeriodId, allocation.categoryId)] = copy
            return id
        }

        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> =
            allocations.map { insertInternal(it) }

        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            storage[Pair(allocation.budgetPeriodId, allocation.categoryId)] = allocation
        }

        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
            storage.values.filter { it.budgetPeriodId == periodId }

        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> =
            storage.values.toList()

        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()

        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? =
            storage[Pair(periodId, categoryId)]

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
        override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> = emptyList()
    }

    @Before
    fun setUp() {
        fakeAccountDao = FakeAccountDao()
        fakeCategoryDao = FakeCategoryDao()
        fakeBudgetPeriodDao = FakeBudgetPeriodDao()
        fakeBudgetAllocationDao = FakeBudgetAllocationDao()

        budgetRepository = BudgetRepository(
            categoryDao = fakeCategoryDao,
            budgetPeriodDao = fakeBudgetPeriodDao,
            budgetAllocationDao = fakeBudgetAllocationDao,
            budgetAdjustmentDao = object : com.tapledger.core.database.dao.BudgetAdjustmentDao {
                override suspend fun insert(adjustment: BudgetAdjustmentEntity): Long = 1L
                override fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> = emptyFlow()
                override suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> = emptyList()
                override suspend fun getAllAdjustmentsSync(): List<BudgetAdjustmentEntity> = emptyList()
            },
            transactionRunner = DirectTransactionRunner()
        )

        committer = OnboardingCommitter(
            transactionRunner = DirectTransactionRunner(),
            accountDao = fakeAccountDao,
            categoryDao = fakeCategoryDao,
            budgetPeriodDao = fakeBudgetPeriodDao,
            budgetAllocationDao = fakeBudgetAllocationDao,
            budgetRepository = budgetRepository,
            dataIntegrityChecker = dataIntegrityChecker
        )
    }

    @Test
    fun testParseOpeningBalance_zeroEquivalentsAndStrictValidation() {
        // Zero equivalents
        assertThat(MoneyFormatter.parseOpeningBalance("0")).isEqualTo(0L)
        assertThat(MoneyFormatter.parseOpeningBalance("0.0")).isEqualTo(0L)
        assertThat(MoneyFormatter.parseOpeningBalance("0.00")).isEqualTo(0L)
        assertThat(MoneyFormatter.parseOpeningBalance("₹0")).isEqualTo(0L)
        assertThat(MoneyFormatter.parseOpeningBalance("Rs. 0/-")).isEqualTo(0L)

        // Valid positive amounts
        assertThat(MoneyFormatter.parseOpeningBalance("50000")).isEqualTo(5000000L)
        assertThat(MoneyFormatter.parseOpeningBalance("₹1,50,000.50")).isEqualTo(15000050L)

        // Rejections
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseOpeningBalance("-500")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseOpeningBalance("10.555") // > 2 decimals
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyFormatter.parseOpeningBalance("100000000000") // > 100 Cr
        }
    }

    @Test
    fun testO4Distribution_exactSummationWithRemainders() {
        val incomePaise = 75_001_00L // ₹75,001.00
        val (needsTarget, wantsTarget, savingsTarget) = OnboardingDistribution.calculateBucketTargets(incomePaise)

        // B3/B4: Sum must equal planned income exactly
        assertThat(needsTarget + wantsTarget + savingsTarget).isEqualTo(incomePaise)

        val categories = listOf(
            CategoryEntity(id = 1L, name = "Cat 1", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 1, createdAt = 0L, updatedAt = 0L),
            CategoryEntity(id = 2L, name = "Cat 2", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 2, createdAt = 0L, updatedAt = 0L),
            CategoryEntity(id = 3L, name = "Cat 3", defaultBucket = BudgetBucket.NEEDS, defaultSortOrder = 3, createdAt = 0L, updatedAt = 0L)
        )

        val distribution = OnboardingDistribution.distributeBucket(needsTarget, categories)
        assertThat(distribution.values.sum()).isEqualTo(needsTarget)
        // Remainder must be on category with lowest sort order (id 1)
        assertThat(distribution[1L]!!).isAtLeast(distribution[2L]!!)
        assertThat(distribution[2L]!!).isEqualTo(distribution[3L]!!)
    }

    @Test
    fun testValidCommit_producesExpectedRows_zeroTransactions_zeroViolations() {
        runBlocking {
        val today = LocalDate.now(DateTimeUtils.IST_ZONE)
        val asOfDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

        // Seed reference categories
        ReferenceCategories.seedIfEmpty(fakeCategoryDao)
        val categories = fakeCategoryDao.getAllCategoriesSync()

        val incomePaise = 80_000_00L
        val (needs, wants, savings) = OnboardingDistribution.calculateBucketTargets(incomePaise)
        val allocs = mutableMapOf<Long, Long>()
        allocs.putAll(OnboardingDistribution.distributeBucket(needs, categories.filter { it.defaultBucket == BudgetBucket.NEEDS }))
        allocs.putAll(OnboardingDistribution.distributeBucket(wants, categories.filter { it.defaultBucket == BudgetBucket.WANTS }))
        allocs.putAll(OnboardingDistribution.distributeBucket(savings, categories.filter { it.defaultBucket == BudgetBucket.SAVINGS }))

        val draft = OnboardingDraft(
            monthlyIncomePaise = incomePaise,
            asOfDateEpochMillis = asOfDate,
            bankAccounts = listOf(
                StagedAccount(name = "Main Bank", type = AccountType.BANK, identifierLast4 = "1234", balancePaise = 40_000_00L)
            ),
            cashBalancePaise = 5_000_00L,
            creditCards = listOf(
                StagedCreditCard(name = "Reward Card", identifierLast4 = "9999", outstandingOwedPaise = 10_000_00L, creditLimitPaise = 50_000_00L)
            ),
            categoryAllocations = allocs
        )

        committer.commit(draft)

        // Verify accounts inserted
        val accounts = fakeAccountDao.getAllAccountsSync()
        assertThat(accounts).hasSize(3) // 1 Bank, 1 Cash, 1 CC

        val bank = accounts.find { it.type == AccountType.BANK }!!
        assertThat(bank.openingBalancePaise).isEqualTo(40_000_00L)

        val cash = accounts.find { it.type == AccountType.CASH }!!
        assertThat(cash.openingBalancePaise).isEqualTo(5_000_00L)

        val cc = accounts.find { it.type == AccountType.CREDIT_CARD }!!
        // CC stored signed negative
        assertThat(cc.openingBalancePaise).isEqualTo(-10_000_00L)

        // Verify budget period
        val period = fakeBudgetPeriodDao.getByYearMonth(today.year, today.monthValue)
        assertThat(period).isNotNull()
        assertThat(period!!.incomePaise).isEqualTo(incomePaise)
        assertThat(period.isReviewed).isTrue()

        // Verify Net Worth identity matches sum of staged balances
        val netWorth = AccountBalanceCalculator.calculateNetWorthFromBalances(accounts.map { it.openingBalancePaise })
        assertThat(netWorth.totalAssetsPaise).isEqualTo(45_000_00L) // 40k + 5k
        assertThat(netWorth.totalLiabilitiesPaise).isEqualTo(10_000_00L) // 10k
        assertThat(netWorth.netWorthPaise).isEqualTo(35_000_00L) // 45k - 10k

        // Verify DataIntegrityChecker
        val issues = dataIntegrityChecker.verifyIntegrity(
            accounts = accounts,
            categories = categories,
            periods = listOf(period),
            allocations = fakeBudgetAllocationDao.getAllocationsForPeriodSync(period.id),
            transactions = emptyList(),
            transactionAllocations = emptyList(),
            monthCloses = emptyList()
        )
        assertThat(issues).isEmpty()
        }
    }

    @Test
    fun testCommitRejection_incomeZeroOrNegative() {
        val draft = OnboardingDraft(monthlyIncomePaise = 0L)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { committer.commit(draft) }
        }
    }

    @Test
    fun testCommitRejection_openingDateOutsideCurrentPeriod() {
        val pastDate = 1000L // Far in past
        val draft = OnboardingDraft(
            monthlyIncomePaise = 50_000_00L,
            asOfDateEpochMillis = pastDate
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { committer.commit(draft) }
        }
    }

    @Test
    fun testCommitRejection_duplicateCommitBlocked() {
        runBlocking {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            val asOfDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

            val draft = OnboardingDraft(
                monthlyIncomePaise = 50_000_00L,
                asOfDateEpochMillis = asOfDate,
                bankAccounts = listOf(StagedAccount(name = "Bank A", type = AccountType.BANK, balancePaise = 10_000_00L)),
                cashBalancePaise = 0L
            )

            committer.commit(draft)

            // Attempting second commit must throw IllegalStateException
            assertThrows(IllegalStateException::class.java) {
                runBlocking { committer.commit(draft) }
            }
        }
    }

    @Test
    fun testRollback_onInjectedFailureLeavesZeroRows() {
        runBlocking {
            val today = LocalDate.now(DateTimeUtils.IST_ZONE)
            val asOfDate = DateTimeUtils.getStartOfMonthEpochMillis(today.year, today.monthValue)

            // Take snapshot before commit
            val accountSnapshot = fakeAccountDao.storage.toMap()
            val periodSnapshot = fakeBudgetPeriodDao.storage.toMap()

            val rollbackRunner = RollbackTransactionRunner {
                fakeAccountDao.storage.clear()
                fakeAccountDao.storage.putAll(accountSnapshot)
                fakeBudgetPeriodDao.storage.clear()
                fakeBudgetPeriodDao.storage.putAll(periodSnapshot)
            }

            // Committer configured with a failing component
            val failingCommitter = OnboardingCommitter(
                transactionRunner = rollbackRunner,
                accountDao = fakeAccountDao,
                categoryDao = fakeCategoryDao,
                budgetPeriodDao = fakeBudgetPeriodDao,
                budgetAllocationDao = fakeBudgetAllocationDao,
                budgetRepository = budgetRepository,
                dataIntegrityChecker = object : DataIntegrityChecker() {
                    // Injected failure
                    override fun verifyIntegrity(
                        accounts: List<AccountEntity>,
                        categories: List<CategoryEntity>,
                        periods: List<BudgetPeriodEntity>,
                        allocations: List<BudgetAllocationEntity>,
                        transactions: List<com.tapledger.core.database.entity.TransactionEntity>,
                        transactionAllocations: List<com.tapledger.core.database.entity.TransactionAllocationEntity>,
                        monthCloses: List<com.tapledger.core.database.entity.MonthCloseEntity>,
                        adjustments: List<BudgetAdjustmentEntity>,
                        advances: List<AdvanceLedgerEntity>,
                        statementImports: List<com.tapledger.core.database.entity.StatementImportEntity>,
                        statementLines: List<com.tapledger.core.database.entity.StatementLineEntity>
                    ) = listOf(com.tapledger.domain.model.IntegrityIssue(
                        ruleId = "T1",
                        message = "Simulated invariant failure",
                        entityType = "ACCOUNT",
                        entityId = "1",
                        severity = com.tapledger.domain.model.IntegritySeverity.CRITICAL
                    ))
                }
            )

            val draft = OnboardingDraft(
                monthlyIncomePaise = 50_000_00L,
                asOfDateEpochMillis = asOfDate,
                bankAccounts = listOf(StagedAccount(name = "Bank A", type = AccountType.BANK, balancePaise = 10_000_00L)),
                cashBalancePaise = 1000_00L
            )

            assertThrows(IllegalStateException::class.java) {
                runBlocking { failingCommitter.commit(draft) }
            }

            // Database rolled back: 0 accounts, 0 periods
            assertThat(fakeAccountDao.storage).isEmpty()
            assertThat(fakeBudgetPeriodDao.storage).isEmpty()
        }
    }
}
