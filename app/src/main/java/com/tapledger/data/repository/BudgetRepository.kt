package com.tapledger.data.repository

import androidx.room.withTransaction
import com.tapledger.core.database.TapLedgerDatabase
import com.tapledger.core.database.dao.BudgetAdjustmentDao
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.dao.BudgetPeriodDao
import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.database.entity.BudgetPeriodEntity
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.model.BudgetPeriodStatus
import com.tapledger.core.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val transactionRunner: com.tapledger.core.database.DatabaseTransactionRunner,
    private val categoryDao: CategoryDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao
) {
    fun getAllActiveCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllActiveCategories()

    suspend fun getAllCategoriesSync(): List<CategoryEntity> = categoryDao.getAllCategoriesSync()

    suspend fun getCategoryById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun getOrCreateBudgetPeriodForTimestamp(timestamp: Long): BudgetPeriodEntity {
        val ym = DateTimeUtils.toYearMonth(timestamp)
        return getOrCreateBudgetPeriod(ym.year, ym.monthValue)
    }

    suspend fun getOrCreateBudgetPeriod(year: Int, month: Int, defaultIncomePaise: Long = 52_000_00L): BudgetPeriodEntity {
        val existing = budgetPeriodDao.getByYearMonth(year, month)
        if (existing != null) return existing

        return transactionRunner.run {
            val inTx = budgetPeriodDao.getByYearMonth(year, month)
            if (inTx != null) return@run inTx

            val latestExisting = budgetPeriodDao.getLatestPeriod()
            if (latestExisting == null) {
                createSinglePeriod(year, month, defaultIncomePaise, null)
            } else {
                val latestYm = java.time.YearMonth.of(latestExisting.year, latestExisting.month)
                val targetYm = java.time.YearMonth.of(year, month)
                if (targetYm.isAfter(latestYm)) {
                    var currentYm = latestYm.plusMonths(1)
                    var lastPeriod: BudgetPeriodEntity = latestExisting
                    while (!currentYm.isAfter(targetYm)) {
                        val existingAtCurr = budgetPeriodDao.getByYearMonth(currentYm.year, currentYm.monthValue)
                        if (existingAtCurr != null) {
                            lastPeriod = existingAtCurr
                        } else {
                            val newPeriod = createSinglePeriod(
                                currentYm.year,
                                currentYm.monthValue,
                                lastPeriod.incomePaise,
                                lastPeriod.id
                            )
                            lastPeriod = newPeriod
                        }
                        currentYm = currentYm.plusMonths(1)
                    }
                    budgetPeriodDao.getByYearMonth(year, month)!!
                } else {
                    createSinglePeriod(year, month, latestExisting.incomePaise, latestExisting.id)
                }
            }
        }
    }

    private suspend fun createSinglePeriod(
        year: Int,
        month: Int,
        incomePaise: Long,
        templatePeriodId: Long?
    ): BudgetPeriodEntity {
        val startAt = DateTimeUtils.getStartOfMonthEpochMillis(year, month)
        val endAt = DateTimeUtils.getEndOfMonthEpochMillis(year, month)
        val now = DateTimeUtils.nowEpochMillis()

        val periodEntity = BudgetPeriodEntity(
            year = year,
            month = month,
            startAt = startAt,
            endAt = endAt,
            incomePaise = incomePaise,
            status = BudgetPeriodStatus.OPEN,
            isReviewed = false,
            createdAt = now,
            updatedAt = now
        )
        val periodId = budgetPeriodDao.insert(periodEntity)
        val createdPeriod = periodEntity.copy(id = periodId)

        val allocations = if (templatePeriodId != null) {
            val templateAllocations = budgetAllocationDao.getAllocationsForPeriodSync(templatePeriodId)
            if (templateAllocations.isNotEmpty()) {
                templateAllocations.map { template ->
                    BudgetAllocationEntity(
                        budgetPeriodId = periodId,
                        categoryId = template.categoryId,
                        categoryNameSnapshot = template.categoryNameSnapshot,
                        bucketSnapshot = template.bucketSnapshot,
                        sortOrderSnapshot = template.sortOrderSnapshot,
                        advancePolicySnapshot = template.advancePolicySnapshot,
                        amountPaise = template.amountPaise,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            } else {
                snapshotCategories(periodId, now)
            }
        } else {
            snapshotCategories(periodId, now)
        }

        if (allocations.isNotEmpty()) {
            budgetAllocationDao.insertAll(allocations)
        }

        return createdPeriod
    }

    private suspend fun snapshotCategories(periodId: Long, now: Long): List<BudgetAllocationEntity> {
        val categories = categoryDao.getAllCategoriesSync()
        return categories.map { category ->
            BudgetAllocationEntity(
                budgetPeriodId = periodId,
                categoryId = category.id,
                categoryNameSnapshot = category.name,
                bucketSnapshot = category.defaultBucket,
                sortOrderSnapshot = category.defaultSortOrder,
                advancePolicySnapshot = category.defaultAdvancePolicy,
                amountPaise = 0L,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> =
        budgetAllocationDao.observeAllocationsForPeriod(periodId)

    suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> =
        budgetAllocationDao.getAllocationsForPeriodSync(periodId)

    fun observeAdjustmentsForPeriod(periodId: Long): Flow<List<BudgetAdjustmentEntity>> =
        budgetAdjustmentDao.observeAdjustmentsForPeriod(periodId)

    suspend fun getAdjustmentsForPeriodSync(periodId: Long): List<BudgetAdjustmentEntity> =
        budgetAdjustmentDao.getAdjustmentsForPeriodSync(periodId)

    suspend fun recordBudgetAdjustment(
        periodId: Long,
        fromCategoryId: Long,
        toCategoryId: Long,
        amountPaise: Long,
        reason: String?
    ): Long {
        require(amountPaise > 0L) { "Adjustment amount must be positive" }
        require(fromCategoryId != toCategoryId) { "Cannot adjust to the same category" }
        val adjustment = BudgetAdjustmentEntity(
            budgetPeriodId = periodId,
            fromCategoryId = fromCategoryId,
            toCategoryId = toCategoryId,
            amountPaise = amountPaise,
            reason = reason,
            createdAt = DateTimeUtils.nowEpochMillis()
        )
        return budgetAdjustmentDao.insert(adjustment)
    }

    suspend fun updateBudgetAllocationAmount(periodId: Long, categoryId: Long, newAmountPaise: Long) {
        val existing = budgetAllocationDao.getAllocation(periodId, categoryId)
        if (existing != null) {
            val updated = existing.copy(
                amountPaise = newAmountPaise,
                updatedAt = DateTimeUtils.nowEpochMillis()
            )
            budgetAllocationDao.update(updated)
        }
    }

    suspend fun createCategory(
        name: String,
        defaultBucket: BudgetBucket,
        defaultAdvancePolicy: com.tapledger.core.model.AdvancePolicy,
        initialAllocationPaise: Long,
        periodId: Long?
    ): Long {
        val now = DateTimeUtils.nowEpochMillis()
        val nextSortOrder = (categoryDao.getMaxSortOrder() ?: 0) + 1
        return transactionRunner.run {
            val category = CategoryEntity(
                name = name.trim(),
                defaultBucket = defaultBucket,
                defaultSortOrder = nextSortOrder,
                defaultAdvancePolicy = defaultAdvancePolicy,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val categoryId = categoryDao.insert(category)

            if (periodId != null) {
                val allocation = BudgetAllocationEntity(
                    budgetPeriodId = periodId,
                    categoryId = categoryId,
                    categoryNameSnapshot = name.trim(),
                    bucketSnapshot = defaultBucket,
                    sortOrderSnapshot = nextSortOrder,
                    advancePolicySnapshot = defaultAdvancePolicy,
                    amountPaise = initialAllocationPaise,
                    createdAt = now,
                    updatedAt = now
                )
                budgetAllocationDao.insert(allocation)
            }
            categoryId
        }
    }

    suspend fun updateCategory(category: CategoryEntity) {
        val updated = category.copy(updatedAt = DateTimeUtils.nowEpochMillis())
        categoryDao.update(updated)
    }

    suspend fun deactivateCategory(categoryId: Long) {
        val category = categoryDao.getById(categoryId) ?: return
        val updated = category.copy(
            isActive = false,
            updatedAt = DateTimeUtils.nowEpochMillis()
        )
        categoryDao.update(updated)
    }
}
