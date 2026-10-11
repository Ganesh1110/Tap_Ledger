package com.tapledger.domain.calculator

import java.math.BigInteger

/**
 * Implements Flow.md §9.2: Capacity-Capped Largest-Remainder Refund Apportionment
 * and Rules R1 (R1_CUMULATIVE_REFUND_EXCEEDED) and R2 (R2_REFUND_EXCEEDS_CATEGORY_CAP).
 *
 * Strictly zero floating-point arithmetic.
 */
object RefundApportionmentCalculator {

    data class CategoryCapacity(
        val categoryId: Long,
        val originalAllocationPaise: Long,
        val priorRefundsPaise: Long = 0L
    ) {
        val remainingCapacityPaise: Long = originalAllocationPaise - priorRefundsPaise

        init {
            require(originalAllocationPaise > 0L) { "Original allocation must be positive (Rule A3)" }
            require(priorRefundsPaise >= 0L) { "Prior refunds cannot be negative" }
            require(remainingCapacityPaise >= 0L) {
                "Remaining capacity cannot be negative for category #$categoryId (prior: $priorRefundsPaise, orig: $originalAllocationPaise)"
            }
        }
    }

    data class ApportionedRefund(
        val categoryId: Long,
        val amountPaise: Long
    )

    data class ApportionmentResult(
        val totalRefundPaise: Long,
        val totalCapacityPaise: Long,
        val apportionments: List<ApportionedRefund> // only includes entries with amountPaise > 0 (Rule A3)
    )

    /**
     * Apportions refund amount [refundPaise] across [capacities] using the
     * Capacity-Capped Largest-Remainder algorithm (§9.2).
     */
    fun apportionRefund(
        refundPaise: Long,
        capacities: List<CategoryCapacity>
    ): ApportionmentResult {
        require(refundPaise > 0L) { "Refund amount must be positive (Rule T2)" }
        require(capacities.isNotEmpty()) { "Capacities list cannot be empty" }

        // Step 1: Compute remaining capacity for each category
        val totalCap = capacities.sumOf { it.remainingCapacityPaise }
        if (refundPaise > totalCap) {
            throw IllegalArgumentException(
                "Rule R1_CUMULATIVE_REFUND_EXCEEDED: Refund amount ($refundPaise paise) exceeds remaining capacity ($totalCap paise)"
            )
        }

        val rBig = BigInteger.valueOf(refundPaise)
        val totalCapBig = BigInteger.valueOf(totalCap)

        class InterimCandidate(
            val categoryId: Long,
            val capacity: Long,
            var baseRefund: Long,
            val remainder: Long
        )

        // Step 2: Compute base integer refund and remainder per category
        val candidates = capacities.map { cat ->
            val capBig = BigInteger.valueOf(cat.remainingCapacityPaise)
            val product = rBig.multiply(capBig)
            val baseRefund = product.divide(totalCapBig).toLong()
            val remainder = product.remainder(totalCapBig).toLong()
            InterimCandidate(
                categoryId = cat.categoryId,
                capacity = cat.remainingCapacityPaise,
                baseRefund = baseRefund,
                remainder = remainder
            )
        }

        val allocatedSoFar = candidates.sumOf { it.baseRefund }
        val unallocatedPaise = (refundPaise - allocatedSoFar).toInt()
        require(unallocatedPaise >= 0) { "Unallocated paise cannot be negative" }

        // Step 3: Distribute unallocated paise one paisa at a time sorted deterministically:
        // - Descending Remainder_i
        // - Tie-break: Larger cap_i, then lower category_id
        val sortedForRemainder = candidates.sortedWith(
            compareByDescending<InterimCandidate> { it.remainder }
                .thenByDescending { it.capacity }
                .thenBy { it.categoryId }
        )

        for (i in 0 until unallocatedPaise) {
            sortedForRemainder[i].baseRefund += 1L
        }

        // Verify Rule R2 and construct result (strictly amountPaise > 0 per Rule A3)
        val apportionments = mutableListOf<ApportionedRefund>()
        for (candidate in candidates) {
            if (candidate.baseRefund > candidate.capacity) {
                throw IllegalStateException(
                    "Rule R2_REFUND_EXCEEDS_CATEGORY_CAP: Refund for category ${candidate.categoryId} (${candidate.baseRefund}) exceeds capacity (${candidate.capacity})"
                )
            }
            if (candidate.baseRefund > 0L) {
                apportionments.add(
                    ApportionedRefund(
                        categoryId = candidate.categoryId,
                        amountPaise = candidate.baseRefund
                    )
                )
            }
        }

        val totalApportioned = apportionments.sumOf { it.amountPaise }
        require(totalApportioned == refundPaise) {
            "Sum of apportioned refunds ($totalApportioned) must equal total refund ($refundPaise)"
        }

        return ApportionmentResult(
            totalRefundPaise = refundPaise,
            totalCapacityPaise = totalCap,
            apportionments = apportionments
        )
    }
}
