package com.componentvault.android.data

import com.componentvault.android.model.ComponentOfficialMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal data class LcscDomesticProduct(
    val metadata: ComponentOfficialMetadata,
    val parameters: Map<String, String>,
    val datasheetUrl: String?,
)

internal class LcscDomesticBlockedException : IOException("LCSC verification page returned")

/** Public Chinese LCSC search. It deliberately does not emulate or bypass verification cookies. */
internal object LcscDomesticCatalog {
    private val nextDataPattern = Regex(
        """<script\b[^>]*\bid\s*=\s*[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val htmlTagPattern = Regex("<[^>]+>")

    fun searchUrl(keyword: String): String? = keyword.trim().takeIf(String::isNotEmpty)?.let {
        "https://so.szlcsc.com/global.html?k=${URLEncoder.encode(it, Charsets.UTF_8.name())}"
    }

    fun parseSearchPage(html: String): List<LcscDomesticProduct> {
        if (looksLikeVerificationPage(html)) throw LcscDomesticBlockedException()
        val payload = nextDataPattern.find(html)?.groupValues?.get(1)?.trim()
            ?.takeIf(String::isNotEmpty) ?: throw IOException("LCSC response is missing __NEXT_DATA__")
        val records = runCatching {
            JSONObject(payload).optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("soData")
                ?.optJSONObject("searchResult")
                ?.optJSONArray("productRecordList")
        }.getOrElse { throw IOException("Unable to parse LCSC __NEXT_DATA__", it) }
            ?: throw IOException("LCSC search response structure changed")
        return (0 until records.length()).mapNotNull { index ->
            parseRecord(records.optJSONObject(index) ?: return@mapNotNull null)
        }.take(20)
    }

    fun exactMatch(sku: String, results: List<LcscDomesticProduct>): LcscDomesticProduct? {
        val expected = LcscPublicCatalog.normalizeSku(sku) ?: return null
        return results.firstOrNull { it.metadata.sku == expected }
    }

    fun fetchSearchPage(keyword: String): String {
        val connection = URL(requireNotNull(searchUrl(keyword))).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "ComponentVault-Android/0.3")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            val status = connection.responseCode
            AppDiagnostics.record("lookup_domestic", "http" to status)
            if (status !in 200..299) throw IOException("HTTP $status")
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw IOException("Search page too large")
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRecord(record: JSONObject): LcscDomesticProduct? {
        val product = record.optJSONObject("productVO") ?: return null
        val sku = LcscPublicCatalog.normalizeSku(product.text("productCode").orEmpty()) ?: return null
        val productId = product.text("productId")?.takeIf { it.all(Char::isDigit) } ?: return null
        val params = record.optJSONObject("paramLinkedMap")?.let(::stringMap).orEmpty()
        val model = clean(record.text("lightProductModel")) ?: clean(product.text("productModel"))
        val categoryPath = clean(record.text("lightCatalogName")) ?: clean(product.text("productType"))
        val name = clean(record.text("lightProductName")) ?: clean(product.text("productName"))
            ?: model ?: sku
        val brand = clean(record.text("lightBrandName")) ?: clean(product.text("productGradePlateName"))
        val packageName = clean(record.text("lightStandard")) ?: clean(product.text("encapsulationModel"))
            ?: params.entries.firstOrNull { (key, _) -> key.contains("封装") || key.equals("Package", true) }?.value
        val productUrl = "https://item.szlcsc.com/$productId.html"
        val datasheetUrl = datasheetUrl(product)
        return LcscDomesticProduct(
            metadata = ComponentOfficialMetadata(
                source = "lcsc_domestic_web",
                sku = sku,
                name = name,
                packageName = packageName,
                category = categoryPath,
                model = model,
                brand = brand,
                vendor = brand,
                categoryPath = categoryPath,
                officialUrl = productUrl,
                imageUrl = trustedDomesticImageUrl(product.text("breviaryImageUrl")),
                matchedBy = "search",
                confidence = "candidate",
                parameters = params,
                datasheetUrl = datasheetUrl,
            ),
            parameters = params,
            datasheetUrl = datasheetUrl,
        )
    }

    private fun stringMap(json: JSONObject): Map<String, String> = buildMap {
        json.keys().forEach { key ->
            val cleanKey = clean(key)
            val cleanValue = clean(json.optString(key))
            if (cleanKey != null && cleanValue != null) put(cleanKey, cleanValue)
        }
    }

    private fun datasheetUrl(product: JSONObject): String? {
        val groups = product.optJSONArray("fileTypeVOList") ?: return null
        for (index in 0 until groups.length()) {
            val details = groups.optJSONObject(index)?.optJSONArray("detailVOList") ?: continue
            for (detailIndex in 0 until details.length()) {
                val raw = details.optJSONObject(detailIndex)?.text("fileUrl") ?: continue
                val url = if (raw.startsWith("https://")) raw else "https://atta.szlcsc.com/${raw.trimStart('/')}"
                if (trustedUrl(url, setOf("atta.szlcsc.com"))) return url
            }
        }
        return null
    }

    private fun trustedDomesticImageUrl(value: String?): String? = value?.trim()?.takeIf {
        trustedUrl(it, setOf("img.szlcsc.com", "image.szlcsc.com", "static.szlcsc.com", "assets.lcsc.com"))
    }

    private fun trustedUrl(value: String, hosts: Set<String>): Boolean = runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return@runCatching false
        uri.scheme.equals("https", true) && uri.userInfo == null && uri.port == -1 &&
            host in hosts
    }.getOrDefault(false)

    private fun JSONObject.text(key: String): String? = opt(key)?.toString()?.trim()
        ?.takeIf { it.isNotEmpty() && it != "null" }

    private fun clean(value: String?): String? = value?.replace(htmlTagPattern, " ")
        ?.replace("&nbsp;", " ")?.replace("&amp;", "&")
        ?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotEmpty)

    private fun looksLikeVerificationPage(html: String): Boolean =
        listOf("_xvasu", "_xvtsc", "_xvpfs", "_xvpts", "Security verification")
            .any { html.contains(it, ignoreCase = true) }
}
