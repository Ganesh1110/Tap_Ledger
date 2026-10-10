package com.tapledger.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Flow.md §2.2 Database Integrity Triggers (Rule M1 Hard Prevention)
 *
 * Implements SQLite BEFORE INSERT / UPDATE / DELETE triggers across financial tables
 * to guarantee immutability of closed budget periods at the database storage engine layer.
 */
object DatabaseTriggers {

    fun createTriggers(db: SupportSQLiteDatabase) {
        // Drop any previously existing triggers to ensure idempotent creation
        val triggers = listOf(
            "trg_tx_prevent_insert_closed_period",
            "trg_tx_prevent_update_closed_period",
            "trg_tx_prevent_delete_closed_period",
            "trg_adv_prevent_insert_closed_period",
            "trg_adv_prevent_update_closed_period",
            "trg_adv_prevent_delete_closed_period",
            "trg_budget_alloc_insert_closed",
            "trg_budget_alloc_update_closed",
            "trg_budget_alloc_delete_closed",
            "trg_budget_adj_insert_closed",
            "trg_budget_adj_update_closed",
            "trg_budget_adj_delete_closed",
            "trg_tx_alloc_insert_closed",
            "trg_tx_alloc_update_closed",
            "trg_tx_alloc_delete_closed",
            "trg_month_close_update",
            "trg_month_close_delete_closed"
        )
        for (trg in triggers) {
            db.execSQL("DROP TRIGGER IF EXISTS $trg")
        }

        // 1. Transactions Triggers (Flow.md §2.2)
        // BEFORE INSERT: Abort if occurred_at falls in a CLOSED period
        db.execSQL("""
            CREATE TRIGGER trg_tx_prevent_insert_closed_period
            BEFORE INSERT ON transactions
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE status = 'CLOSED' 
                  AND NEW.occurred_at >= start_at 
                  AND NEW.occurred_at < end_at
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot insert transaction into closed budget period');
            END;
        """.trimIndent())

        // BEFORE UPDATE: Abort if modifying financial/status attributes in a CLOSED period, or moving into a CLOSED period
        db.execSQL("""
            CREATE TRIGGER trg_tx_prevent_update_closed_period
            BEFORE UPDATE ON transactions
            WHEN (
                EXISTS (
                    SELECT 1 FROM budget_periods 
                    WHERE status = 'CLOSED' 
                      AND OLD.occurred_at >= start_at 
                      AND OLD.occurred_at < end_at
                )
                AND (
                    OLD.amount_paise != NEW.amount_paise 
                    OR OLD.direction != NEW.direction 
                    OR (OLD.account_id IS NOT NEW.account_id) 
                    OR OLD.occurred_at != NEW.occurred_at 
                    OR OLD.status != NEW.status 
                    OR OLD.kind != NEW.kind
                )
            ) OR EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE status = 'CLOSED' 
                  AND NEW.occurred_at >= start_at 
                  AND NEW.occurred_at < end_at
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot alter transaction in closed budget period');
            END;
        """.trimIndent())

        // BEFORE DELETE: Abort if occurred_at falls in a CLOSED period
        db.execSQL("""
            CREATE TRIGGER trg_tx_prevent_delete_closed_period
            BEFORE DELETE ON transactions
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE status = 'CLOSED' 
                  AND OLD.occurred_at >= start_at 
                  AND OLD.occurred_at < end_at
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete transaction from closed budget period');
            END;
        """.trimIndent())

        // 2. Advance Ledger Triggers (Flow.md §2.2)
        // Keyed primarily on applied_to_period_id.
        // When applied_to_period_id IS NOT NULL: abort if applied target period is CLOSED
        db.execSQL("""
            CREATE TRIGGER trg_adv_prevent_insert_closed_period
            BEFORE INSERT ON advance_ledger
            WHEN NEW.applied_to_period_id IS NOT NULL AND EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = NEW.applied_to_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot insert advance ledger row into closed target period');
            END;
        """.trimIndent())

        // BEFORE UPDATE: modifying pool rows (applied_to_period_id IS NULL) or rows in closed periods is strictly prohibited
        db.execSQL("""
            CREATE TRIGGER trg_adv_prevent_update_closed_period
            BEFORE UPDATE ON advance_ledger
            WHEN (OLD.applied_to_period_id IS NULL OR NEW.applied_to_period_id IS NULL)
              OR EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE (id = OLD.applied_to_period_id OR id = NEW.applied_to_period_id) 
                  AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot modify advance ledger row in closed period or pool row');
            END;
        """.trimIndent())

        // BEFORE DELETE:
        // When applied_to_period_id IS NOT NULL: abort if target applied period is CLOSED
        // When applied_to_period_id IS NULL (unassigned pool): abort ONLY IF source_period is CLOSED (allows atomic Reopen when source is OPEN)
        db.execSQL("""
            CREATE TRIGGER trg_adv_prevent_delete_closed_period
            BEFORE DELETE ON advance_ledger
            WHEN (
                OLD.applied_to_period_id IS NOT NULL AND EXISTS (
                    SELECT 1 FROM budget_periods 
                    WHERE id = OLD.applied_to_period_id AND status = 'CLOSED'
                )
            ) OR (
                OLD.applied_to_period_id IS NULL AND EXISTS (
                    SELECT 1 FROM budget_periods 
                    WHERE id = OLD.source_period_id AND status = 'CLOSED'
                )
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete advance ledger row for closed period');
            END;
        """.trimIndent())

        // 3. Budget Allocations Triggers (Flow.md §2.2)
        db.execSQL("""
            CREATE TRIGGER trg_budget_alloc_insert_closed
            BEFORE INSERT ON budget_allocations
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = NEW.budget_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot insert budget allocation into closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_budget_alloc_update_closed
            BEFORE UPDATE ON budget_allocations
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE (id = OLD.budget_period_id OR id = NEW.budget_period_id) 
                  AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot modify budget allocation in closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_budget_alloc_delete_closed
            BEFORE DELETE ON budget_allocations
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = OLD.budget_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete budget allocation from closed period');
            END;
        """.trimIndent())

        // 4. Budget Adjustments Triggers (Flow.md §2.2)
        db.execSQL("""
            CREATE TRIGGER trg_budget_adj_insert_closed
            BEFORE INSERT ON budget_adjustments
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = NEW.budget_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot insert budget adjustment into closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_budget_adj_update_closed
            BEFORE UPDATE ON budget_adjustments
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE (id = OLD.budget_period_id OR id = NEW.budget_period_id) 
                  AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot modify budget adjustment in closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_budget_adj_delete_closed
            BEFORE DELETE ON budget_adjustments
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = OLD.budget_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete budget adjustment from closed period');
            END;
        """.trimIndent())

        // 5. Transaction Allocations Triggers (Flow.md §2.2)
        db.execSQL("""
            CREATE TRIGGER trg_tx_alloc_insert_closed
            BEFORE INSERT ON transaction_allocations
            WHEN EXISTS (
                SELECT 1 FROM transactions t 
                JOIN budget_periods p ON (t.occurred_at >= p.start_at AND t.occurred_at < p.end_at)
                WHERE t.id = NEW.transaction_id AND p.status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot insert transaction allocation for closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_tx_alloc_update_closed
            BEFORE UPDATE ON transaction_allocations
            WHEN EXISTS (
                SELECT 1 FROM transactions t 
                JOIN budget_periods p ON (t.occurred_at >= p.start_at AND t.occurred_at < p.end_at)
                WHERE (t.id = OLD.transaction_id OR t.id = NEW.transaction_id) AND p.status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot modify transaction allocation for closed period');
            END;
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER trg_tx_alloc_delete_closed
            BEFORE DELETE ON transaction_allocations
            WHEN EXISTS (
                SELECT 1 FROM transactions t 
                JOIN budget_periods p ON (t.occurred_at >= p.start_at AND t.occurred_at < p.end_at)
                WHERE t.id = OLD.transaction_id AND p.status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete transaction allocation from closed period');
            END;
        """.trimIndent())

        // 6. Month Closes Triggers (Flow.md §2.2, §10.1)
        // Month close snapshot is immutable once written
        db.execSQL("""
            CREATE TRIGGER trg_month_close_update
            BEFORE UPDATE ON month_closes
            BEGIN
                SELECT RAISE(ABORT, 'Cannot modify month close record');
            END;
        """.trimIndent())

        // Month close snapshot can only be deleted if associated budget period is OPEN (during atomic Reopen)
        db.execSQL("""
            CREATE TRIGGER trg_month_close_delete_closed
            BEFORE DELETE ON month_closes
            WHEN EXISTS (
                SELECT 1 FROM budget_periods 
                WHERE id = OLD.budget_period_id AND status = 'CLOSED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'Cannot delete month close while period is closed');
            END;
        """.trimIndent())
    }

    val Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            createTriggers(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            createTriggers(db)
        }
    }
}
