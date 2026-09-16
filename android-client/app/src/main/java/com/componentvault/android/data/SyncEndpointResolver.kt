package com.componentvault.android.data

import com.componentvault.android.model.SyncConfiguration
import java.io.IOException

internal data class ResolvedSyncEndpoint<T>(
    val configuration: SyncConfiguration,
    val probe: T,
    val usesExternalAddress: Boolean,
)

/** Resolve once, before sending inventory writes; never switch endpoints mid-sync. */
internal object SyncEndpointResolver {
    fun <T> resolve(
        configuration: SyncConfiguration,
        probe: (SyncConfiguration, Int) -> T,
    ): ResolvedSyncEndpoint<T> {
        val fallback = configuration.externalServerBaseUrl.trim().trimEnd('/')
        val canFallback = fallback.isNotBlank() && fallback != configuration.serverBaseUrl
        return try {
            ResolvedSyncEndpoint(
                configuration,
                probe(configuration, if (canFallback) 3_000 else 15_000),
                false,
            )
        } catch (primaryFailure: IOException) {
            if (!canFallback) throw primaryFailure
            val externalConfiguration = configuration.copy(serverBaseUrl = fallback)
            try {
                ResolvedSyncEndpoint(externalConfiguration, probe(externalConfiguration, 15_000), true)
            } catch (externalFailure: Exception) {
                externalFailure.addSuppressed(primaryFailure)
                throw externalFailure
            }
        }
    }
}
