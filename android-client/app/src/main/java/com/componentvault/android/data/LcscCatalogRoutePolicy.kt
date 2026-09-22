package com.componentvault.android.data

import com.componentvault.android.model.AppLanguage
import java.io.IOException
import java.util.Locale

internal enum class LcscCatalogSource(val wireName: String) {
    Domestic("lcsc_domestic_web"),
    International("lcsc_public_web"),
}

internal enum class LcscCatalogFailureKind {
    Blocked,
    Unreachable,
    NoMatch,
    InvalidResponse,
}

internal data class LcscCatalogAttempt(
    val source: LcscCatalogSource,
    val failure: LcscCatalogFailureKind,
)

internal data class LcscCatalogLookupResult(
    val metadata: com.componentvault.android.model.ComponentOfficialMetadata?,
    val preferredSource: LcscCatalogSource,
    val resolvedSource: LcscCatalogSource?,
    val attempts: List<LcscCatalogAttempt>,
) {
    val usedFallback: Boolean get() = resolvedSource != null && resolvedSource != preferredSource
}

internal object LcscCatalogRoutePolicy {
    fun preferredSource(language: AppLanguage): LcscCatalogSource = when (language) {
        AppLanguage.ZhCn -> LcscCatalogSource.Domestic
        AppLanguage.English -> LcscCatalogSource.International
    }

    fun preferredSource(locale: Locale): LcscCatalogSource =
        if (locale.language.equals("zh", ignoreCase = true)) {
            LcscCatalogSource.Domestic
        } else {
            LcscCatalogSource.International
        }

    fun order(preferred: LcscCatalogSource): List<LcscCatalogSource> = when (preferred) {
        LcscCatalogSource.Domestic -> listOf(LcscCatalogSource.Domestic, LcscCatalogSource.International)
        LcscCatalogSource.International -> listOf(LcscCatalogSource.International, LcscCatalogSource.Domestic)
    }

    fun shouldPersist(result: LcscCatalogLookupResult): Boolean =
        result.metadata != null && result.resolvedSource == result.preferredSource

    fun classify(error: Throwable): LcscCatalogFailureKind = when (error) {
        is LcscDomesticBlockedException -> LcscCatalogFailureKind.Blocked
        is LcscDomesticResponseException -> LcscCatalogFailureKind.InvalidResponse
        is IOException -> LcscCatalogFailureKind.Unreachable
        else -> LcscCatalogFailureKind.InvalidResponse
    }
}
