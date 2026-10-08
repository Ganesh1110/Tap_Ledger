package com.tapledger.core.database

import androidx.room.withTransaction
import com.tapledger.core.database.entity.AccountEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AccountType
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: TapLedgerDatabase
) {

    suspend fun seedInitialDataIfEmpty() {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val periodDao = database.budgetPeriodDao()
        val allocationDao = database.budgetAllocationDao()

        if (accountDao.getAllAccountsSync().isNotEmpty()) {
            return // Already initialized
        }

        val now = DateTimeUtils.nowEpochMillis()
        val today = LocalDate.now(DateTimeUtils.IST_ZONE)
        val currentYear = today.year
        val currentMonth = today.monthValue

        database.withTransaction {
            // 1. Seed Accounts (Section 8)
            val accounts = listOf(
                AccountEntity(
                    name = "HDFC Savings",
                    type = AccountType.BANK,
                    identifierLast4 = "1234",
                    openingBalancePaise = 50_000_00L, // +₹50,000
                    openingBalanceDate = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth),
                    createdAt = now,
                    updatedAt = now
                ),
                AccountEntity(
                    name = "SBI Savings",
                    type = AccountType.BANK,
                    identifierLast4 = "5678",
                    openingBalancePaise = 10_000_00L, // +₹10,000
                    openingBalanceDate = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth),
                    createdAt = now,
                    updatedAt = now
                ),
                AccountEntity(
                    name = "HDFC Credit Card",
                    type = AccountType.CREDIT_CARD,
                    identifierLast4 = "9012",
                    openingBalancePaise = -8_000_00L, // -₹8,000 liability
                    openingBalanceDate = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth),
                    createdAt = now,
                    updatedAt = now
                ),
                AccountEntity(
                    name = "Cash",
                    type = AccountType.CASH,
                    identifierLast4 = null,
                    openingBalancePaise = 2_000_00L, // +₹2,000
                    openingBalanceDate = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth),
                    createdAt = now,
                    updatedAt = now
                )
            )
            accountDao.insertAll(accounts)

            // 2. Seed Categories (Section 6)
            // Needs: ₹36,000, Wants: ₹8,000, Savings: ₹6,000, Unallocated: ₹2,000 -> Total: ₹52,000
            val categoriesWithInitialBudget = listOf(
                // Needs (Total ₹36,000)
                Triple("Groceries & Food", BudgetBucket.NEEDS, 16_000_00L),
                Triple("Rent & Housing", BudgetBucket.NEEDS, 12_000_00L),
                Triple("Utilities & Bills", BudgetBucket.NEEDS, 5_000_00L),
                Triple("Transport & Fuel", BudgetBucket.NEEDS, 3_000_00L),

                // Wants (Total ₹8,000)
                Triple("Dining Out", BudgetBucket.WANTS, 4_000_00L),
                Triple("Entertainment & OTT", BudgetBucket.WANTS, 2_000_00L),
                Triple("Personal Shopping", BudgetBucket.WANTS, 2_000_00L),

                // Savings (Total ₹6,000)
                Triple("Emergency Fund", BudgetBucket.SAVINGS, 3_000_00L),
                Triple("Investments", BudgetBucket.SAVINGS, 3_000_00L)
            )

            val categoryEntities = categoriesWithInitialBudget.mapIndexed { index, (name, bucket, _) ->
                CategoryEntity(
                    name = name,
                    defaultBucket = bucket,
                    defaultSortOrder = index + 1,
                    defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
                    createdAt = now,
                    updatedAt = now
                )
            }
            val insertedCategoryIds = categoryDao.insertAll(categoryEntities)

            // 3. Seed current Budget Period (Section 6, 7)
            val period = BudgetPeriodEntity(
                year = currentYear,
                month = currentMonth,
                startAt = DateTimeUtils.getStartOfMonthEpochMillis(currentYear, currentMonth),
                endAt = DateTimeUtils.getEndOfMonthEpochMillis(currentYear, currentMonth),
                incomePaise = 52_000_00L, // ₹52,000
                status = BudgetPeriodStatus.OPEN,
                createdAt = now,
                updatedAt = now
            )
            val periodId = periodDao.insert(period)

            // 4. Seed Monthly Budget Allocations Snapshot (Section 7, 10.4)
            val allocations = categoriesWithInitialBudget.mapIndexed { index, (name, bucket, amountPaise) ->
                val catId = insertedCategoryIds[index]
                BudgetAllocationEntity(
                    budgetPeriodId = periodId,
                    categoryId = catId,
                    categoryNameSnapshot = name,
                    bucketSnapshot = bucket,
                    sortOrderSnapshot = index + 1,
                    advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
                    amountPaise = amountPaise,
                    createdAt = now,
                    updatedAt = now
                )
            }
            allocationDao.insertAll(allocations)
        }
    }
}
