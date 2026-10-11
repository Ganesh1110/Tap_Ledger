package com.tapledger.core.database

import com.tapledger.core.database.dao.CategoryDao
import com.tapledger.core.database.entity.CategoryEntity
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import com.tapledger.core.util.DateTimeUtils

/**
 * System reference categories per Flow.md §2.1 & §8.1.
 * Pure reference data with zero monetary amounts.
 */
object ReferenceCategories {
    val DEFAULTS = listOf(
        // Needs
        Triple("Groceries & Food", BudgetBucket.NEEDS, 1),
        Triple("Rent & Housing", BudgetBucket.NEEDS, 2),
        Triple("Utilities & Bills", BudgetBucket.NEEDS, 3),
        Triple("Transport & Fuel", BudgetBucket.NEEDS, 4),

        // Wants
        Triple("Dining Out", BudgetBucket.WANTS, 5),
        Triple("Entertainment & OTT", BudgetBucket.WANTS, 6),
        Triple("Personal Shopping", BudgetBucket.WANTS, 7),

        // Savings
        Triple("Emergency Fund", BudgetBucket.SAVINGS, 8),
        Triple("Investments", BudgetBucket.SAVINGS, 9)
    )

    suspend fun seedIfEmpty(categoryDao: CategoryDao): List<Long> {
        val existing = categoryDao.getAllCategoriesSync()
        if (existing.isNotEmpty()) {
            return existing.map { it.id }
        }
        val now = DateTimeUtils.nowEpochMillis()
        val entities = DEFAULTS.map { (name, bucket, order) ->
            CategoryEntity(
                name = name,
                defaultBucket = bucket,
                defaultSortOrder = order,
                defaultAdvancePolicy = AdvancePolicy.CARRY_TO_ADVANCE,
                createdAt = now,
                updatedAt = now
            )
        }
        return categoryDao.insertAll(entities)
    }
}
