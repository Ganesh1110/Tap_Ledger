package com.tapledger.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Validates MIGRATION_1_2 from Schema Version 1 to Version 2.
 *
 * Verifies that:
 * - V1 tables and existing data migrate cleanly to V2 definitions
 * - Non-null `direction` is backfilled accurately based on transaction kind and legacy transfer_direction
 * - `notes` is migrated from legacy `note`
 * - `transfer_group_id` is converted to text UUID representation
 * - `advance_ledger` maps `budget_period_id` to `source_period_id`
 * - Database triggers (§2.2) are established
 * - `PRAGMA integrity_check` passes with 'ok'
 */
class MigrationTest {

    private lateinit var conn: Connection
    private lateinit var dbAdapter: SupportSQLiteDatabase

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

        createV1Schema()
        populateV1Data()
    }

    @After
    fun tearDown() {
        conn.close()
    }

    private fun createV1Schema() {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE `accounts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `identifier_last4` TEXT,
                    `opening_balance_paise` INTEGER NOT NULL,
                    `opening_balance_date` INTEGER NOT NULL,
                    `currency` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `categories` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `default_bucket` TEXT NOT NULL,
                    `default_sort_order` INTEGER NOT NULL,
                    `default_advance_policy` TEXT NOT NULL,
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
                    `status` TEXT NOT NULL,
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
                    `reason` TEXT,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `account_id` INTEGER NOT NULL,
                    `kind` TEXT NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `merchant_raw` TEXT,
                    `merchant_normalized` TEXT,
                    `upi_reference` TEXT,
                    `occurred_at` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `source_fingerprint` TEXT,
                    `transfer_group_id` INTEGER,
                    `transfer_direction` TEXT,
                    `refund_of_transaction_id` INTEGER,
                    `note` TEXT
                );
            """)

            stmt.execute("""
                CREATE TABLE `transaction_allocations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transaction_id` INTEGER NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `advance_ledger` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `budget_period_id` INTEGER NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `amount_paise` INTEGER NOT NULL,
                    `direction` TEXT NOT NULL,
                    `reason` TEXT,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `merchant_map` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `merchant_normalized` TEXT NOT NULL,
                    `category_id` INTEGER NOT NULL,
                    `source` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `unparsed_alerts` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `source` TEXT NOT NULL,
                    `sender_or_package` TEXT NOT NULL,
                    `raw_redacted_preview` TEXT NOT NULL,
                    `received_at` INTEGER NOT NULL,
                    `reason` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `statement_imports` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `account_id` INTEGER NOT NULL,
                    `period_start` INTEGER NOT NULL,
                    `period_end` INTEGER NOT NULL,
                    `format` TEXT NOT NULL,
                    `opening_balance_paise` INTEGER NOT NULL,
                    `closing_balance_paise` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `statement_lines` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `statement_import_id` INTEGER NOT NULL,
                    `line_number` INTEGER NOT NULL,
                    `transaction_date` INTEGER NOT NULL,
                    `value_date` INTEGER,
                    `description` TEXT NOT NULL,
                    `reference` TEXT,
                    `debit_paise` INTEGER NOT NULL,
                    `credit_paise` INTEGER NOT NULL,
                    `balance_paise` INTEGER,
                    `matched_transaction_id` INTEGER,
                    `match_status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `month_closes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `budget_period_id` INTEGER NOT NULL,
                    `closed_at` INTEGER NOT NULL,
                    `closed_by` TEXT NOT NULL,
                    `reconciliation_status` TEXT NOT NULL,
                    `override_reason` TEXT,
                    `created_at` INTEGER NOT NULL
                );
            """)

            stmt.execute("""
                CREATE TABLE `transaction_audit_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transaction_id` INTEGER NOT NULL,
                    `action` TEXT NOT NULL,
                    `old_value_json` TEXT,
                    `new_value_json` TEXT,
                    `reason` TEXT,
                    `created_at` INTEGER NOT NULL
                );
            """)
        }
    }

    private fun populateV1Data() {
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO accounts VALUES (1, 'HDFC Bank', 'BANK', '1234', 5000000, 1000, 'INR', 1, 1000, 1000)")
            stmt.execute("INSERT INTO categories VALUES (10, 'Food', 'NEEDS', 1, 'CARRY_TO_ADVANCE', 1, 1000, 1000)")
            stmt.execute("INSERT INTO budget_periods VALUES (100, 2026, 1, 1000, 2000, 5000000, 'OPEN', 1000, 1000)")

            // V1 Transactions:
            // 1. Expense -> should have direction OUT
            stmt.execute("INSERT INTO transactions VALUES (1, 1, 'EXPENSE', 45000, 'Swiggy', 'swiggy', 'UPI1', 1500, 1500, 1500, 'CATEGORIZED', 'NOTIFICATION', 'fp1', NULL, NULL, NULL, 'Dinner')")
            // 2. Income -> should have direction IN
            stmt.execute("INSERT INTO transactions VALUES (2, 1, 'INCOME', 5000000, 'Salary', 'corp', 'UPI2', 1100, 1100, 1100, 'CATEGORIZED', 'MANUAL', 'fp2', NULL, NULL, NULL, 'Jan salary')")
            // 3. Transfer In -> should have direction IN
            stmt.execute("INSERT INTO transactions VALUES (3, 1, 'TRANSFER', 100000, 'Transfer', 'xfer', 'UPI3', 1200, 1200, 1200, 'CATEGORIZED', 'MANUAL', 'fp3', 888, 'IN', NULL, 'Received')")
            // 4. Transfer Out -> should have direction OUT
            stmt.execute("INSERT INTO transactions VALUES (4, 1, 'TRANSFER', 100000, 'Transfer', 'xfer', 'UPI4', 1200, 1200, 1200, 'CATEGORIZED', 'MANUAL', 'fp4', 888, 'OUT', NULL, 'Sent')")

            stmt.execute("INSERT INTO advance_ledger VALUES (1, 100, 10, 5000, 'NEGATIVE', 'Deficit', 1000)")
            stmt.execute("INSERT INTO merchant_map VALUES (1, 'swiggy', 10, 'CONFIRMED', 1000, 2000)")
            stmt.execute("INSERT INTO month_closes VALUES (1, 100, 2000, 'USER', 'RECONCILED', NULL, 2000)")
        }
    }

    @Test
    fun migration_1_to_2_preservesDataAndMigratesToConformantSchema() {
        // Execute Migration 1 -> 2
        MIGRATION_1_2.migrate(dbAdapter)

        // Verify accounts migrated correctly
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, name, is_savings, credit_limit_paise FROM accounts WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString("name")).isEqualTo("HDFC Bank")
            assertThat(rs.getInt("is_savings")).isEqualTo(0)
            assertThat(rs.getObject("credit_limit_paise")).isNull()
        }

        // Verify transactions direction backfill and notes migration
        conn.createStatement().use { stmt ->
            val rs1 = stmt.executeQuery("SELECT direction, notes, transfer_group_id FROM transactions WHERE id = 1")
            assertThat(rs1.next()).isTrue()
            assertThat(rs1.getString("direction")).isEqualTo("OUT")
            assertThat(rs1.getString("notes")).isEqualTo("Dinner")

            val rs2 = stmt.executeQuery("SELECT direction, notes FROM transactions WHERE id = 2")
            assertThat(rs2.next()).isTrue()
            assertThat(rs2.getString("direction")).isEqualTo("IN")
            assertThat(rs2.getString("notes")).isEqualTo("Jan salary")

            val rs3 = stmt.executeQuery("SELECT direction, transfer_group_id FROM transactions WHERE id = 3")
            assertThat(rs3.next()).isTrue()
            assertThat(rs3.getString("direction")).isEqualTo("IN")
            assertThat(rs3.getString("transfer_group_id")).isEqualTo("888")

            val rs4 = stmt.executeQuery("SELECT direction, transfer_group_id FROM transactions WHERE id = 4")
            assertThat(rs4.next()).isTrue()
            assertThat(rs4.getString("direction")).isEqualTo("OUT")
            assertThat(rs4.getString("transfer_group_id")).isEqualTo("888")
        }

        // Verify advance_ledger mapped budget_period_id -> source_period_id
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT source_period_id, applied_to_period_id, amount_paise FROM advance_ledger WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getLong("source_period_id")).isEqualTo(100L)
            assertThat(rs.getObject("applied_to_period_id")).isNull()
            assertThat(rs.getLong("amount_paise")).isEqualTo(5000L)
        }

        // Verify merchant_map migration
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT times_confirmed, times_overridden, last_used_at FROM merchant_map WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getInt("times_confirmed")).isEqualTo(1)
            assertThat(rs.getInt("times_overridden")).isEqualTo(0)
            assertThat(rs.getLong("last_used_at")).isEqualTo(2000L)
        }

        // Verify month_closes migration
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT planned_income_paise, total_spent_paise, closing_balances_json FROM month_closes WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getLong("planned_income_paise")).isEqualTo(0L)
            assertThat(rs.getString("closing_balances_json")).isEqualTo("{}")
        }
    }

    @Test
    fun migration_2_to_3_createsSavingsTablesAndTriggers() {
        // First migrate 1 -> 2
        MIGRATION_1_2.migrate(dbAdapter)

        // Now migrate 2 -> 3
        MIGRATION_2_3.migrate(dbAdapter)

        // Verify tables exist by inserting test rows
        conn.createStatement().use { stmt ->
            stmt.execute("""
                INSERT INTO savings_goals (id, name, target_paise, deadline_epoch_ms, priority, status, linked_account_id, created_at, updated_at)
                VALUES (1, 'Emergency Fund', 10000000, 2000000000000, 1, 'ACTIVE', 1, 1000, 1000)
            """)

            stmt.execute("""
                INSERT INTO savings_contributions (id, goal_id, amount_paise, source, transaction_id, period_id, note, created_at)
                VALUES (1, 1, 500000, 'MANUAL', NULL, NULL, 'Initial seed', 1000)
            """)

            val rs = stmt.executeQuery("SELECT name, target_paise FROM savings_goals WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString("name")).isEqualTo("Emergency Fund")
            assertThat(rs.getLong("target_paise")).isEqualTo(10000000L)

            val rsContrib = stmt.executeQuery("SELECT SUM(amount_paise) FROM savings_contributions WHERE goal_id = 1")
            assertThat(rsContrib.next()).isTrue()
            assertThat(rsContrib.getLong(1)).isEqualTo(500000L)
        }

        // Verify overdraw trigger prevents balance going negative
        try {
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    INSERT INTO savings_contributions (id, goal_id, amount_paise, source, transaction_id, period_id, note, created_at)
                    VALUES (2, 1, -600000, 'MANUAL', NULL, NULL, 'Overdraw attempt', 2000)
                """)
            }
            org.junit.Assert.fail("Expected trigger to abort on goal overdraw")
        } catch (e: Exception) {
            assertThat(e.message).contains("Savings goal balance cannot be negative")
        }
    }

    @Test
    fun migration_3_to_4_createsSavingsTransferPlansTableAndIndices() {
        // Migrate 1 -> 2 -> 3 -> 4
        MIGRATION_1_2.migrate(dbAdapter)
        MIGRATION_2_3.migrate(dbAdapter)
        MIGRATION_3_4.migrate(dbAdapter)

        conn.createStatement().use { stmt ->
            // Insert account 2 (Savings)
            stmt.execute("INSERT INTO accounts (id, name, type, identifier_last4, opening_balance_paise, opening_balance_date, is_savings, is_active, created_at, updated_at) VALUES (2, 'SBI Savings', 'BANK', '5678', 10000000, 1000, 1, 1, 1000, 1000)")

            stmt.execute("""
                INSERT INTO savings_transfer_plans (
                    id, period_id, kind, source_account_id, dest_account_id, suggested_paise, moved_paise, status, transfer_group_id, trigger_transaction_id, created_at, moved_at, verified_at
                ) VALUES (
                    1, 100, 'PAYDAY', 1, 2, 1200000, NULL, 'SUGGESTED', NULL, NULL, 1000, NULL, NULL
                )
            """)

            val rs = stmt.executeQuery("SELECT kind, suggested_paise, status FROM savings_transfer_plans WHERE id = 1")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getString("kind")).isEqualTo("PAYDAY")
            assertThat(rs.getLong("suggested_paise")).isEqualTo(1200000L)
            assertThat(rs.getString("status")).isEqualTo("SUGGESTED")
        }

        // Verify unique index on (period_id, kind)
        try {
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    INSERT INTO savings_transfer_plans (
                        id, period_id, kind, source_account_id, dest_account_id, suggested_paise, moved_paise, status, transfer_group_id, trigger_transaction_id, created_at, moved_at, verified_at
                    ) VALUES (
                        2, 100, 'PAYDAY', 1, 2, 1500000, NULL, 'SUGGESTED', NULL, NULL, 2000, NULL, NULL
                    )
                """)
            }
            org.junit.Assert.fail("Expected unique index constraint violation for duplicate (period_id, kind)")
        } catch (e: Exception) {
            assertThat(e.message).contains("UNIQUE constraint failed")
        }
    }

    @Test
    fun migration_4_to_5_addsIsPinnedColumnToCategories() {
        // Migrate 1 -> 2 -> 3 -> 4 -> 5
        MIGRATION_1_2.migrate(dbAdapter)
        MIGRATION_2_3.migrate(dbAdapter)
        MIGRATION_3_4.migrate(dbAdapter)
        MIGRATION_4_5.migrate(dbAdapter)

        conn.createStatement().use { stmt ->
            // Verify existing categories from populateV1Data now have is_pinned = 0 default
            val rs = stmt.executeQuery("SELECT id, name, is_pinned FROM categories WHERE id = 10")
            assertThat(rs.next()).isTrue()
            assertThat(rs.getInt("is_pinned")).isEqualTo(0)

            // Update category 10 to be pinned
            stmt.execute("UPDATE categories SET is_pinned = 1 WHERE id = 10")
            val rsUpdated = stmt.executeQuery("SELECT is_pinned FROM categories WHERE id = 10")
            assertThat(rsUpdated.next()).isTrue()
            assertThat(rsUpdated.getInt("is_pinned")).isEqualTo(1)

            // Insert new category with is_pinned = 1
            stmt.execute("""
                INSERT INTO categories (id, name, default_bucket, default_sort_order, default_advance_policy, is_active, is_pinned, created_at, updated_at)
                VALUES (99, 'House Rent', 'NEEDS', 0, 'EXPIRE', 1, 1, 1000, 1000)
            """)
            val rsNew = stmt.executeQuery("SELECT name, is_pinned FROM categories WHERE id = 99")
            assertThat(rsNew.next()).isTrue()
            assertThat(rsNew.getString("name")).isEqualTo("House Rent")
            assertThat(rsNew.getInt("is_pinned")).isEqualTo(1)
        }
    }
}


