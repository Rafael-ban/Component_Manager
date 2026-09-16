package com.componentvault.android.data

import android.app.Application
import android.content.Context
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SettingsConnectionTest {
    @Test
    fun externalDraftIsUsedWhenPrimaryIsOfflineWithoutChangingSavedSettings(): Unit = runBlocking {
        val context: Application = RuntimeEnvironment.getApplication()
        val repository = InventoryRepository(context)
        repository.saveSyncConfiguration("https://saved.example", "saved-token", true, "https://saved-external.example")
        val saved = repository.loadSyncConfiguration()
        val preferences = context.getSharedPreferences("component_vault_sync", Context.MODE_PRIVATE)
        preferences.edit().putLong("sync_cursor", 42L).commit()
        val offlinePort = ServerSocket(0).use { it.localPort }
        ServerSocket(0).use { server ->
            server.soTimeout = 10_000
            val responder = CompletableFuture.runAsync {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    assertEquals("POST /auth/ping HTTP/1.1", headers.first())
                    val bytes = "{\"server_time\":\"external\"}".toByteArray()
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
            val result = repository.testConnection("http://127.0.0.1:$offlinePort", "draft-token", "http://127.0.0.1:${server.localPort}")
            assertEquals(true, result.isSuccess)
            assertEquals(saved, repository.loadSyncConfiguration())
            assertEquals(42L, preferences.getLong("sync_cursor", -1L))
            responder.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun successfulAndFailedDraftTestsNeverReplaceSavedConnectionOrCursor(): Unit = runBlocking {
        val context: Application = RuntimeEnvironment.getApplication()
        val repository = InventoryRepository(context)
        repository.saveSyncConfiguration("https://saved.example", "saved-token", true)
        val preferences = context.getSharedPreferences("component_vault_sync", Context.MODE_PRIVATE)
        preferences.edit().putLong("sync_cursor", 42L).commit()
        val saved = repository.loadSyncConfiguration()
        val authorization = AtomicReference<String>()
        val server = ServerSocket().apply {
            bind(InetSocketAddress("127.0.0.1", 0))
            soTimeout = 10_000
        }
        val responder = CompletableFuture.runAsync {
            repeat(2) {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    assertEquals("POST /auth/ping HTTP/1.1", headers.first())
                    authorization.set(headers.first { it.startsWith("Authorization:", ignoreCase = true) }.substringAfter(':').trim())
                    val success = authorization.get() == "Bearer draft-token"
                    val bytes = (if (success) "{\"server_time\":\"test\"}" else "{\"detail\":\"Invalid token\"}").toByteArray()
                    val status = if (success) "200 OK" else "401 Unauthorized"
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }
        try {
            val draftUrl = "http://127.0.0.1:${server.localPort}"
            assertEquals(true, repository.testConnection(" $draftUrl/ ", " draft-token ").isSuccess)
            assertEquals("Bearer draft-token", authorization.get())
            assertEquals(saved, repository.loadSyncConfiguration())
            assertEquals(42L, preferences.getLong("sync_cursor", -1L))
            assertEquals(false, repository.testConnection(draftUrl, "wrong-token").isSuccess)
            assertEquals(saved, repository.loadSyncConfiguration())
            assertEquals(42L, preferences.getLong("sync_cursor", -1L))
            responder.get(10, TimeUnit.SECONDS)
        } finally {
            server.close()
        }
    }
}
