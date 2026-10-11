package com.tapledger.domain.onboarding

import com.tapledger.core.database.DatabaseTransactionRunner
import com.tapledger.core.database.ReferenceCategories
import com.tapledger.core.database.dao.AccountDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import com.tapledger.data.repository.BudgetRepository
import com.tapledger.domain.validator.DataIntegrityChecker
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes the atomic commit for first-run onboarding (Flow.md §15.1).
 *
 * Guarantees:
 * 1. All-or-nothing execution inside a single Room transaction (transactionRunner.run).
 * 2. ZERO transactions created.
 * 3. Credit cards stored as signed negative integers.
 * 4. Exactly one CASH account created.
 * 5. Opening balance date StartOfDayIST falls within current OPEN period.
 * 6. Second commit rejected without duplicates.
 * 7. Verified with DataIntegrityChecker reporting 0 violations across all 19 rules.
 */
@Singleton
class OnboardingCommitter @Inject constructor(
    private val transactionRunner: DatabaseTransactionRunner,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val budgetRepository: BudgetRepository,
    private val dataIntegrityChecker: DataIntegrityChecker
) {

    suspend fun commit(draft: OnboardingDraft) {
        val now = DateTimeUtils.nowEpochMillis()
        val today = LocalDate.now(DateTimeUtils.IST_ZONE)
        val currentYear = today.year
        val currentMonth = today.monthValue

        val startOfPeriod = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth)
        val endOfPeriod = DateTimeUtils.getEndOfMonthEpochMillis(currentYear, currentMonth)

        // 1. Validation Rules
        if (draft.monthlyIncomePaise <= 0L) {
            throw IllegalArgumentException("Monthly income must be strictly greater than ₹0")
        }

        val asOfDateStartOfDay = DateTimeUtils.getStartOfDayEpochMillis(
            if (draft.asOfDateEpochMillis > 0L) draft.asOfDateEpochMillis else now
        )

        if (asOfDateStartOfDay < startOfPeriod || asOfDateStartOfDay >= endOfPeriod) {
            throw IllegalArgumentException("Opening balance date must fall within the current budget period ($currentYear-$currentMonth)")
        }

        // Account name uniqueness check
        val allAccountNames = mutableListOf<String>()
        draft.bankAccounts.forEach { acc ->
            val err = OnboardingValidator.validateAccountName(acc.name, allAccountNames)
            if (err != null) throw IllegalArgumentException(err)
            allAccountNames.add(acc.name.trim())
        }

        val cashName = "Cash"
        val cashErr = OnboardingValidator.validateAccountName(cashName, allAccountNames)
        if (cashErr != null) throw IllegalArgumentException(cashErr)
        allAccountNames.add(cashName)

        draft.creditCards.forEach { card ->
            val err = OnboardingValidator.validateAccountName(card.name, allAccountNames)
            if (err != null) throw IllegalArgumentException(err)
            allAccountNames.add(card.name.trim())
        }

        // 2. Atomic Database Execution
        transactionRunner.run {
            // Idempotency: Reject if already onboarded
            val activeAccounts = accountDao.getAllAccountsSync().filter { it.isActive }
            val existingPeriods = budgetPeriodDao.getAllPeriodsSync()
            if (activeAccounts.isNotEmpty() && existingPeriods.isNotEmpty()) {
                throw IllegalStateException("Application has already completed onboarding")
            }

            // Ensure reference categories exist
            ReferenceCategories.seedIfEmpty(categoryDao)
            val allCategories = categoryDao.getAllCategoriesSync()
            val categoryById = allCategories.associateBy { it.id }

            // Insert accounts
            val accountsToInsert = mutableListOf<AccountEntity>()

            // Bank / Wallet accounts
            for (acc in draft.bankAccounts) {
                if (acc.balancePaise < 0L) {
                    throw IllegalArgumentException("Bank/Wallet opening balance cannot be negative")
                }
                accountsToInsert.add(
                    AccountEntity(
                        name = acc.name.trim(),
                        type = acc.type,
                        identifierLast4 = acc.identifierLast4?.trim()?.ifEmpty { null },
                        openingBalancePaise = acc.balancePaise,
                        openingBalanceDate = asOfDateStartOfDay,
                        isSavings = acc.isSavings,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            // Always create exactly one CASH account
            if (draft.cashBalancePaise < 0L) {
                throw IllegalArgumentException("Cash balance cannot be negative")
            }
            accountsToInsert.add(
                AccountEntity(
                    name = cashName,
                    type = AccountType.CASH,
                    identifierLast4 = null,
                    openingBalancePaise = draft.cashBalancePaise,
                    openingBalanceDate = asOfDateStartOfDay,
                    isSavings = false,
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // Credit cards (stored negative)
            for (card in draft.creditCards) {
                if (card.outstandingOwedPaise < 0L) {
                    throw IllegalArgumentException("Credit card outstanding cannot be negative")
                }
                // Entered as positive, stored as signed negative per Flow.md §6.1 rule 4
                val storedOpeningBalance = if (card.outstandingOwedPaise == 0L) 0L else -card.outstandingOwedPaise
                accountsToInsert.add(
                    AccountEntity(
                        name = card.name.trim(),
                        type = AccountType.CREDIT_CARD,
                        identifierLast4 = card.identifierLast4?.trim()?.ifEmpty { null },
                        openingBalancePaise = storedOpeningBalance,
                        openingBalanceDate = asOfDateStartOfDay,
                        creditLimitPaise = card.creditLimitPaise,
                        statementDay = card.statementDay,
                        dueDay = card.dueDay,
                        isSavings = false,
                        isActive = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            accountDao.insertAll(accountsToInsert)

            // Get-or-create current budget period with planned income
            val period = budgetRepository.getOrCreateBudgetPeriod(
                year = currentYear,
                month = currentMonth,
                defaultIncomePaise = draft.monthlyIncomePaise
            )

            // Mark period reviewed and set income
            budgetPeriodDao.update(
                period.copy(
                    incomePaise = draft.monthlyIncomePaise,
                    isReviewed = true,
                    updatedAt = now
                )
            )

            // Insert base allocations from O4
            for ((categoryId, amountPaise) in draft.categoryAllocations) {
                val cat = categoryById[categoryId] ?: continue
                val existingAlloc = budgetAllocationDao.getAllocation(period.id, categoryId)
                if (existingAlloc != null) {
                    budgetAllocationDao.update(
                        existingAlloc.copy(
                            amountPaise = amountPaise,
                            updatedAt = now
                        )
                    )
                } else {
                    budgetAllocationDao.insert(
                        BudgetAllocationEntity(
                            budgetPeriodId = period.id,
                            categoryId = categoryId,
                            categoryNameSnapshot = cat.name,
                            bucketSnapshot = cat.defaultBucket,
                            sortOrderSnapshot = cat.defaultSortOrder,
                            advancePolicySnapshot = cat.defaultAdvancePolicy,
                            amountPaise = amountPaise,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            }

            // Verify DataIntegrityChecker across all 19 rules
            val savedAccounts = accountDao.getAllAccountsSync()
            val savedCategories = categoryDao.getAllCategoriesSync()
            val savedPeriods = budgetPeriodDao.getAllPeriodsSync()
            val savedAllocations = budgetAllocationDao.getAllocationsForPeriodSync(period.id)

            val issues = dataIntegrityChecker.verifyIntegrity(
                accounts = savedAccounts,
                categories = savedCategories,
                periods = savedPeriods,
                allocations = savedAllocations,
                transactions = emptyList(),
                transactionAllocations = emptyList(),
                monthCloses = emptyList()
            )

            if (issues.isNotEmpty()) {
                throw IllegalStateException("Data integrity invariant failure on onboarding commit: ${issues.joinToString { it.message }}")
            }
        }
    }
}
