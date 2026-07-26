package com.clipcascade.acquisition

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdinaryClipboardBackendTest {
    private class FakeClock(var value: Long = 100) : MonotonicClock {
        override fun nowMs(): Long = value
    }

    private class FakeRegistrar : ClipboardChangeRegistrar {
        var registerCount = 0
        var unregisterCount = 0
        var failRegister = false
        var failUnregister = false
        var listener: (() -> Unit)? = null

        override fun register(listener: () -> Unit) {
            if (failRegister) {
                throw IllegalStateException("register failed")
            }
            registerCount += 1
            this.listener = listener
        }

        override fun unregister(listener: () -> Unit) {
            if (failUnregister) {
                throw IllegalStateException("unregister failed")
            }
            unregisterCount += 1
            if (this.listener === listener) {
                this.listener = null
            }
        }

        fun fire() {
            listener?.invoke()
        }
    }

    @Test
    fun `capability is available but foreground only`() {
        val backend = OrdinaryClipboardBackend(FakeRegistrar(), FakeClock())

        val capability = backend.inspectCapability()

        assertEquals(AcquisitionBackendId.ORDINARY_LISTENER, capability.backendId)
        assertEquals(BackendCapabilityState.AVAILABLE, capability.state)
        assertEquals(
            AcquisitionCoverageClass.FOREGROUND_ONLY,
            capability.coverageClass,
        )
    }

    @Test
    fun `start is idempotent and registers exactly once`() {
        val registrar = FakeRegistrar()
        val backend = OrdinaryClipboardBackend(registrar, FakeClock())

        assertEquals(
            BackendStartCode.STARTED,
            backend.start {}.code,
        )
        assertEquals(
            BackendStartCode.ALREADY_RUNNING,
            backend.start {}.code,
        )

        assertEquals(1, registrar.registerCount)
        assertTrue(backend.snapshot().running)
        assertEquals(1L, backend.snapshot().startCount)
    }

    @Test
    fun `trigger emits metadata and updates snapshot`() {
        val registrar = FakeRegistrar()
        val clock = FakeClock(250)
        val backend = OrdinaryClipboardBackend(registrar, clock)
        val triggers = mutableListOf<AcquisitionTrigger>()
        backend.start(triggers::add)

        registrar.fire()

        assertEquals(1, triggers.size)
        assertEquals(
            AcquisitionBackendId.ORDINARY_LISTENER,
            triggers.single().backendId,
        )
        assertEquals(TriggerType.PRIMARY_CLIP_CHANGED, triggers.single().triggerType)
        assertEquals(250L, triggers.single().monotonicTimestampMs)
        assertNull(triggers.single().sourcePackage)
        assertEquals(1L, backend.snapshot().triggerCount)
        assertEquals(250L, backend.snapshot().lastTriggerAtMonotonicMs)
    }

    @Test
    fun `stop is idempotent and blocks later callback`() {
        val registrar = FakeRegistrar()
        val clock = FakeClock(100)
        val backend = OrdinaryClipboardBackend(registrar, clock)
        val triggers = mutableListOf<AcquisitionTrigger>()
        backend.start(triggers::add)
        val staleListener = registrar.listener
        clock.value = 200

        assertEquals(BackendStopCode.STOPPED, backend.stop().code)
        assertEquals(BackendStopCode.ALREADY_STOPPED, backend.stop().code)
        staleListener?.invoke()

        assertEquals(1, registrar.unregisterCount)
        assertFalse(backend.snapshot().running)
        assertEquals(200L, backend.snapshot().lastStoppedAtMonotonicMs)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `registration failure is observable and retryable`() {
        val registrar = FakeRegistrar().apply { failRegister = true }
        val backend = OrdinaryClipboardBackend(registrar, FakeClock())

        val failed = backend.start {}

        assertEquals(BackendStartCode.FAILED, failed.code)
        assertEquals(BackendReasonCode.BACKEND_START_FAILED, failed.reasonCode)
        assertEquals(
            BackendReasonCode.BACKEND_START_FAILED,
            backend.snapshot().lastErrorCode,
        )
        assertFalse(backend.snapshot().running)

        registrar.failRegister = false
        assertEquals(BackendStartCode.STARTED, backend.start {}.code)
        assertTrue(backend.snapshot().running)
    }

    @Test
    fun `unregistration failure still prevents future emission`() {
        val registrar = FakeRegistrar()
        val backend = OrdinaryClipboardBackend(registrar, FakeClock())
        val triggers = mutableListOf<AcquisitionTrigger>()
        backend.start(triggers::add)
        val staleListener = registrar.listener
        registrar.failUnregister = true

        val result = backend.stop()
        staleListener?.invoke()

        assertEquals(BackendStopCode.FAILED, result.code)
        assertEquals(BackendReasonCode.BACKEND_STOP_FAILED, result.reasonCode)
        assertFalse(backend.snapshot().running)
        assertTrue(triggers.isEmpty())
    }

    @Test
    fun `trigger sink failure is contained and reported`() {
        val registrar = FakeRegistrar()
        val backend = OrdinaryClipboardBackend(registrar, FakeClock())
        backend.start { throw IllegalStateException("sink failed") }

        registrar.fire()

        assertEquals(
            BackendReasonCode.TRIGGER_DELIVERY_FAILED,
            backend.snapshot().lastErrorCode,
        )
        assertTrue(backend.snapshot().running)
    }

    @Test
    fun `concurrent starts still register once`() {
        val registrar = FakeRegistrar()
        val backend = OrdinaryClipboardBackend(registrar, FakeClock())
        val resultCodes = Collections.synchronizedList(
            mutableListOf<BackendStartCode>(),
        )
        val startGate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) {
            executor.submit {
                startGate.await()
                resultCodes += backend.start {}.code
            }
        }
        startGate.countDown()
        executor.shutdown()
        while (!executor.isTerminated) {
            Thread.yield()
        }

        assertEquals(1, registrar.registerCount)
        assertEquals(1, resultCodes.count { it == BackendStartCode.STARTED })
        assertEquals(
            7,
            resultCodes.count { it == BackendStartCode.ALREADY_RUNNING },
        )
    }
}
