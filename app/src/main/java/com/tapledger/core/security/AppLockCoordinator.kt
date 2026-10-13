package com.tapledger.core.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockCoordinator(
    private val mpinManager: MpinManager,
    private val clock: MpinClock,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    @Inject
    constructor(
        mpinManager: MpinManager,
        clock: MpinClock
    ) : this(mpinManager, clock, Dispatchers.Main.immediate)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var backgroundedAtElapsed: Long = 0L

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _lockMessage = MutableStateFlow<String?>(null)
    val lockMessage: StateFlow<String?> = _lockMessage.asStateFlow()

    init {
        // Cold start lock evaluation
        scope.launch {
            mpinManager.isMpinSet.collect { isSet ->
                if (isSet && backgroundedAtElapsed == 0L) {
                    // Cold start with MPIN set -> lock app
                    _isAppLocked.value = true
                } else if (!isSet) {
                    _isAppLocked.value = false
                    _lockMessage.value = null
                }
            }
        }
    }

    fun onAppBackgrounded() {
        if (!mpinManager.isMpinSet.value) return
        backgroundedAtElapsed = clock.elapsedRealtime()
        if (mpinManager.gracePeriod.value == GracePeriod.IMMEDIATELY) {
            _isAppLocked.value = true
        }
    }

    fun onAppForegrounded() {
        if (!mpinManager.isMpinSet.value) {
            _isAppLocked.value = false
            return
        }

        val lastVerification = mpinManager.getLastFullVerificationAt()
        val interval = mpinManager.periodicReverification.value
        val shouldForce = AppLockPolicy.shouldTriggerPeriodicReverification(
            lastFullVerificationAt = lastVerification,
            currentTimeMillis = clock.currentTimeMillis(),
            interval = interval
        )

        if (shouldForce) {
            _lockMessage.value = "For your security, please verify your MPIN."
            _isAppLocked.value = true
            return
        }

        val gracePeriod = mpinManager.gracePeriod.value
        val expired = AppLockPolicy.isGracePeriodExpired(
            backgroundedAtElapsed = backgroundedAtElapsed,
            currentElapsed = clock.elapsedRealtime(),
            gracePeriod = gracePeriod
        )

        if (expired) {
            _isAppLocked.value = true
        }
    }

    fun unlock() {
        _isAppLocked.value = false
        _lockMessage.value = null
        backgroundedAtElapsed = 0L
        mpinManager.recordSuccessfulVerification()
    }

    fun lock(reason: String? = null) {
        _lockMessage.value = reason
        _isAppLocked.value = true
    }
}
