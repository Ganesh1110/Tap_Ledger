package com.tapledger.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationFlowTest {

    @Test
    fun testNavigationGraph() {
        // 1. Verify 4 Core Pillars in Bottom Navigation per Flow.md §15
        val bottomNavRoutes = BOTTOM_NAV_ITEMS.map { it.route }
        assertThat(bottomNavRoutes).containsExactly(
            Screen.Dashboard.route,
            Screen.Transactions.route,
            Screen.CategoryManagement.route,
            Screen.Accounts.route
        ).inOrder()

        val bottomNavTitles = BOTTOM_NAV_ITEMS.map { it.title }
        assertThat(bottomNavTitles).containsExactly(
            "Home",
            "Transactions",
            "Budget",
            "Accounts"
        ).inOrder()

        // 2. Verify all 11 sub-screens and workflow destinations exist per Flow.md §15
        val screens = listOf(
            Screen.Dashboard,
            Screen.Transactions,
            Screen.ManualEntry,
            Screen.PendingInbox,
            Screen.Accounts,
            Screen.BackupRestore,
            Screen.Setup,
            Screen.CategoryManagement,
            Screen.StatementReconciliation,
            Screen.MonthClose,
            Screen.DataDiagnostics,
            Screen.Insights
        )

        val routes = screens.map { it.route }
        assertThat(routes).contains("dashboard")
        assertThat(routes).contains("transactions")
        assertThat(routes).contains("manual_entry")
        assertThat(routes).contains("pending_inbox")
        assertThat(routes).contains("accounts")
        assertThat(routes).contains("backup_restore")
        assertThat(routes).contains("setup")
        assertThat(routes).contains("categories")
        assertThat(routes).contains("statements")
        assertThat(routes).contains("month_close")
        assertThat(routes).contains("diagnostics")
        assertThat(routes).contains("insights")

        // Unique routes invariant
        assertThat(routes.distinct().size).isEqualTo(screens.size)
    }
}
