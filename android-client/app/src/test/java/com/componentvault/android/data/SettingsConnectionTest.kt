package com.componentvault.android.data

import android.app.Application
import android.content.Context
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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
    fun successfulAndFailedDraftTestsNeverReplaceSavedConnectionOrCursor() = runBlocking {
        val context: Application = RuntimeEnvironment.getApplication()
        val repository = InventoryRepository(context)
        repository.saveSyncConfiguration("https://saved.example", "saved-token", true)
        val preferences = context.getSharedPreferences("component_vault_sync", Context.MODE_PRIVATE)
        preferences.edit().putLong("sync_cursor", 42L).commit()
        val saved = repository.loadSyncConfiguration()
        val authorization = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/auth/ping") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            val success = authorization.get() == "Bearer draft-token"
            val bytes = (if (success) "{\"server_time\":\"test\"}" else "{\"detail\":\"Invalid token\"}").toByteArray()
            exchange.sendResponseHeaders(if (success) 200 else 401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val draftUrl = "http://127.0.0.1:${server.address.port}"
            assertEquals(true, repository.testConnection(" $draftUrl/ ", " draft-token ").isSuccess)
            assertEquals("Bearer draft-token", authorization.get())
            assertEquals(saved, repository.loadSyncConfiguration())
            assertEquals(42L, preferences.getLong("sync_cursor", -1L))
            assertEquals(false, repository.testConnection(draftUrl, "wrong-token").isSuccess)
            assertEquals(saved, repository.loadSyncConfiguration())
            assertEquals(42L, preferences.getLong("sync_cursor", -1L))
        } finally {
            server.stop(0)
        }
    }
}
