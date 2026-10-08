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
    private val database: TapLedgerDatabase,
    private val categoryDao: CategoryDao,
    private val budgetPeriodDao: BudgetPeriodDao,
    private val budgetAllocationDao: BudgetAllocationDao,
    private val budgetAdjustmentDao: BudgetAdjustmentDao
) {
    fun getAllActiveCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllActiveCategories()

    suspend fun getAllCategoriesSync(): List<CategoryEntity> = categoryDao.getAllCategoriesSync()

    suspend fun getCategoryById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun getOrCreateBudgetPeriod(year: Int, month: Int, defaultIncomePaise: Long = 52_000_00L): BudgetPeriodEntity {
        val existing = budgetPeriodDao.getByYearMonth(year, month)
        if (existing != null) return existing

        val startAt = DateTimeUtils.getStartOfMonthEpochMillis(year, month)
        val endAt = DateTimeUtils.getEndOfMonthEpochMillis(year, month)
        val now = DateTimeUtils.nowEpochMillis()

        return database.withTransaction {
            val periodEntity = BudgetPeriodEntity(
                year = year,
                month = month,
                startAt = startAt,
                endAt = endAt,
                incomePaise = defaultIncomePaise,
                status = BudgetPeriodStatus.OPEN,
                createdAt = now,
                updatedAt = now
            )
            val periodId = budgetPeriodDao.insert(periodEntity)
            val createdPeriod = periodEntity.copy(id = periodId)

            // Snapshot existing active categories into monthly allocations
            val categories = categoryDao.getAllCategoriesSync()
            val allocations = categories.map { category ->
                BudgetAllocationEntity(
                    budgetPeriodId = periodId,
                    categoryId = category.id,
                    categoryNameSnapshot = category.name,
                    bucketSnapshot = category.defaultBucket,
                    sortOrderSnapshot = category.defaultSortOrder,
                    advancePolicySnapshot = category.defaultAdvancePolicy,
                    amountPaise = 0L, // Initial base allocation
                    createdAt = now,
                    updatedAt = now
                )
            }
            if (allocations.isNotEmpty()) {
                budgetAllocationDao.insertAll(allocations)
            }

            createdPeriod
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
        return database.withTransaction {
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
