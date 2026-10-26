package com.tapledger.ui.components

import androidx.compose.ui.Modifier
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Tests verifying the implementation and invariants of Keyboard Aware Views across TapLedger screens.
 */
class KeyboardAwareTest {

    @Test
    fun testKeyboardAwareModifier_FalseFlags_ReturnsOriginalModifier() {
        val base = Modifier
        val modified = base.keyboardAware(imePadding = false, imeNestedScroll = false)
        // With both false, identity modifier is returned without touching Android-only internals
        assertThat(modified).isEqualTo(base)
    }

    @Test
    fun testOnboardingScreen_HasKeyboardAwareAndVerticalScroll() {
        val file = File("src/main/java/com/tapledger/ui/screens/onboarding/OnboardingScreen.kt")
        assertThat(file.exists()).isTrue()
        val content = file.readText()

        // 1. Box container has consumeWindowInsets and keyboardAware()
        assertThat(content).contains(".consumeWindowInsets(paddingValues)")
        assertThat(content).contains(".keyboardAware()")
        // 2. StepIncome has verticalScroll and ImeAction.Done
        assertThat(content).contains("verticalScroll(rememberScrollState())")
        assertThat(content).contains("ImeAction.Done")
        // 3. StepAccounts and StepBudget have contentPadding for bottom scroll safety
        assertThat(content).contains("contentPadding = PaddingValues(bottom = Spacing.listBottomPadding)")
    }

    @Test
    fun testTransactionsScreen_HasKeyboardAwareAndHidesFabOnKeyboard() {
        val file = File("src/main/java/com/tapledger/ui/screens/transactions/TransactionsScreen.kt")
        assertThat(file.exists()).isTrue()
        val content = file.readText()

        // 1. Column has keyboardAware()
        assertThat(content).contains(".keyboardAware()")
        // 2. FAB is conditionally hidden when keyboard is visible
        assertThat(content).contains("if (!isKeyboardVisible())")
    }

    @Test
    fun testMainScaffold_HidesBottomNavWhenKeyboardOpen() {
        val file = File("src/main/java/com/tapledger/ui/navigation/MainScaffold.kt")
        assertThat(file.exists()).isTrue()
        val content = file.readText()

        // 1. Queries isKeyboardVisible()
        assertThat(content).contains("isKeyboardVisible()")
        // 2. Suppresses bottom navigation when keyboard is open
        assertThat(content).contains("if (isPrimaryDestination && !isKeyboardOpen)")
    }

    @Test
    fun testManualEntryScreen_HasImeNestedScroll() {
        val file = File("src/main/java/com/tapledger/ui/screens/manual/ManualEntryScreen.kt")
        assertThat(file.exists()).isTrue()
        val content = file.readText()

        // 1. Sticky bottomBar uses imePadding()
        assertThat(content).contains(".imePadding()")
        // 2. Scrollable column uses keyboardAware nested scroll
        assertThat(content).contains(".keyboardAware(imePadding = false, imeNestedScroll = true)")
    }

    @Test
    fun testDialogsAreScrollableForKeyboardSafety() {
        val accountsFile = File("src/main/java/com/tapledger/ui/screens/accounts/AccountsScreen.kt")
        assertThat(accountsFile.readText()).contains("verticalScroll(rememberScrollState())")

        val backupFile = File("src/main/java/com/tapledger/ui/screens/backup/BackupRestoreScreen.kt")
        assertThat(backupFile.readText()).contains("verticalScroll(rememberScrollState())")

        val monthCloseFile = File("src/main/java/com/tapledger/ui/screens/monthclose/MonthCloseScreen.kt")
        assertThat(monthCloseFile.readText()).contains("verticalScroll(rememberScrollState())")

        val categoriesFile = File("src/main/java/com/tapledger/ui/screens/categories/CategoryManagementScreen.kt")
        assertThat(categoriesFile.readText()).contains("verticalScroll(rememberScrollState())")
    }
}
