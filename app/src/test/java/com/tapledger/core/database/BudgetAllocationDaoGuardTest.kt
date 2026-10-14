package com.tapledger.core.database

import com.google.common.truth.Truth.assertThat
import com.tapledger.core.database.dao.BudgetAllocationDao
import com.tapledger.core.database.entity.AdvanceLedgerEntity
import com.tapledger.core.database.entity.BudgetAdjustmentEntity
import com.tapledger.core.database.entity.BudgetAllocationEntity
import com.tapledger.core.model.AdvanceDirection
import com.tapledger.core.model.AdvancePolicy
import com.tapledger.core.model.BudgetBucket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Validates Flow.md §8.2 DAO Invariant Guard (Rule B2 Protection):
 * BaseAllocated(C) >= AdvanceNegative(C) + AdjOut(C) - AdjIn(C) - AdvancePositive(C)
 *
 * Ensures that any attempt by the application layer to insert or update a base allocation
 * below the minimum committed deficit/adjustments throws IllegalArgumentException.
 */
class BudgetAllocationDaoGuardTest {

    private class TestBudgetAllocationDao : BudgetAllocationDao() {
        var currentAdjustments: List<BudgetAdjustmentEntity> = emptyList()
        var currentAdvances: List<AdvanceLedgerEntity> = emptyList()
        val insertedOrUpdated = mutableListOf<BudgetAllocationEntity>()

        override suspend fun getAdjustmentsForPeriod(periodId: Long): List<BudgetAdjustmentEntity> =
            currentAdjustments.filter { it.budgetPeriodId == periodId }

        override suspend fun getAdvancesForPeriod(periodId: Long): List<AdvanceLedgerEntity> =
            currentAdvances.filter { it.appliedToPeriodId == periodId }

        override suspend fun insertInternal(allocation: BudgetAllocationEntity): Long {
            insertedOrUpdated.add(allocation)
            return allocation.id
        }

        override suspend fun insertAllInternal(allocations: List<BudgetAllocationEntity>): List<Long> {
            insertedOrUpdated.addAll(allocations)
            return allocations.map { it.id }
        }

        override suspend fun updateInternal(allocation: BudgetAllocationEntity) {
            insertedOrUpdated.add(allocation)
        }

        override fun observeAllocationsForPeriod(periodId: Long): Flow<List<BudgetAllocationEntity>> = emptyFlow()
        override suspend fun getAllocationsForPeriodSync(periodId: Long): List<BudgetAllocationEntity> = emptyList()
        override suspend fun getAllAllocationsSync(): List<BudgetAllocationEntity> = emptyList()
        override suspend fun getAllocation(periodId: Long, categoryId: Long): BudgetAllocationEntity? = null
        override suspend fun deleteAllocation(periodId: Long, categoryId: Long) {}
    }

    private fun createAllocation(
        periodId: Long = 100L,
        categoryId: Long = 10L,
        amountPaise: Long
    ) = BudgetAllocationEntity(
        id = 1L,
        budgetPeriodId = periodId,
        categoryId = categoryId,
        categoryNameSnapshot = "Groceries",
        bucketSnapshot = BudgetBucket.NEEDS,
        sortOrderSnapshot = 1,
        advancePolicySnapshot = AdvancePolicy.CARRY_TO_ADVANCE,
        amountPaise = amountPaise,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun guardAllowsAllocation_whenNoAdjustmentsOrAdvancesExist() = runBlocking {
        val dao = TestBudgetAllocationDao()
        val alloc = createAllocation(amountPaise = 0L)

        dao.insert(alloc)
        assertThat(dao.insertedOrUpdated).contains(alloc)
    }

    @Test
    fun guardAllowsAllocation_whenPositiveHeadroomExists() = runBlocking {
        val dao = TestBudgetAllocationDao()
        // AdvPos: 2000, AdjIn: 1000 -> minRequiredBase = 0 - 1000 - 2000 = -3000
        dao.currentAdvances = listOf(
            AdvanceLedgerEntity(
                id = 1,
                sourcePeriodId = 99L,
                appliedToPeriodId = 100L,
                categoryId = 10L,
                amountPaise = 2000L,
                direction = AdvanceDirection.POSITIVE,
                reason = "Carryover",
                createdAt = 1000L
            )
        )
        dao.currentAdjustments = listOf(
            BudgetAdjustmentEntity(
                id = 1,
                budgetPeriodId = 100L,
                fromCategoryId = 20L,
                toCategoryId = 10L,
                amountPaise = 1000L,
                createdAt = 1000L
            )
        )

        // Allocation of 0 paise is completely valid
        val alloc = createAllocation(amountPaise = 0L)
        dao.insert(alloc)
        assertThat(dao.insertedOrUpdated).contains(alloc)
    }

    @Test
    fun guardEnforcesExactThreshold_forDeficitAbsorption() = runBlocking {
        val dao = TestBudgetAllocationDao()
        // AdvNeg = 5000 paise (deficit applied from previous month)
        dao.currentAdvances = listOf(
            AdvanceLedgerEntity(
                id = 1,
                sourcePeriodId = 99L,
                appliedToPeriodId = 100L,
                categoryId = 10L,
                amountPaise = 5000L,
                direction = AdvanceDirection.NEGATIVE,
                createdAt = 1000L
            )
        )

        // 1. Boundary pass: Base == 5000
        val exactAlloc = createAllocation(amountPaise = 5000L)
        dao.insert(exactAlloc)
        assertThat(dao.insertedOrUpdated).contains(exactAlloc)

        // 2. Above boundary: Base == 5001
        val safeAlloc = createAllocation(amountPaise = 5001L)
        dao.update(safeAlloc)
        assertThat(dao.insertedOrUpdated).contains(safeAlloc)

        // 3. Below boundary: Base == 4999 -> throws IllegalArgumentException
        val failingAlloc = createAllocation(amountPaise = 4999L)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.insert(failingAlloc) }
        }
        assertThat(ex.message).contains("cannot be reduced below committed net negative adjustments (5000 paise)")
    }

    @Test
    fun guardCalculatesCombinedFormula_withDeficitsAndTransfers() = runBlocking {
        val dao = TestBudgetAllocationDao()
        // Formula: Base >= AdvNeg (8000) + AdjOut (4000) - AdjIn (2000) - AdvPos (3000)
        // Required Base >= 8000 + 4000 - 2000 - 3000 = 7000 paise
        dao.currentAdvances = listOf(
            AdvanceLedgerEntity(
                id = 1,
                sourcePeriodId = 99L,
                appliedToPeriodId = 100L,
                categoryId = 10L,
                amountPaise = 8000L,
                direction = AdvanceDirection.NEGATIVE,
                reason = "Deficit",
                createdAt = 1000L
            ),
            AdvanceLedgerEntity(
                id = 2,
                sourcePeriodId = 99L,
                appliedToPeriodId = 100L,
                categoryId = 10L,
                amountPaise = 3000L,
                direction = AdvanceDirection.POSITIVE,
                reason = "Surplus",
                createdAt = 1000L
            )
        )
        dao.currentAdjustments = listOf(
            BudgetAdjustmentEntity(
                id = 1,
                budgetPeriodId = 100L,
                fromCategoryId = 10L, // AdjOut
                toCategoryId = 30L,
                amountPaise = 4000L,
                createdAt = 1000L
            ),
            BudgetAdjustmentEntity(
                id = 2,
                budgetPeriodId = 100L,
                fromCategoryId = 40L,
                toCategoryId = 10L, // AdjIn
                amountPaise = 2000L,
                createdAt = 1000L
            )
        )

        // At 7000 paise: succeeds
        val validAlloc = createAllocation(amountPaise = 7000L)
        dao.insert(validAlloc)
        assertThat(dao.insertedOrUpdated).contains(validAlloc)

        // At 6999 paise: fails
        val invalidAlloc = createAllocation(amountPaise = 6999L)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.insert(invalidAlloc) }
        }
        assertThat(ex.message).contains("cannot be reduced below committed net negative adjustments (7000 paise)")
    }
}
