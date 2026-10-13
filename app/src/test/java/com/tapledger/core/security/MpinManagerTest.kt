package com.tapledger.core.security

import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class MpinManagerTest {

    private lateinit var clock: FakeMpinClock
    private lateinit var keystore: FakeMpinKeystore
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var context: Context
    private lateinit var mpinManager: MpinManagerImpl

    @Before
    fun setUp() {
        clock = FakeMpinClock()
        keystore = FakeMpinKeystore()
        prefs = FakeSharedPreferences()

        mpinManager = MpinManagerImpl(
            keystore = keystore,
            clock = clock,
            prefs = prefs
        )
    }

    @Test
    fun setAndVerifySuccess() = runTest {
        assertThat(mpinManager.isMpinSet.value).isFalse()

        val setResult = mpinManager.setMpin("4829", "4829")
        assertThat(setResult.isSuccess).isTrue()
        assertThat(mpinManager.isMpinSet.value).isTrue()
        assertThat(mpinManager.mpinLength.value).isEqualTo(4)

        val verifyResult = mpinManager.verify("4829")
        assertThat(verifyResult).isEqualTo(MpinVerifyResult.Success)
    }

    @Test
    fun wrongMpinFails() = runTest {
        mpinManager.setMpin("4829", "4829")

        val wrongResult = mpinManager.verify("9999")
        assertThat(wrongResult).isInstanceOf(MpinVerifyResult.WrongMpin::class.java)
    }

    @Test
    fun changeMpinRequiresCurrentMpin() = runTest {
        mpinManager.setMpin("4829", "4829")

        // Wrong current MPIN
        val badChange = mpinManager.changeMpin("0000", "5913", "5913")
        assertThat(badChange.isFailure).isTrue()

        // Correct current MPIN
        val goodChange = mpinManager.changeMpin("4829", "5913", "5913")
        assertThat(goodChange.isSuccess).isTrue()

        // Old MPIN no longer works
        val oldVerify = mpinManager.verify("4829")
        assertThat(oldVerify).isNotEqualTo(MpinVerifyResult.Success)

        // New MPIN works
        val newVerify = mpinManager.verify("5913")
        assertThat(newVerify).isEqualTo(MpinVerifyResult.Success)
    }

    @Test
    fun disableRequiresCurrentMpin() = runTest {
        mpinManager.setMpin("4829", "4829")

        // Bad current pin
        val badDisable = mpinManager.disable("1111")
        assertThat(badDisable.isFailure).isTrue()
        assertThat(mpinManager.isMpinSet.value).isTrue()

        // Good current pin
        val goodDisable = mpinManager.disable("4829")
        assertThat(goodDisable.isSuccess).isTrue()
        assertThat(mpinManager.isMpinSet.value).isFalse()
        assertThat(keystore.isKeyCleared).isTrue()
    }

    @Test
    fun hashNeverEqualsPlaintextAndSaltChangesOutput() {
        val pin = "8391"
        val salt1 = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        val salt2 = byteArrayOf(16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1)

        val hash1 = MpinHasher.hash(pin, salt1)
        val hash2 = MpinHasher.hash(pin, salt2)

        assertThat(hash1).isNotEqualTo(pin.toByteArray())
        assertThat(hash1).isNotEqualTo(hash2)
        assertThat(hash1.size).isEqualTo(32)
        assertThat(hash2.size).isEqualTo(32)
    }

    @Test
    fun isMpinSetFlowEmitsStateTransitions() = runTest {
        mpinManager.isMpinSet.test {
            assertThat(awaitItem()).isFalse()

            mpinManager.setMpin("3819", "3819")
            assertThat(awaitItem()).isTrue()

            mpinManager.disable("3819")
            assertThat(awaitItem()).isFalse()
        }
    }
}
