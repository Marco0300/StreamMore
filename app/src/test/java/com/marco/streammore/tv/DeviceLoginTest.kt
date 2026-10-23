package com.marco.streammore.tv

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceLoginTest {
    @Test fun approvalArrivingAfterExpiryCannotNavigate() = runBlocking {
        var time = 0L
        var cancelled = 0
        val result = runDeviceLogin(
            start = { DeviceLoginCode("ABCD-EFGH", "private", 7, 5) },
            poll = { time = 8000; JSONObject() }, cancel = { cancelled++ }, onState = {},
            now = { time }, sleep = { time += it },
        )
        assertNull(result)
        assertEquals(1, cancelled)
    }

    @Test fun leavingDuringStartOrPollCancelsPrivatelyWithoutStaleState() = runBlocking {
        for (duringStart in listOf(true, false)) {
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            var cancelled = 0
            var staleStates = 0
            var leaving = false
            val job = launch {
                runDeviceLogin(
                    start = {
                        if (duringStart) { entered.complete(Unit); release.await() }
                        DeviceLoginCode("ABCD-EFGH", "private", 600, 5)
                    },
                    poll = { entered.complete(Unit); release.await(); JSONObject() },
                    cancel = { kotlinx.coroutines.delay(1); assertEquals("private", it); cancelled++ },
                    onState = { if (leaving) staleStates++ }, sleep = {},
                )
            }
            entered.await()
            leaving = true
            job.cancel()
            release.complete(Unit)
            job.join()
            assertEquals(1, cancelled)
            assertEquals(0, staleStates)
            assertTrue(job.isCancelled)
        }
    }

    @Test fun unknownOrExpiredCodeStopsImmediately() = runBlocking {
        var time = 0L
        var polls = 0
        var cancelled = 0
        var state = DeviceLoginState()
        runDeviceLogin(
            start = { DeviceLoginCode("ABCD-EFGH", "private", 15, 5) },
            poll = { polls++; throw ApiException(404, "private") }, cancel = { cancelled++ }, onState = { state = it },
            now = { time }, sleep = { time += it },
        )
        assertEquals(1, polls)
        assertEquals(1, cancelled)
        assertTrue(state.retry)
        assertNull(state.userCode)
        assertEquals("Code expired. Get a new code.", state.message)
    }

    @Test fun transientFailuresRetryWithRateLimitBackoffUntilApproval() = runBlocking {
        var time = 0L
        var polls = 0
        val waits = mutableListOf<Long>()
        val states = mutableListOf<DeviceLoginState>()
        val result = runDeviceLogin(
            start = { DeviceLoginCode("ABCD-EFGH", "private", 60, 5) },
            poll = { when (++polls) { 1 -> throw ApiException(429, "private"); 2 -> throw ApiException(503, "private"); 3 -> throw ApiException(-1, "private"); else -> JSONObject() } },
            cancel = { fail("Approved code must not be cancelled") }, onState = { states.add(it) },
            now = { time }, sleep = { waits.add(it); time += it },
        )
        assertNotNull(result)
        assertEquals(listOf(5000L, 10000L, 10000L, 10000L), waits)
        assertTrue(states.any { it.message.contains("Retrying") })
        assertFalse(states.toString().contains("private"))
    }

    @Test fun expiresWithoutPollingPastDeadlineAndCancelsCode() = runBlocking {
        var time = 0L
        var polls = 0
        var cancelled = 0
        var state = DeviceLoginState()
        val result = runDeviceLogin(
            start = { DeviceLoginCode("ABCD-EFGH", "private", 7, 5) },
            poll = { polls++; null }, cancel = { cancelled++ }, onState = { state = it },
            now = { time }, sleep = { time += it; check(time <= 10000) },
        )
        assertNull(result)
        assertEquals(7000L, time)
        assertEquals(1, polls)
        assertEquals(1, cancelled)
        assertTrue(state.retry)
        assertNull(state.userCode)
        assertEquals("Code expired. Get a new code.", state.message)
    }

    @Test fun pendingWaitsForIntervalThenApprovesExactlyOnce() = runBlocking {
        var time = 0L
        val waits = mutableListOf<Long>()
        val states = mutableListOf<DeviceLoginState>()
        var polls = 0
        var cancelled = 0
        val approved = JSONObject("""{"status":"approved","user":{}}""")
        val result = runDeviceLogin(
            start = { DeviceLoginCode("ABCD-EFGH", "private", 600, 1) },
            poll = { assertEquals("private", it); if (++polls == 2) approved else null },
            cancel = { cancelled++ }, onState = { states.add(it) },
            now = { time }, sleep = { waits.add(it); time += it },
        )
        assertSame(approved, result)
        assertEquals(listOf(3000L, 3000L), waits)
        assertEquals(0, cancelled)
        assertEquals("ABCD-EFGH", states.first().userCode)
        assertFalse(states.toString().contains("private"))
    }
}
