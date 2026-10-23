package com.marco.streammore.tv

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeviceAuthApiTest {

    @Test fun cancelledPollCannotPersistLateSessionCookie() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("streammore_session", 0).edit().clear().commit()
        val server = ServerSocket(0)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                reader.readLine()
                while (!reader.readLine().isNullOrEmpty()) { }
                entered.countDown()
                release.await(5, java.util.concurrent.TimeUnit.SECONDS)
                val body = """{"status":"approved","user":{}}"""
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nSet-Cookie: streammore_session=late; HttpOnly\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray())
                    flush()
                }
            }
        }
        try {
            val api = StreammoreApi(context, "http://127.0.0.1:${server.localPort}")
            val job = launch(kotlinx.coroutines.Dispatchers.Default) { api.pollDeviceLogin("fixture-private") }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            job.cancel()
            release.countDown()
            job.join()
            assertNull(api.sessionCookie())
            assertTrue(context.getSharedPreferences("streammore_session", 0).all.isEmpty())
        } finally { release.countDown(); server.close(); worker.join(1000) }
    }

    @Test fun pairingUsesExistingSessionForProtectedProfiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("streammore_session", 0).edit().clear().commit()
        val requests = mutableListOf<Triple<String, String, String?>>()
        val server = ServerSocket(0)
        var polls = 0
        val worker = thread(isDaemon = true) {
            repeat(5) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val path = reader.readLine().split(" ")[1]
                    val headers = generateSequence { reader.readLine().takeIf { it.isNotEmpty() } }
                        .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                    val chars = CharArray(headers["content-length"]?.toInt() ?: 0)
                    var offset = 0
                    while (offset < chars.size) offset += reader.read(chars, offset, chars.size - offset)
                    requests.add(Triple(path, String(chars), headers["cookie"]))
                    var cookieHeader = ""
                    val (status, body) = when (path) {
                        "/api/auth/device/start" -> 201 to """{"userCode":"ABCD-EFGH","deviceCode":"fixture-private","expiresIn":600,"interval":5}"""
                        "/api/auth/device/token" -> if (polls++ == 0) 202 to """{"status":"pending"}""" else {
                            cookieHeader = "set-cookie: streammore_session=fixture-session; Path=/; HttpOnly\r\n"
                            200 to """{"status":"approved","user":{"liveTvEnabled":false}}"""
                        }
                        "/api/profiles" -> 200 to """{"profiles":[]}"""
                        else -> 200 to """{"ok":true}"""
                    }
                    val bytes = body.toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status OK\r\n${cookieHeader}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }
        try {
            val base = "http://127.0.0.1:${server.localPort}"
            val api = StreammoreApi(context, base)
            val code = api.startDeviceLogin()
            assertEquals("ABCD-EFGH", code.userCode)
            assertEquals(600, code.expiresIn)
            assertEquals(5, code.interval)
            assertNull(api.pollDeviceLogin(code.deviceCode))
            val approval = api.pollDeviceLogin(code.deviceCode)!!
            assertFalse(accountHasLiveTvAccess(approval))
            val restored = StreammoreApi(context, base)
            restored.profiles()
            api.cancelDeviceLogin(code.deviceCode)
            assertEquals(listOf("/api/auth/device/start", "/api/auth/device/token", "/api/auth/device/token", "/api/profiles", "/api/auth/device/cancel"), requests.map { it.first })
            assertEquals(0, JSONObject(requests.first().second).length())
            for (request in requests.filter { it.first.endsWith("token") || it.first.endsWith("cancel") }) {
                assertEquals("fixture-private", JSONObject(request.second).getString("deviceCode"))
            }
            assertEquals("streammore_session=fixture-session", requests[3].third)
            assertFalse(context.getSharedPreferences("streammore_session", 0).all.values.any { it.toString().contains("fixture-private") })
        } finally { server.close(); worker.join(1000) }
    }
}
