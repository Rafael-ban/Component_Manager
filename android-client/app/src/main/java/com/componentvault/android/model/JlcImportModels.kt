package com.componentvault.android.model

enum class JlcImportSource {
    Text,
    Qr,
}

data class JlcImportPayload(
    val source: JlcImportSource,
    val rawPayload: String,
    val sourceLabel: String,
    val sku: String,
    val name: String,
    val packageName: String,
    val category: String,
    val model: String? = null,
    val brand: String? = null,
    val suggestedQuantity: Int? = null,
    val notes: List<String> = emptyList(),
) {
    fun toComponentDraft(
        quantity: Int,
        location: String,
        minStock: Int,
    ): ComponentDraft {
        val descriptionLines = buildList {
            if (!model.isNullOrBlank()) {
                add("Model: $model")
            }
            if (!brand.isNullOrBlank()) {
                add("Brand: $brand")
            }
            addAll(notes.filter { it.isNotBlank() })
            add("Import source: $sourceLabel")
            add("Raw payload: $rawPayload")
        }

        return ComponentDraft(
            sku = sku,
            name = name,
            category = category,
            packageName = packageName,
            location = location,
            description = descriptionLines.joinToString(separator = "\n"),
            quantity = quantity,
            minStock = minStock,
        )
    }
}
