package com.componentvault.android.data

import com.componentvault.android.model.SyncConfiguration
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncEndpointResolverTest {
    private val settings = SyncConfiguration(
        "device", "http://lan", "token", true, "", "", "https://external",
    )

    @Test
    fun reachablePrimaryWinsAndEachRunTriesPrimaryAgain() {
        val calls = mutableListOf<String>()
        repeat(2) {
            val endpoint = SyncEndpointResolver.resolve(settings) { configuration, timeout ->
                calls += configuration.serverBaseUrl
                assertEquals(3_000, timeout)
                "ok"
            }
            assertFalse(endpoint.usesExternalAddress)
            assertEquals(settings, endpoint.configuration)
        }
        assertEquals(listOf("http://lan", "http://lan"), calls)
    }

    @Test
    fun ConnectionFailureAndTimeoutUseConfiguredExternalAddress() {
        for (failure in listOf(ConnectException("offline"), SocketTimeoutException("timeout"))) {
            val calls = mutableListOf<String>()
            val endpoint = SyncEndpointResolver.resolve(settings) { configuration, timeout ->
                calls += configuration.serverBaseUrl
                if (configuration.serverBaseUrl == settings.serverBaseUrl) throw failure
                assertEquals(15_000, timeout)
                assertEquals(settings.apiToken, configuration.apiToken)
                "external-response"
            }
            assertEquals(listOf("http://lan", "https://external"), calls)
            assertTrue(endpoint.usesExternalAddress)
            assertEquals("external-response", endpoint.probe)
            assertEquals("http://lan", settings.serverBaseUrl)
        }
    }

    @Test
    fun ServerRejectionAndInvalidResponseDoNotFallBack() {
        for (failure in listOf(IllegalStateException("HTTP 401"), IllegalArgumentException("invalid JSON"))) {
            var calls = 0
            val thrown = assertFailsWith<RuntimeException> {
                SyncEndpointResolver.resolve(settings) { _, _ -> calls++; throw failure }
            }
            assertEquals(failure, thrown)
            assertEquals(1, calls)
        }
    }

    @Test
    fun MissingOrDuplicateExternalAddressDoesNotRetry() {
        for (fallback in listOf("", "http://lan/")) {
            var calls = 0
            assertFailsWith<ConnectException> {
                SyncEndpointResolver.resolve(settings.copy(externalServerBaseUrl = fallback)) { _, timeout ->
                    calls++
                    assertEquals(15_000, timeout)
                    throw ConnectException("offline")
                }
            }
            assertEquals(1, calls)
        }
    }
}
