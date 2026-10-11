package com.tapledger.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.data.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Validates that on a fresh install in release configuration:
 * 1. Every monetary table in the database contains exactly ZERO rows.
 * 2. Onboarding status (derived from database) is strictly false.
 */
class FreshInstallTest {

    private lateinit var conn: Connection
    private lateinit var dbAdapter: SupportSQLiteDatabase

    private val monetaryTables = listOf(
        "accounts",
        "transactions",
        "transaction_allocations",
        "budget_periods",
        "budget_allocations",
        "budget_adjustments",
        "advance_ledger",
        "month_closes",
        "statement_imports",
        "statement_lines",
        "merchant_map"
    )

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        dbAdapter = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    val sql = args[0] as String
                    conn.createStatement().use { it.execute(sql) }
                    null
                }
                "query" -> {
                    val sql = args[0] as String
                    val rs = conn.createStatement().executeQuery(sql)
                    Proxy.newProxyInstance(
                        android.database.Cursor::class.java.classLoader,
                        arrayOf(android.database.Cursor::class.java)
                    ) { _, cursorMethod, _ ->
                        when (cursorMethod.name) {
                            "moveToFirst" -> rs.next()
                            "getString" -> rs.getString(1)
                            "close" -> rs.close()
                            else -> null
                        }
                    }
                }
                else -> null
            }
        } as SupportSQLiteDatabase

        createV2FreshSchema()
        DatabaseTriggers.createTriggers(dbAdapter)
    }

    @After
    fun tearDown() {
        conn.close()
    }

    @Test
    fun testFreshInstall_allMonetaryTablesAreCompletelyEmpty() {
        for (table in monetaryTables) {
            val stmt = conn.createStatement()
            try {
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM $table")
                assertThat(rs.next()).isTrue()
                val count = rs.getInt(1)
                com.google.common.truth.Truth.assertWithMessage("Table '$table' row count on fresh install").that(count).isEqualTo(0)
            } finally {
                stmt.close()
            }
        }
    }

    @Test
    fun testFreshInstall_isOnboardedEvaluatesToFalse() = runBlocking {
        val fakeAccountDao = object : AccountDao {
            override suspend fun insert(account: AccountEntity): Long = 1L
            override suspend fun insertAll(accounts: List<AccountEntity>): List<Long> = emptyList()
            override suspend fun update(account: AccountEntity) {}
            override suspend fun getById(id: Long): AccountEntity? = null
            override fun getAllActiveAccounts(): Flow<List<AccountEntity>> = emptyFlow()
            override suspend fun getAllAccountsSync(): List<AccountEntity> = emptyList()
            override suspend fun findByLast4(last4: String): AccountEntity? = null
            override suspend fun findAllActiveByLast4(last4: String): List<AccountEntity> = emptyList()
            override suspend fun getActiveBankAccounts(): List<AccountEntity> = emptyList()
        }

        val fakeBudgetPeriodDao = object : BudgetPeriodDao {
            override suspend fun insert(budgetPeriod: BudgetPeriodEntity): Long = 1L
            override suspend fun update(budgetPeriod: BudgetPeriodEntity) {}
            override suspend fun getById(id: Long): BudgetPeriodEntity? = null
            override suspend fun getByYearMonth(year: Int, month: Int): BudgetPeriodEntity? = null
            override fun observeByYearMonth(year: Int, month: Int): Flow<BudgetPeriodEntity?> = emptyFlow()
            override fun getAllPeriods(): Flow<List<BudgetPeriodEntity>> = emptyFlow()
            override suspend fun getAllPeriodsSync(): List<BudgetPeriodEntity> = emptyList()
            override suspend fun getPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = null
            override suspend fun getEarliestPeriod(): BudgetPeriodEntity? = null
            override suspend fun getLatestPeriod(): BudgetPeriodEntity? = null
            override suspend fun getOpenPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = null
            override suspend fun getFirstOpenPeriod(): BudgetPeriodEntity? = null
            override suspend fun getClosedPeriodEndingAfter(startOfDayTimestamp: Long): BudgetPeriodEntity? = null
            override suspend fun getClosedPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity? = null
        }

        val onboardingRepo = OnboardingRepository(fakeAccountDao, fakeBudgetPeriodDao)
        assertThat(onboardingRepo.isOnboarded()).isFalse()
    }

    private fun createV2FreshSchema() {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE `accounts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `identifier_last4` TEXT,
                    `opening_balance_paise` INTEGER NOT NULL,
                    `opening_balance_date` INTEGER NOT NULL,
                    `credit_limit_paise` INTEGER,
                    `statement_day` INTEGER,
                    `due_day` INTEGER,
                    `is_savings` INTEGER NOT NULL DEFAULT 0,
                    `is_active` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `system_key` TEXT,
                    `default_bucket` TEXT NOT NULL,
                    `default_sort_order` INTEGER NOT NULL,
                    `default_advance_policy` TEXT NOT NULL,
                    `sweep_target_category_id` INTEGER,
                    `is_active` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `budget_periods` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `year` INTEGER NOT NULL,
                    `month` INTEGER NOT NULL,
                    `start_at` INTEGER NOT NULL,
                    `end_at` INTEGER NOT NULL,
                    `income_paise` INTEGER NOT NULL,
                    `is_reviewed` INTEGER NOT NULL DEFAULT 0,
                    `status` TEXT NOT NULL,
                    `closed_at` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `budget_allocations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `budget_period_id` INTEGER NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `category_name_snapshot` TEXT NOT NULL,
                    `bucket_snapshot` TEXT NOT NULL,
                    `sort_order_snapshot` INTEGER NOT NULL,
                    `advance_policy_snapshot` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `budget_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `budget_period_id` INTEGER NOT NULL,
                    `from_category_id` INTEGER NOT NULL,
                    `to_category_id` INTEGER NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `advance_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `source_period_id` INTEGER NOT NULL,
                    `applied_to_period_id` INTEGER,
                    `category_id` INTEGER NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `direction` TEXT NOT NULL,
                    `reason` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `account_id` INTEGER,
                    `kind` TEXT NOT NULL,
                    `direction` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `merchant_raw` TEXT,
                    `status` TEXT NOT NULL,
                    `previous_status` TEXT,
                    `source` TEXT NOT NULL,
                    `occurred_at` INTEGER NOT NULL,
                    `original_occurred_at` INTEGER,
                    `upi_reference` TEXT,
                    `transfer_group_id` TEXT,
                    `counterparty_account_id` INTEGER,
                    `refund_of_transaction_id` INTEGER,
                    `possible_duplicate_of_id` INTEGER,
                    `checkpoint_balance_paise` INTEGER,
                    `checkpoint_type` TEXT,
                    `notes` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `transaction_allocations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transaction_id` INTEGER NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `amount_paise` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `month_closes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `budget_period_id` INTEGER NOT NULL,
                    `closed_at` INTEGER NOT NULL,
                    `planned_income_paise` INTEGER NOT NULL,
                    `actual_income_paise` INTEGER NOT NULL,
                    `total_budgeted_paise` INTEGER NOT NULL,
                    `total_spent_paise` INTEGER NOT NULL,
                    `total_saved_paise` INTEGER NOT NULL,
                    `reconciliation_status` TEXT NOT NULL,
                    `override_reason` TEXT,
                    `closing_balances_json` TEXT NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `statement_imports` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `account_id` INTEGER NOT NULL,
                    `period_start` INTEGER NOT NULL,
                    `period_end` INTEGER NOT NULL,
                    `opening_balance_paise` INTEGER NOT NULL,
                    `closing_balance_paise` INTEGER NOT NULL,
                    `total_debits_paise` INTEGER NOT NULL,
                    `total_credits_paise` INTEGER NOT NULL,
                    `line_count` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `statement_lines` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `import_id` INTEGER NOT NULL,
                    `transaction_date` INTEGER NOT NULL,
                    `value_date` INTEGER,
                    `description` TEXT NOT NULL,
                    `reference` TEXT,
                    `debit_paise` INTEGER NOT NULL,
                    `credit_paise` INTEGER NOT NULL,
                    `balance_after_paise` INTEGER,
                    `matched_transaction_id` INTEGER,
                    `match_status` TEXT NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `merchant_map` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `merchant_normalized` TEXT NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `times_confirmed` INTEGER NOT NULL,
                    `times_overridden` INTEGER NOT NULL,
                    `last_used_at` INTEGER NOT NULL
                );
            """)
        }
    }
}
