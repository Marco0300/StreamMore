package com.marco.streammore.tv

import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import org.json.JSONObject

// Deliberately not a data class: toString must never expose the private device code.
class DeviceLoginCode(val userCode: String, val deviceCode: String, val expiresIn: Int, val interval: Int)

data class DeviceLoginState(val userCode: String? = null, val message: String = "Creating a sign-in code…", val retry: Boolean = false)

internal suspend fun runDeviceLogin(
    start: suspend () -> DeviceLoginCode,
    poll: suspend (String) -> JSONObject?,
    cancel: suspend (String) -> Unit,
    onState: (DeviceLoginState) -> Unit,
    now: () -> Long = { System.nanoTime() / 1_000_000 },
    sleep: suspend (Long) -> Unit = { delay(it) },
): JSONObject? {
    var ownedCode: DeviceLoginCode? = null
    var approved = false
    try {
        // Retain ownership even when Back races the start response, so it can be revoked.
        withContext(NonCancellable) { ownedCode = start() }
        currentCoroutineContext().ensureActive()
        val code = checkNotNull(ownedCode)
        onState(DeviceLoginState(code.userCode, "Waiting for approval…"))
        var interval = code.interval.coerceAtLeast(3) * 1000L
        val deadline = now() + code.expiresIn.coerceAtLeast(0) * 1000L
        while (now() < deadline) {
            sleep(minOf(interval, deadline - now()))
            currentCoroutineContext().ensureActive()
            if (now() >= deadline) break
            val response = try {
                // Bound a suspended request to the code's remaining lifetime.
                withTimeoutOrNull(deadline - now()) { poll(code.deviceCode) }
            } catch (failure: ApiException) {
                if (failure.status == 404) break
                if (failure.status == 429) interval = (interval * 2).coerceAtMost(60_000L).coerceAtLeast(interval)
                onState(DeviceLoginState(code.userCode, "Connection interrupted. Retrying…"))
                continue
            }
            currentCoroutineContext().ensureActive()
            if (now() >= deadline) break
            if (response != null) {
                approved = true
                return response
            }
        }
        onState(DeviceLoginState(message = "Code expired. Get a new code.", retry = true))
        return null
    } finally {
        if (!approved) ownedCode?.let { code ->
            withContext(NonCancellable) {
                withTimeoutOrNull(5_000) { runCatching { cancel(code.deviceCode) } }
            }
        }
    }
}
