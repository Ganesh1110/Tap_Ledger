package com.tapledger.ui.charts.util

import java.util.Locale

data class SliceRemainderItem(
    val id: Long,
    val valuePaise: Long,
    val initialBp: Int,
    val remainder: Long
)

data class AxisTicks(
    val stepPaise: Long,
    val minPaise: Long,
    val maxPaise: Long,
    val ticks: List<Long>
)

/**
 * Shared integer-only presentation arithmetic utilities strictly conforming to Flow.md §17.1.
 * Zero floating-point arithmetic (no Float, Double, BigDecimal).
 * All money scaling uses Math.multiplyExact and Math.addExact to protect against silent overflow.
 */
object ChartMath {

    const val MILLIS_PER_IST_DAY = 86_400_000L
    const val BASIS_POINTS_TOTAL = 10_000 // 100.00%

    /**
     * D1 Basis points: bp = floor(part * 10_000 / whole); whole <= 0 gives 0.
     */
    fun computeBasisPoints(part: Long, whole: Long): Int {
        if (whole <= 0L || part <= 0L) return 0
        val scaled = Math.multiplyExact(part, BASIS_POINTS_TOTAL.toLong())
        return (scaled / whole).toInt()
    }

    /**
     * D2 Percent labels: Largest remainder apportionment in bp so slices sum to exactly 10,000.
     * Tie-break order: larger remainder, then larger value, then ascending id.
     * Returns a Map of id to apportioned basis points.
     */
    fun apportionBasisPoints(slices: List<Pair<Long, Long>>): Map<Long, Int> {
        if (slices.isEmpty()) return emptyMap()
        val total = slices.fold(0L) { acc, (_, v) -> Math.addExact(acc, v.coerceAtLeast(0L)) }
        if (total <= 0L) {
            return slices.associate { it.first to 0 }
        }

        val items = slices.map { (id, value) ->
            val v = value.coerceAtLeast(0L)
            val scaled = Math.multiplyExact(v, BASIS_POINTS_TOTAL.toLong())
            val initialBp = (scaled / total).toInt()
            val remainder = scaled % total
            SliceRemainderItem(id = id, valuePaise = v, initialBp = initialBp, remainder = remainder)
        }

        val sumInitial = items.sumOf { it.initialBp }
        val deficit = (BASIS_POINTS_TOTAL - sumInitial).coerceAtLeast(0)

        // Sort: larger remainder, then larger value, then ascending id
        val sortedIndices = items.indices.sortedWith { i1, i2 ->
            val a = items[i1]
            val b = items[i2]
            when {
                a.remainder != b.remainder -> b.remainder.compareTo(a.remainder)
                a.valuePaise != b.valuePaise -> b.valuePaise.compareTo(a.valuePaise)
                else -> a.id.compareTo(b.id)
            }
        }

        val finalBp = items.map { it.initialBp }.toMutableList()
        for (k in 0 until deficit.coerceAtMost(items.size)) {
            val idx = sortedIndices[k]
            finalBp[idx] += 1
        }

        return items.indices.associate { items[it].id to finalBp[it] }
    }

    /**
     * D2 Formatted percentage string via pure integer math.
     * e.g., 5025 bp -> "50.25%"
     */
    fun formatBasisPoints(bp: Int): String {
        val whole = bp / 100
        val frac = bp % 100
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        return "$whole.$fracStr%"
    }

    /**
     * D3 Pace (OPEN period containing now only):
     * totalDays = (end_at - start_at)/86_400_000
     * elapsedDays = clamp(floorDiv(nowIST - start_at, 86_400_000) + 1, 1, totalDays)
     * expectedSpend = floor(budget * elapsedDays / totalDays)
     */
    fun computePace(
        startAt: Long,
        endAt: Long,
        nowIST: Long,
        budgetPaise: Long
    ): Pair<Long, Long> {
        val totalDays = ((endAt - startAt) / MILLIS_PER_IST_DAY).coerceAtLeast(1L)
        val rawElapsed = Math.floorDiv(nowIST - startAt, MILLIS_PER_IST_DAY) + 1L
        val elapsedDays = rawElapsed.coerceIn(1L, totalDays)
        val expectedSpend = if (budgetPaise <= 0L) {
            0L
        } else {
            Math.multiplyExact(budgetPaise, elapsedDays) / totalDays
        }
        return Pair(elapsedDays, expectedSpend)
    }

    /**
     * D4 Projection (display-only estimate):
     * projected = floor(spent * totalDays / elapsedDays), shown only when elapsedDays >= 5.
     */
    fun computeProjection(
        spentPaise: Long,
        elapsedDays: Long,
        totalDays: Long
    ): Long? {
        if (elapsedDays < 5L || elapsedDays <= 0L || totalDays <= 0L) return null
        return Math.multiplyExact(spentPaise.coerceAtLeast(0L), totalDays) / elapsedDays
    }

    /**
     * D5 Nice axis ticks: step is the smallest value of the form {1,2,5} x 10^k paise
     * that yields <= 5 intervals. Zero baseline is always present.
     */
    fun computeNiceTicks(minPaise: Long, maxPaise: Long): AxisTicks {
        val effectiveMin = minOf(minPaise, 0L)
        val effectiveMax = maxOf(maxPaise, 0L)
        val span = (effectiveMax - effectiveMin).coerceAtLeast(100L) // at least ₹1 span

        // Generate candidate steps of {1, 2, 5} * 10^k paise
        val candidateMultipliers = longArrayOf(1L, 2L, 5L)
        var step = 100L // ₹1 minimum step
        var powerOfTen = 100L

        findStep@ while (powerOfTen <= 100_00_00_000_00L) {
            for (mult in candidateMultipliers) {
                val candidate = Math.multiplyExact(mult, powerOfTen / 1L)
                val intervals = calculateIntervals(effectiveMin, effectiveMax, candidate)
                if (intervals in 1..5) {
                    step = candidate
                    break@findStep
                }
            }
            powerOfTen = Math.multiplyExact(powerOfTen, 10L)
        }

        val startTick = floorStep(effectiveMin, step)
        val endTick = ceilStep(effectiveMax, step)

        val ticks = mutableListOf<Long>()
        var curr = startTick
        while (curr <= endTick) {
            ticks.add(curr)
            curr = Math.addExact(curr, step)
        }

        return AxisTicks(
            stepPaise = step,
            minPaise = startTick,
            maxPaise = endTick,
            ticks = ticks
        )
    }

    private fun calculateIntervals(min: Long, max: Long, step: Long): Long {
        val start = floorStep(min, step)
        val end = ceilStep(max, step)
        return (end - start) / step
    }

    private fun floorStep(value: Long, step: Long): Long {
        return Math.floorDiv(value, step) * step
    }

    private fun ceilStep(value: Long, step: Long): Long {
        val div = Math.floorDiv(value, step)
        val rem = value % step
        return if (rem == 0L) div * step else (div + 1L) * step
    }

    /**
     * D7 Money animation: interpolate Long as start + (end - start) * permille / 1000
     * permille is an Int 0..1000. Never Float*Long.
     */
    fun interpolateMoney(start: Long, end: Long, permille: Int): Long {
        val clampedPermille = permille.coerceIn(0, 1000).toLong()
        if (clampedPermille == 0L) return start
        if (clampedPermille == 1000L) return end
        val diff = Math.subtractExact(end, start)
        val delta = Math.multiplyExact(diff, clampedPermille) / 1000L
        return Math.addExact(start, delta)
    }
}
