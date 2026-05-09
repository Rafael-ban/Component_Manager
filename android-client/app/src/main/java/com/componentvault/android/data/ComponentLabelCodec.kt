package com.componentvault.android.data

import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportSourceType
import com.componentvault.android.model.ComponentLabelSeed
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class WarehouseLabelPayload(
    val sku: String,
    val name: String,
    val category: String,
    val packageName: String,
    val location: String,
    val quantity: Int,
    val minStock: Int,
    val model: String? = null,
    val brand: String? = null,
)

internal object ComponentLabelCodec {
    private const val WarehouseFormat = "component-vault-label"
    private const val WarehouseVersion = 1

    fun buildQrPayload(seed: ComponentLabelSeed): String {
        val isJlcStyle = seed.sourceLabel?.contains("JLC", ignoreCase = true) == true ||
            seed.rawPayload?.trim()?.startsWith("{on:", ignoreCase = true) == true

        return if (isJlcStyle) {
            buildJlcCompatiblePayload(seed)
        } else {
            encodeWarehouseLabel(
                WarehouseLabelPayload(
                    sku = seed.sku,
                    name = seed.name,
                    category = seed.category,
                    packageName = seed.packageName,
                    location = seed.location,
                    quantity = seed.quantity,
                    minStock = seed.minStock,
                    model = seed.model,
                    brand = seed.brand,
                ),
            )
        }
    }

    fun parseScannedPayload(rawPayload: String): ComponentImportCandidate? {
        decodeWarehouseLabel(rawPayload)?.let { payload ->
            return ComponentImportCandidate(
                sourceType = ComponentImportSourceType.WarehouseLabel,
                rawPayload = rawPayload.trim(),
                sourceLabel = "Warehouse label QR",
                sku = payload.sku,
                name = payload.name,
                packageName = payload.packageName,
                category = payload.category,
                model = payload.model,
                brand = payload.brand,
                suggestedQuantity = payload.quantity,
                notes = buildList {
                    add("Warehouse location: ${payload.location}")
                    add("Minimum stock: ${payload.minStock}")
                },
            )
        }
        return null
    }

    private fun buildJlcCompatiblePayload(seed: ComponentLabelSeed): String {
        val payload = linkedMapOf(
            "on" to encodeJlcValue("local-${seed.sku}"),
            "pc" to encodeJlcValue(seed.sku),
            "pm" to encodeJlcValue(seed.model ?: seed.name),
            "qty" to seed.quantity.toString(),
            "mc" to "null",
            "cc" to "1",
            "pdi" to encodeJlcValue(seed.sku),
            "hp" to "null",
            "pkg" to encodeJlcValue(seed.packageName),
            "nm" to encodeJlcValue(seed.name),
            "br" to encodeJlcValue(seed.brand),
            "cat" to encodeJlcValue(seed.category),
            "loc" to encodeJlcValue(seed.location),
        )

        return payload.entries.joinToString(
            prefix = "{",
            postfix = "}",
            separator = ",",
        ) { (key, value) -> "$key:${value.ifBlank { "null" }}" }
    }

    private fun encodeJlcValue(value: String?): String {
        val normalized = value?.trim().orEmpty()
        if (normalized.isBlank()) {
            return "null"
        }
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8.toString())
    }

    private fun encodeWarehouseLabel(payload: WarehouseLabelPayload): String {
        return JSONObject()
            .put("fmt", WarehouseFormat)
            .put("v", WarehouseVersion)
            .put("sku", payload.sku)
            .put("name", payload.name)
            .put("cat", payload.category)
            .put("pkg", payload.packageName)
            .put("loc", payload.location)
            .put("qty", payload.quantity)
            .put("min", payload.minStock)
            .put("model", payload.model)
            .put("brand", payload.brand)
            .toString()
    }

    private fun decodeWarehouseLabel(rawPayload: String): WarehouseLabelPayload? {
        val trimmed = rawPayload.trim()
        if (!trimmed.startsWith("{")) {
            return null
        }

        return runCatching {
            val payload = JSONObject(trimmed)
            if (payload.optString("fmt") != WarehouseFormat || payload.optInt("v") != WarehouseVersion) {
                null
            } else {
                WarehouseLabelPayload(
                    sku = payload.optString("sku"),
                    name = payload.optString("name"),
                    category = payload.optString("cat"),
                    packageName = payload.optString("pkg"),
                    location = payload.optString("loc"),
                    quantity = payload.optInt("qty"),
                    minStock = payload.optInt("min"),
                    model = payload.optString("model").takeIf { it.isNotBlank() && it != "null" },
                    brand = payload.optString("brand").takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }.getOrNull()
            ?.takeIf {
                it.sku.isNotBlank() && it.name.isNotBlank() && it.packageName.isNotBlank()
            }
    }
}
