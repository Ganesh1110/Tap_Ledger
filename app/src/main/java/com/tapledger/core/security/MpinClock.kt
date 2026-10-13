package com.tapledger.core.security

/**
 * Clock abstraction allowing deterministic testing of monotonic elapsed time
 * (SystemClock.elapsedRealtime) and wall-clock timestamps.
 */
interface MpinClock {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

class SystemMpinClock : MpinClock {
    override fun elapsedRealtime(): Long = android.os.SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
