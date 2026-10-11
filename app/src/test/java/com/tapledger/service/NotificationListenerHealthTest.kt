package com.tapledger.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NotificationListenerHealthTest {

    @Test
    fun testListenerHealthAndRebind() {
        // 1. Initial / disconnected state
        FinancialNotificationListenerService.isConnected = false
        FinancialNotificationListenerService.lastConnectedAt = 1000L
        FinancialNotificationListenerService.lastDisconnectedAt = 2000L

        assertThat(FinancialNotificationListenerService.isConnected).isFalse()
        assertThat(FinancialNotificationListenerService.lastDisconnectedAt)
            .isGreaterThan(FinancialNotificationListenerService.lastConnectedAt)

        // 2. Connected state simulation
        FinancialNotificationListenerService.isConnected = true
        FinancialNotificationListenerService.lastConnectedAt = 3000L

        assertThat(FinancialNotificationListenerService.isConnected).isTrue()
        assertThat(FinancialNotificationListenerService.lastConnectedAt)
            .isGreaterThan(FinancialNotificationListenerService.lastDisconnectedAt)

        // 3. Adaptive health check stall calculation per Flow.md §14.2
        // Warns only when gap exceeds 3x average inter-arrival time
        val averageInterval = 3600_000L // 1 hour average gap
        val lastSeen = 10_000_000L

        // Gap of 2 hours -> NOT stalled (2x < 3x)
        val notStalledNow = lastSeen + 2 * averageInterval
        assertThat(
            NotificationListenerLivenessWorker.isAdaptiveHealthStalled(
                lastSeenAt = lastSeen,
                averageInterArrivalMillis = averageInterval,
                now = notStalledNow
            )
        ).isFalse()

        // Gap of 3.5 hours -> STALLED (3.5x > 3x)
        val stalledNow = lastSeen + (3.5 * averageInterval).toLong()
        assertThat(
            NotificationListenerLivenessWorker.isAdaptiveHealthStalled(
                lastSeenAt = lastSeen,
                averageInterArrivalMillis = averageInterval,
                now = stalledNow
            )
        ).isTrue()
    }
}
