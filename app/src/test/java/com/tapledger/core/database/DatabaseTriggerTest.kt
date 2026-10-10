package com.tapledger.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Validates Flow.md §2.2 Database Integrity Triggers (Rule M1 Hard Prevention).
 *
 * Verifies that SQLite engine-level triggers enforce closed period immutability:
 * - Transactions in closed periods cannot be inserted, altered, or deleted
 * - Pool rows in advance_ledger cannot be modified, and cannot be deleted while source period is CLOSED
 * - Atomic reopen protocol is permitted: setting period to OPEN allows pool rows and month_close deletion
 */
class DatabaseTriggerTest {

    private lateinit var conn: Connection
    private lateinit var dbAdapter: SupportSQLiteDatabase

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        dbAdapter = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                val sql = args[0] as String
                conn.createStatement().use { it.execute(sql) }
                null
            } else {
                null
            }
        } as SupportSQLiteDatabase

        createSchema()
        DatabaseTriggers.createTriggers(dbAdapter)
    }

    @After
    fun tearDown() {
        conn.close()
    }

    private fun createSchema() {
        conn.createStatement().use { stmt ->
            // budget_periods
            stmt.execute("""
                CREATE TABLE budget_periods (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    year INTEGER NOT NULL,
                    month INTEGER NOT NULL,
                    start_at INTEGER NOT NULL,
                    end_at INTEGER NOT NULL,
                    income_paise INTEGER NOT NULL,
                    is_reviewed INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    closed_at INTEGER,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """)

            // accounts
            stmt.execute("""
                CREATE TABLE accounts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    identifier_last4 TEXT,
                    opening_balance_paise INTEGER NOT NULL,
                    opening_balance_date INTEGER NOT NULL,
                    credit_limit_paise INTEGER,
                    statement_day INTEGER,
                    due_day INTEGER,
                    is_savings INTEGER NOT NULL,
                    is_active INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """)

            // categories
            stmt.execute("""
                CREATE TABLE categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    system_key TEXT,
                    default_bucket TEXT NOT NULL,
                    default_sort_order INTEGER NOT NULL,
                    default_advance_policy TEXT NOT NULL,
                    sweep_target_category_id INTEGER,
                    is_active INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """)

            // transactions
            stmt.execute("""
                CREATE TABLE transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    account_id INTEGER,
                    kind TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    merchant_raw TEXT,
                    merchant_normalized TEXT,
                    status TEXT NOT NULL,
                    previous_status TEXT,
                    source TEXT NOT NULL,
                    occurred_at INTEGER NOT NULL,
                    original_occurred_at INTEGER,
                    upi_reference TEXT,
                    transfer_group_id TEXT,
                    counterparty_account_id INTEGER,
                    refund_of_transaction_id INTEGER,
                    possible_duplicate_of_id INTEGER,
                    checkpoint_balance_paise INTEGER,
                    checkpoint_type TEXT,
                    notes TEXT,
                    source_fingerprint TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """)

            // transaction_allocations
            stmt.execute("""
                CREATE TABLE transaction_allocations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    transaction_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL
                );
            """)

            // budget_allocations
            stmt.execute("""
                CREATE TABLE budget_allocations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    budget_period_id INTEGER NOT NULL,
                    category_id INTEGER NOT NULL,
                    category_name_snapshot TEXT NOT NULL,
                    bucket_snapshot TEXT NOT NULL,
                    sort_order_snapshot INTEGER NOT NULL,
                    advance_policy_snapshot TEXT NOT NULL,
                    amount_paise INTEGER NOT NULL
                );
            """)

            // budget_adjustments
            stmt.execute("""
                CREATE TABLE budget_adjustments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    budget_period_id INTEGER NOT NULL,
                    from_category_id INTEGER NOT NULL,
                    to_category_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    reason TEXT,
                    created_at INTEGER NOT NULL
                );
            """)

            // advance_ledger
            stmt.execute("""
                CREATE TABLE advance_ledger (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    source_period_id INTEGER NOT NULL,
                    applied_to_period_id INTEGER,
                    category_id INTEGER NOT NULL,
                    amount_paise INTEGER NOT NULL,
                    direction TEXT NOT NULL,
                    reason TEXT,
                    created_at INTEGER NOT NULL
                );
            """)

            // month_closes
            stmt.execute("""
                CREATE TABLE month_closes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    budget_period_id INTEGER NOT NULL,
                    closed_at INTEGER NOT NULL,
                    planned_income_paise INTEGER NOT NULL,
                    actual_income_paise INTEGER NOT NULL,
                    total_budgeted_paise INTEGER NOT NULL,
                    total_spent_paise INTEGER NOT NULL,
                    total_saved_paise INTEGER NOT NULL,
                    reconciliation_status TEXT NOT NULL,
                    override_reason TEXT,
                    closing_balances_json TEXT NOT NULL
                );
            """)
        }
    }

    @Test
    fun transactionTriggers_preventModificationsInClosedPeriod() {
        // Setup: Period 1 is CLOSED [1000, 2000), Period 2 is OPEN [2000, 3000)
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO budget_periods (id, year, month, start_at, end_at, income_paise, is_reviewed, status, created_at, updated_at) VALUES (1, 2026, 1, 1000, 2000, 5000000, 1, 'CLOSED', 0, 0)")
            stmt.execute("INSERT INTO budget_periods (id, year, month, start_at, end_at, income_paise, is_reviewed, status, created_at, updated_at) VALUES (2, 2026, 2, 2000, 3000, 5000000, 1, 'OPEN', 0, 0)")
        }

        // 1. BEFORE INSERT aborts for closed period
        val insertEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO transactions (id, account_id, kind, direction, amount_paise, status, source, occurred_at, created_at, updated_at) VALUES (1, 1, 'EXPENSE', 'OUT', 50000, 'CONFIRMED', 'SMS', 1500, 0, 0)")
            }
        }
        assertThat(insertEx.message).contains("Cannot insert transaction into closed budget period")

        // 2. BEFORE INSERT succeeds for open period
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO transactions (id, account_id, kind, direction, amount_paise, status, source, occurred_at, created_at, updated_at) VALUES (2, 1, 'EXPENSE', 'OUT', 50000, 'CONFIRMED', 'SMS', 2500, 0, 0)")
        }

        // 3. BEFORE UPDATE aborts when moving transaction into closed period
        val moveEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE transactions SET occurred_at = 1500 WHERE id = 2")
            }
        }
        assertThat(moveEx.message).contains("Cannot alter transaction in closed budget period")

        // 4. Temporarily insert historical tx into closed period by disabling trigger to test UPDATE/DELETE guards
        conn.createStatement().use { stmt ->
            stmt.execute("DROP TRIGGER trg_tx_prevent_insert_closed_period")
            stmt.execute("INSERT INTO transactions (id, account_id, kind, direction, amount_paise, status, source, occurred_at, created_at, updated_at) VALUES (3, 1, 'EXPENSE', 'OUT', 50000, 'CONFIRMED', 'SMS', 1500, 0, 0)")
        }
        // Re-enable triggers
        DatabaseTriggers.createTriggers(dbAdapter)

        // Attempting to modify amount_paise in closed period must abort
        val updateEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE transactions SET amount_paise = 60000 WHERE id = 3")
            }
        }
        assertThat(updateEx.message).contains("Cannot alter transaction in closed budget period")

        // Attempting to DELETE transaction in closed period must abort
        val deleteEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM transactions WHERE id = 3")
            }
        }
        assertThat(deleteEx.message).contains("Cannot delete transaction from closed budget period")
    }

    @Test
    fun advanceLedgerTriggers_enforceTargetAndPoolImmutabilityAndAtomicReopen() {
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO budget_periods (id, year, month, start_at, end_at, income_paise, is_reviewed, status, created_at, updated_at) VALUES (1, 2026, 1, 1000, 2000, 5000000, 1, 'CLOSED', 0, 0)")
            stmt.execute("INSERT INTO budget_periods (id, year, month, start_at, end_at, income_paise, is_reviewed, status, created_at, updated_at) VALUES (2, 2026, 2, 2000, 3000, 5000000, 1, 'OPEN', 0, 0)")
        }

        // 1. Cannot insert advance row targeting a closed period
        val insertClosedEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("INSERT INTO advance_ledger (id, source_period_id, applied_to_period_id, category_id, amount_paise, direction, reason, created_at) VALUES (1, 1, 1, 10, 5000, 'NEGATIVE', 'deficit', 0)")
            }
        }
        assertThat(insertClosedEx.message).contains("Cannot insert advance ledger row into closed target period")

        // 2. Can insert advance row targeting an OPEN period
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO advance_ledger (id, source_period_id, applied_to_period_id, category_id, amount_paise, direction, reason, created_at) VALUES (2, 1, 2, 10, 5000, 'NEGATIVE', 'deficit', 0)")
        }

        // 3. Can insert an unassigned pool row (applied_to_period_id IS NULL)
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO advance_ledger (id, source_period_id, applied_to_period_id, category_id, amount_paise, direction, reason, created_at) VALUES (3, 1, NULL, 10, 5000, 'NEGATIVE', 'pool', 0)")
        }

        // 4. Modifying a pool row is strictly forbidden
        val updatePoolEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE advance_ledger SET amount_paise = 6000 WHERE id = 3")
            }
        }
        assertThat(updatePoolEx.message).contains("Cannot modify advance ledger row in closed period or pool row")

        // 5. Deleting a pool row whose source period is CLOSED must abort
        val deletePoolClosedEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM advance_ledger WHERE id = 3")
            }
        }
        assertThat(deletePoolClosedEx.message).contains("Cannot delete advance ledger row for closed period")

        // 6. ATOMIC REOPEN PROTOCOL (§10.1):
        // Transition source period 1 to OPEN first:
        conn.createStatement().use { stmt ->
            stmt.execute("UPDATE budget_periods SET status = 'OPEN', closed_at = NULL WHERE id = 1")
            // Now deleting period 1's pool row succeeds cleanly without tripping the trigger!
            stmt.execute("DELETE FROM advance_ledger WHERE id = 3")
        }

        // Verify pool row was deleted
        val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM advance_ledger WHERE id = 3")
        assertThat(rs.next()).isTrue()
        assertThat(rs.getInt(1)).isEqualTo(0)
    }

    @Test
    fun monthCloseTriggers_enforceSnapshotImmutabilityAndSafeReopen() {
        conn.createStatement().use { stmt ->
            stmt.execute("INSERT INTO budget_periods (id, year, month, start_at, end_at, income_paise, is_reviewed, status, created_at, updated_at) VALUES (1, 2026, 1, 1000, 2000, 5000000, 1, 'CLOSED', 0, 0)")
            stmt.execute("INSERT INTO month_closes (id, budget_period_id, closed_at, planned_income_paise, actual_income_paise, total_budgeted_paise, total_spent_paise, total_saved_paise, reconciliation_status, closing_balances_json) VALUES (1, 1, 2000, 5000000, 5000000, 5000000, 4500000, 500000, 'RECONCILED', '{}')")
        }

        // 1. UPDATE on month_closes is strictly prohibited
        val updateEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE month_closes SET total_spent_paise = 4600000 WHERE id = 1")
            }
        }
        assertThat(updateEx.message).contains("Cannot modify month close record")

        // 2. DELETE when associated period is CLOSED must abort
        val deleteClosedEx = assertThrows(SQLException::class.java) {
            conn.createStatement().use { stmt ->
                stmt.execute("DELETE FROM month_closes WHERE id = 1")
            }
        }
        assertThat(deleteClosedEx.message).contains("Cannot delete month close while period is closed")

        // 3. ATOMIC REOPEN PROTOCOL (§10.1):
        // Reopening sets period to OPEN first, then deletes month_closes
        conn.createStatement().use { stmt ->
            stmt.execute("UPDATE budget_periods SET status = 'OPEN', closed_at = NULL WHERE id = 1")
            stmt.execute("DELETE FROM month_closes WHERE id = 1")
        }

        val rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM month_closes WHERE id = 1")
        assertThat(rs.next()).isTrue()
        assertThat(rs.getInt(1)).isEqualTo(0)
    }
}
