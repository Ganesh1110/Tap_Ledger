package com.tapledger.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from Database Version 1 to Version 2.
 * Conforms all entities to Flow.md §2:
 * - accounts: credit_limit_paise, statement_day, due_day, is_savings; removes currency
 * - categories: system_key, sweep_target_category_id
 * - budget_periods: is_reviewed, closed_at
 * - transactions: nullable account_id, non-null direction, previous_status, original_occurred_at,
 *   counterparty_account_id, possible_duplicate_of_id, checkpoint_balance_paise, checkpoint_type,
 *   notes (renamed from note), UUID transfer_group_id
 * - transaction_allocations: composite unique index (transaction_id, category_id)
 * - advance_ledger: source_period_id, applied_to_period_id
 * - merchant_map: times_confirmed, times_overridden, last_used_at, composite unique index (merchant_normalized, category_id)
 * - unparsed_alerts: package_name, title, raw_text, occurred_at, error_reason, created_at
 * - statement_imports: total_debits_paise, total_credits_paise, line_count; removes format
 * - statement_lines: import_id, balance_after_paise; removes line_number, created_at
 * - month_closes: planned_income_paise, actual_income_paise, total_budgeted_paise, total_spent_paise,
 *   total_saved_paise, closing_balances_json
 * - transaction_audit_log: before_snapshot_json, after_snapshot_json, nullable transaction_id
 * - Recreates SQLite database integrity triggers (§2.2) and executes PRAGMA integrity_check.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. accounts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `identifier_last4` TEXT,
                `opening_balance_paise` INTEGER NOT NULL,
                `opening_balance_date` INTEGER NOT NULL,
                `credit_limit_paise` INTEGER,
                `statement_day` INTEGER,
                `due_day` INTEGER,
                `is_savings` INTEGER NOT NULL,
                `is_active` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `accounts_new` (
                `id`, `name`, `type`, `identifier_last4`, `opening_balance_paise`, `opening_balance_date`,
                `credit_limit_paise`, `statement_day`, `due_day`, `is_savings`, `is_active`, `created_at`, `updated_at`
            )
            SELECT `id`, `name`, `type`, `identifier_last4`, `opening_balance_paise`, `opening_balance_date`,
                   NULL, NULL, NULL, 0, `is_active`, `created_at`, `updated_at`
            FROM `accounts`
        """.trimIndent())
        db.execSQL("DROP TABLE `accounts`")
        db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_identifier_last4` ON `accounts` (`identifier_last4`)")

        // 2. categories
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `system_key` TEXT,
                `default_bucket` TEXT NOT NULL,
                `default_sort_order` INTEGER NOT NULL,
                `default_advance_policy` TEXT NOT NULL,
                `sweep_target_category_id` INTEGER,
                `is_active` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`sweep_target_category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `categories_new` (
                `id`, `name`, `system_key`, `default_bucket`, `default_sort_order`, `default_advance_policy`,
                `sweep_target_category_id`, `is_active`, `created_at`, `updated_at`
            )
            SELECT `id`, `name`, NULL, `default_bucket`, `default_sort_order`, `default_advance_policy`,
                   NULL, `is_active`, `created_at`, `updated_at`
            FROM `categories`
        """.trimIndent())
        db.execSQL("DROP TABLE `categories`")
        db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_system_key` ON `categories` (`system_key`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_sweep_target_category_id` ON `categories` (`sweep_target_category_id`)")

        // 3. budget_periods
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `budget_periods_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `year` INTEGER NOT NULL,
                `month` INTEGER NOT NULL,
                `start_at` INTEGER NOT NULL,
                `end_at` INTEGER NOT NULL,
                `income_paise` INTEGER NOT NULL,
                `is_reviewed` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `closed_at` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `budget_periods_new` (
                `id`, `year`, `month`, `start_at`, `end_at`, `income_paise`, `is_reviewed`, `status`, `closed_at`, `created_at`, `updated_at`
            )
            SELECT `id`, `year`, `month`, `start_at`, `end_at`, `income_paise`, 1, `status`, NULL, `created_at`, `updated_at`
            FROM `budget_periods`
        """.trimIndent())
        db.execSQL("DROP TABLE `budget_periods`")
        db.execSQL("ALTER TABLE `budget_periods_new` RENAME TO `budget_periods`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_periods_year_month` ON `budget_periods` (`year`, `month`)")

        // 4. transactions
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transactions_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `account_id` INTEGER,
                `kind` TEXT NOT NULL,
                `direction` TEXT NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                `merchant_raw` TEXT,
                `merchant_normalized` TEXT,
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
                `source_fingerprint` TEXT,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`counterparty_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`refund_of_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`possible_duplicate_of_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `transactions_new` (
                `id`, `account_id`, `kind`, `direction`, `amount_paise`, `merchant_raw`, `merchant_normalized`,
                `status`, `previous_status`, `source`, `occurred_at`, `original_occurred_at`, `upi_reference`,
                `transfer_group_id`, `counterparty_account_id`, `refund_of_transaction_id`, `possible_duplicate_of_id`,
                `checkpoint_balance_paise`, `checkpoint_type`, `notes`, `source_fingerprint`, `created_at`, `updated_at`
            )
            SELECT `id`, `account_id`, `kind`,
                   CASE
                       WHEN `kind` IN ('INCOME', 'REFUND', 'INTEREST_EARNED') THEN 'IN'
                       WHEN `kind` = 'TRANSFER' AND `transfer_direction` = 'IN' THEN 'IN'
                       ELSE 'OUT'
                   END,
                   `amount_paise`, `merchant_raw`, `merchant_normalized`,
                   `status`, NULL, `source`, `occurred_at`, NULL, `upi_reference`,
                   CASE WHEN `transfer_group_id` IS NOT NULL THEN CAST(`transfer_group_id` AS TEXT) ELSE NULL END,
                   NULL, `refund_of_transaction_id`, NULL,
                   NULL, NULL, `note`, `source_fingerprint`, `created_at`, `updated_at`
            FROM `transactions`
        """.trimIndent())
        db.execSQL("DROP TABLE `transactions`")
        db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_account_id` ON `transactions` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_occurred_at` ON `transactions` (`occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_status` ON `transactions` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transfer_group_id` ON `transactions` (`transfer_group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_upi_reference` ON `transactions` (`upi_reference`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_counterparty_account_id` ON `transactions` (`counterparty_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_refund_of_transaction_id` ON `transactions` (`refund_of_transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_possible_duplicate_of_id` ON `transactions` (`possible_duplicate_of_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_source_fingerprint` ON `transactions` (`source_fingerprint`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_account_id_upi_reference_direction_amount_paise` ON `transactions` (`account_id`, `upi_reference`, `direction`, `amount_paise`)")

        // 5. transaction_allocations
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transaction_allocations_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_id` INTEGER NOT NULL,
                `category_id` INTEGER NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `transaction_allocations_new` (`id`, `transaction_id`, `category_id`, `amount_paise`)
            SELECT `id`, `transaction_id`, `category_id`, `amount_paise`
            FROM `transaction_allocations`
        """.trimIndent())
        db.execSQL("DROP TABLE `transaction_allocations`")
        db.execSQL("ALTER TABLE `transaction_allocations_new` RENAME TO `transaction_allocations`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_allocations_transaction_id_category_id` ON `transaction_allocations` (`transaction_id`, `category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_allocations_category_id` ON `transaction_allocations` (`category_id`)")

        // 6. advance_ledger
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `advance_ledger_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `source_period_id` INTEGER NOT NULL,
                `applied_to_period_id` INTEGER,
                `category_id` INTEGER NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                `direction` TEXT NOT NULL,
                `reason` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`source_period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`applied_to_period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `advance_ledger_new` (
                `id`, `source_period_id`, `applied_to_period_id`, `category_id`, `amount_paise`, `direction`, `reason`, `created_at`
            )
            SELECT `id`, `budget_period_id`, NULL, `category_id`, `amount_paise`, `direction`, `reason`, `created_at`
            FROM `advance_ledger`
        """.trimIndent())
        db.execSQL("DROP TABLE `advance_ledger`")
        db.execSQL("ALTER TABLE `advance_ledger_new` RENAME TO `advance_ledger`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_advance_ledger_source_period_id` ON `advance_ledger` (`source_period_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_advance_ledger_applied_to_period_id` ON `advance_ledger` (`applied_to_period_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_advance_ledger_category_id` ON `advance_ledger` (`category_id`)")

        // 7. merchant_map
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `merchant_map_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `merchant_normalized` TEXT NOT NULL,
                `category_id` INTEGER NOT NULL,
                `times_confirmed` INTEGER NOT NULL,
                `times_overridden` INTEGER NOT NULL,
                `last_used_at` INTEGER NOT NULL,
                FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `merchant_map_new` (
                `id`, `merchant_normalized`, `category_id`, `times_confirmed`, `times_overridden`, `last_used_at`
            )
            SELECT `id`, `merchant_normalized`, `category_id`, 1, 0, `updated_at`
            FROM `merchant_map`
        """.trimIndent())
        db.execSQL("DROP TABLE `merchant_map`")
        db.execSQL("ALTER TABLE `merchant_map_new` RENAME TO `merchant_map`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_merchant_map_merchant_normalized_category_id` ON `merchant_map` (`merchant_normalized`, `category_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_merchant_map_category_id` ON `merchant_map` (`category_id`)")

        // 8. unparsed_alerts
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `unparsed_alerts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `package_name` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `raw_text` TEXT NOT NULL,
                `occurred_at` INTEGER NOT NULL,
                `error_reason` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `unparsed_alerts_new` (
                `id`, `package_name`, `title`, `raw_text`, `occurred_at`, `error_reason`, `created_at`
            )
            SELECT `id`, `sender_or_package`, `source`, `raw_redacted_preview`, `received_at`, `reason`, `created_at`
            FROM `unparsed_alerts`
        """.trimIndent())
        db.execSQL("DROP TABLE `unparsed_alerts`")
        db.execSQL("ALTER TABLE `unparsed_alerts_new` RENAME TO `unparsed_alerts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_unparsed_alerts_occurred_at` ON `unparsed_alerts` (`occurred_at`)")

        // 9. statement_imports
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `statement_imports_new` (
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
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `statement_imports_new` (
                `id`, `account_id`, `period_start`, `period_end`, `opening_balance_paise`, `closing_balance_paise`,
                `total_debits_paise`, `total_credits_paise`, `line_count`, `status`, `created_at`
            )
            SELECT `id`, `account_id`, `period_start`, `period_end`, `opening_balance_paise`, `closing_balance_paise`,
                   0, 0, 0, `status`, `created_at`
            FROM `statement_imports`
        """.trimIndent())
        db.execSQL("DROP TABLE `statement_imports`")
        db.execSQL("ALTER TABLE `statement_imports_new` RENAME TO `statement_imports`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_statement_imports_account_id` ON `statement_imports` (`account_id`)")

        // 10. statement_lines
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `statement_lines_new` (
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
                `match_status` TEXT NOT NULL,
                FOREIGN KEY(`import_id`) REFERENCES `statement_imports`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`matched_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `statement_lines_new` (
                `id`, `import_id`, `transaction_date`, `value_date`, `description`, `reference`,
                `debit_paise`, `credit_paise`, `balance_after_paise`, `matched_transaction_id`, `match_status`
            )
            SELECT `id`, `statement_import_id`, `transaction_date`, `value_date`, `description`, `reference`,
                   `debit_paise`, `credit_paise`, `balance_paise`, `matched_transaction_id`, `match_status`
            FROM `statement_lines`
        """.trimIndent())
        db.execSQL("DROP TABLE `statement_lines`")
        db.execSQL("ALTER TABLE `statement_lines_new` RENAME TO `statement_lines`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_statement_lines_import_id` ON `statement_lines` (`import_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_statement_lines_matched_transaction_id` ON `statement_lines` (`matched_transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_statement_lines_match_status` ON `statement_lines` (`match_status`)")

        // 11. month_closes
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `month_closes_new` (
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
                `closing_balances_json` TEXT NOT NULL,
                FOREIGN KEY(`budget_period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `month_closes_new` (
                `id`, `budget_period_id`, `closed_at`, `planned_income_paise`, `actual_income_paise`,
                `total_budgeted_paise`, `total_spent_paise`, `total_saved_paise`, `reconciliation_status`,
                `override_reason`, `closing_balances_json`
            )
            SELECT `id`, `budget_period_id`, `closed_at`, 0, 0, 0, 0, 0, `reconciliation_status`,
                   `override_reason`, '{}'
            FROM `month_closes`
        """.trimIndent())
        db.execSQL("DROP TABLE `month_closes`")
        db.execSQL("ALTER TABLE `month_closes_new` RENAME TO `month_closes`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_month_closes_budget_period_id` ON `month_closes` (`budget_period_id`)")

        // 12. transaction_audit_log
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transaction_audit_log_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transaction_id` INTEGER,
                `action` TEXT NOT NULL,
                `before_snapshot_json` TEXT,
                `after_snapshot_json` TEXT,
                `reason` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO `transaction_audit_log_new` (
                `id`, `transaction_id`, `action`, `before_snapshot_json`, `after_snapshot_json`, `reason`, `created_at`
            )
            SELECT `id`, `transaction_id`, `action`, `old_value_json`, `new_value_json`, `reason`, `created_at`
            FROM `transaction_audit_log`
        """.trimIndent())
        db.execSQL("DROP TABLE `transaction_audit_log`")
        db.execSQL("ALTER TABLE `transaction_audit_log_new` RENAME TO `transaction_audit_log`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_audit_log_transaction_id` ON `transaction_audit_log` (`transaction_id`)")

        // 13. Create database triggers (Flow.md §2.2)
        DatabaseTriggers.createTriggers(db)

        // 14. PRAGMA integrity_check
        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 1 -> 2: $result" }
            }
        }
    }
}

/**
 * Migration from Database Version 2 to Version 3.
 * Establishes Savings module data schema (Flow.md §18.2):
 * - savings_goals: id, name, target_paise, deadline_epoch_ms, priority, status, linked_account_id, created_at, updated_at
 * - savings_contributions: id, goal_id, amount_paise, source, transaction_id, period_id, note, created_at
 * - savings_plan_entries: id, period_id, goal_id, planned_paise, created_at
 * - recurring_commitments: id, type, name, amount_paise, due_day, account_id, goal_id, active, created_at
 * - commitment_payments: id, commitment_id, period_id, paid_transaction_id, status, created_at
 * - fixed_deposits: id, name, principal_paise, rate_bp, start_ms, maturity_ms, compounding, goal_id, created_at
 * - Adds indexes on goal_id, period_id, and transaction_id
 * - Establishes savings integrity triggers (§18.1, §18.2)
 * - Executes PRAGMA integrity_check
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. savings_goals
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `savings_goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `target_paise` INTEGER NOT NULL,
                `deadline_epoch_ms` INTEGER,
                `priority` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `linked_account_id` INTEGER,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                FOREIGN KEY(`linked_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_linked_account_id` ON `savings_goals` (`linked_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_status` ON `savings_goals` (`status`)")

        // 2. savings_contributions
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `savings_contributions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `goal_id` INTEGER NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `transaction_id` INTEGER,
                `period_id` INTEGER,
                `note` TEXT,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`goal_id`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_contributions_goal_id` ON `savings_contributions` (`goal_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_contributions_transaction_id` ON `savings_contributions` (`transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_contributions_period_id` ON `savings_contributions` (`period_id`)")

        // 3. savings_plan_entries
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `savings_plan_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `period_id` INTEGER NOT NULL,
                `goal_id` INTEGER NOT NULL,
                `planned_paise` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`goal_id`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_plan_entries_period_id_goal_id` ON `savings_plan_entries` (`period_id`, `goal_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_plan_entries_goal_id` ON `savings_plan_entries` (`goal_id`)")

        // 4. recurring_commitments
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `recurring_commitments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `amount_paise` INTEGER NOT NULL,
                `due_day` INTEGER NOT NULL,
                `account_id` INTEGER,
                `goal_id` INTEGER,
                `active` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`goal_id`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_commitments_account_id` ON `recurring_commitments` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_commitments_goal_id` ON `recurring_commitments` (`goal_id`)")

        // 5. commitment_payments
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `commitment_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `commitment_id` INTEGER NOT NULL,
                `period_id` INTEGER NOT NULL,
                `paid_transaction_id` INTEGER,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`commitment_id`) REFERENCES `recurring_commitments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`paid_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitment_payments_commitment_id` ON `commitment_payments` (`commitment_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitment_payments_period_id` ON `commitment_payments` (`period_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_commitment_payments_paid_transaction_id` ON `commitment_payments` (`paid_transaction_id`)")

        // 6. fixed_deposits
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `fixed_deposits` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `principal_paise` INTEGER NOT NULL,
                `rate_bp` INTEGER NOT NULL,
                `start_ms` INTEGER NOT NULL,
                `maturity_ms` INTEGER NOT NULL,
                `compounding` TEXT NOT NULL,
                `goal_id` INTEGER,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`goal_id`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_fixed_deposits_goal_id` ON `fixed_deposits` (`goal_id`)")

        // 7. Establish triggers
        DatabaseTriggers.createTriggers(db)

        // 8. PRAGMA integrity_check
        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 2 -> 3: $result" }
            }
        }
    }
}

/**
 * Migration from Database Version 3 to Version 4 (Milestones M18-M22: Savings Transfer Guide).
 * - Creates `savings_transfer_plans` table
 * - Unique composite index on `(period_id, kind)`
 * - Indices on `source_account_id`, `dest_account_id`, `transfer_group_id`, `trigger_transaction_id`, `status`
 * - Runs PRAGMA integrity_check
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `savings_transfer_plans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `period_id` INTEGER NOT NULL,
                `kind` TEXT NOT NULL,
                `source_account_id` INTEGER NOT NULL,
                `dest_account_id` INTEGER NOT NULL,
                `suggested_paise` INTEGER NOT NULL,
                `moved_paise` INTEGER,
                `status` TEXT NOT NULL,
                `transfer_group_id` TEXT,
                `trigger_transaction_id` INTEGER,
                `created_at` INTEGER NOT NULL,
                `moved_at` INTEGER,
                `verified_at` INTEGER,
                FOREIGN KEY(`period_id`) REFERENCES `budget_periods`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`source_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`dest_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`trigger_transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_transfer_plans_period_id_kind` ON `savings_transfer_plans` (`period_id`, `kind`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transfer_plans_source_account_id` ON `savings_transfer_plans` (`source_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transfer_plans_dest_account_id` ON `savings_transfer_plans` (`dest_account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transfer_plans_transfer_group_id` ON `savings_transfer_plans` (`transfer_group_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transfer_plans_trigger_transaction_id` ON `savings_transfer_plans` (`trigger_transaction_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transfer_plans_status` ON `savings_transfer_plans` (`status`)")

        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 3 -> 4: $result" }
            }
        }
    }
}

/**
 * Migration from Database Version 4 to Version 5.
 * - Adds `is_pinned` column (INTEGER NOT NULL DEFAULT 0) to `categories` table.
 * - Runs PRAGMA integrity_check.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `is_pinned` INTEGER NOT NULL DEFAULT 0")

        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 4 -> 5: $result" }
            }
        }
    }
}

/**
 * Migration from Database Version 5 to Version 6.
 * - Completely removes Bank Statement Reconciliation module: drops `statement_lines` and `statement_imports` tables.
 * - Runs PRAGMA integrity_check.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `statement_lines`")
        db.execSQL("DROP TABLE IF EXISTS `statement_imports`")

        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 5 -> 6: $result" }
            }
        }
    }
}

/**
 * Migration from Database Version 6 to Version 7.
 * - Creates `loans` table with foreign keys and indices for full loan setup, amortization, and EMI tracking.
 * - Runs PRAGMA integrity_check.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `loans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `account_id` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `principal_paise` INTEGER NOT NULL,
                `annual_interest_rate_bp` INTEGER NOT NULL,
                `tenure_months` INTEGER NOT NULL,
                `emi_day` INTEGER NOT NULL,
                `emi_amount_paise` INTEGER NOT NULL,
                `linked_bank_account_id` INTEGER,
                `start_date_ms` INTEGER NOT NULL,
                `is_active` INTEGER NOT NULL DEFAULT 1,
                `created_at` INTEGER NOT NULL,
                FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`linked_bank_account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_account_id` ON `loans` (`account_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_linked_bank_account_id` ON `loans` (`linked_bank_account_id`)")

        val cursor = db.query("PRAGMA integrity_check")
        cursor.use {
            if (it.moveToFirst()) {
                val result = it.getString(0)
                check(result == "ok") { "Database integrity check failed after migration 6 -> 7: $result" }
            }
        }
    }
}





