package com.componentvault.android.model

enum class ComponentImportSourceType {
    JlcText,
    JlcQr,
    SupplierOcr,
    WarehouseLabel,
}

enum class ComponentImportFieldOrigin {
    Parsed,
    Learned,
    Server,
    User,
}

data class ComponentImportFieldOrigins(
    val sku: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
    val name: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
    val category: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
    val packageName: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
    val model: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
    val brand: ComponentImportFieldOrigin = ComponentImportFieldOrigin.Parsed,
)

enum class ComponentImportLearningMatchType {
    Sku,
    Mpn,
}

data class ComponentImportLearningMapping(
    val id: String,
    val sourceType: ComponentImportSourceType,
    val sourceSku: String? = null,
    val sourceMpn: String? = null,
    val resolvedName: String? = null,
    val resolvedCategory: String? = null,
    val resolvedPackageName: String? = null,
    val resolvedModel: String? = null,
    val resolvedBrand: String? = null,
    val resolvedDescription: String? = null,
    val confidence: Int = 100,
    val lastUsedAt: String? = null,
)

data class ComponentImportLearningMatch(
    val mapping: ComponentImportLearningMapping,
    val matchedBy: ComponentImportLearningMatchType,
)

data class ImportLearningSummary(
    val mappingCount: Int = 0,
)

data class ComponentImportCandidate(
    val sourceType: ComponentImportSourceType,
    val rawPayload: String,
    val sourceLabel: String,
    val sku: String = "",
    val name: String = "",
    val packageName: String = "",
    val category: String = "",
    val model: String? = null,
    val brand: String? = null,
    val suggestedQuantity: Int? = null,
    val notes: List<String> = emptyList(),
    val fieldOrigins: ComponentImportFieldOrigins = ComponentImportFieldOrigins(),
) {
    fun toComponentDraft(
        quantity: Int,
        location: String,
        minStock: Int,
        skuOverride: String = sku,
        nameOverride: String = name,
        categoryOverride: String = category,
        packageNameOverride: String = packageName,
        modelOverride: String? = model,
        brandOverride: String? = brand,
        notesOverride: List<String> = notes,
    ): ComponentDraft {
        val description = buildImportDescription(
            sourceLabel = sourceLabel,
            rawPayload = rawPayload,
            model = modelOverride,
            brand = brandOverride,
            notes = notesOverride,
        )

        return ComponentDraft(
            sku = skuOverride.trim(),
            name = nameOverride.trim(),
            category = categoryOverride.trim(),
            packageName = packageNameOverride.trim(),
            location = location.trim(),
            description = description,
            quantity = quantity,
            minStock = minStock,
        )
    }
}

val ComponentImportCandidate.isJlcSource: Boolean
    get() = sourceType == ComponentImportSourceType.JlcText || sourceType == ComponentImportSourceType.JlcQr

enum class ComponentOfficialLookupOutcome {
    Success,
    NoMatch,
    NotConfigured,
    Failed,
}

data class ComponentOfficialMetadata(
    val sku: String? = null,
    val name: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val model: String? = null,
    val brand: String? = null,
    val categoryPath: String? = null,
    val officialUrl: String? = null,
    val matchedBy: String? = null,
    val confidence: String? = null,
)

data class ComponentOfficialLookupResult(
    val outcome: ComponentOfficialLookupOutcome,
    val metadata: ComponentOfficialMetadata? = null,
    val message: String? = null,
    val fromCache: Boolean = false,
)

data class ComponentImportResolution(
    val candidate: ComponentImportCandidate,
    val learningMatch: ComponentImportLearningMatch? = null,
    val officialLookupResult: ComponentOfficialLookupResult? = null,
)

fun ComponentImportCandidate.withLearningMapping(
    learningMatch: ComponentImportLearningMatch,
): ComponentImportCandidate {
    val mapping = learningMatch.mapping
    val mergedNotes = buildList {
        addAll(notes)
        addIfMissing(
            "Local import learning: matched by ${
                when (learningMatch.matchedBy) {
                    ComponentImportLearningMatchType.Sku -> "SKU"
                    ComponentImportLearningMatchType.Mpn -> "MPN"
                }
            }",
        )
    }

    return copy(
        name = mapping.resolvedName?.takeIf { it.isNotBlank() } ?: name,
        packageName = mapping.resolvedPackageName?.takeIf { it.isNotBlank() } ?: packageName,
        category = mapping.resolvedCategory?.takeIf { it.isNotBlank() } ?: category,
        model = mapping.resolvedModel?.takeIf { it.isNotBlank() } ?: model,
        brand = mapping.resolvedBrand?.takeIf { it.isNotBlank() } ?: brand,
        notes = mergedNotes,
        fieldOrigins = fieldOrigins.copy(
            name = if (!mapping.resolvedName.isNullOrBlank()) {
                ComponentImportFieldOrigin.Learned
            } else {
                fieldOrigins.name
            },
            category = if (!mapping.resolvedCategory.isNullOrBlank()) {
                ComponentImportFieldOrigin.Learned
            } else {
                fieldOrigins.category
            },
            packageName = if (!mapping.resolvedPackageName.isNullOrBlank()) {
                ComponentImportFieldOrigin.Learned
            } else {
                fieldOrigins.packageName
            },
            model = if (!mapping.resolvedModel.isNullOrBlank()) {
                ComponentImportFieldOrigin.Learned
            } else {
                fieldOrigins.model
            },
            brand = if (!mapping.resolvedBrand.isNullOrBlank()) {
                ComponentImportFieldOrigin.Learned
            } else {
                fieldOrigins.brand
            },
        ),
    )
}

fun ComponentImportCandidate.withOfficialMetadata(
    metadata: ComponentOfficialMetadata,
): ComponentImportCandidate {
    val mergedNotes = buildList {
        addAll(notes)
        metadata.matchedBy?.let { addIfMissing("Official lookup: matched by ${it.uppercase()}") }
        metadata.categoryPath?.let { addIfMissing("Official category path: $it") }
        metadata.officialUrl?.let { addIfMissing("Official URL: $it") }
    }

    return copy(
        sku = sku.ifBlank { metadata.sku?.takeIf { it.isNotBlank() }.orEmpty() },
        name = name.ifBlank { metadata.name?.takeIf { it.isNotBlank() }.orEmpty() },
        packageName = packageName.ifBlank { metadata.packageName?.takeIf { it.isNotBlank() }.orEmpty() },
        category = category.ifBlank { metadata.category?.takeIf { it.isNotBlank() }.orEmpty() },
        model = model?.takeIf { it.isNotBlank() } ?: metadata.model?.takeIf { it.isNotBlank() },
        brand = brand?.takeIf { it.isNotBlank() } ?: metadata.brand?.takeIf { it.isNotBlank() },
        notes = mergedNotes,
        fieldOrigins = fieldOrigins.copy(
            sku = if (sku.isBlank() && !metadata.sku.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.sku
            },
            name = if (name.isBlank() && !metadata.name.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.name
            },
            category = if (category.isBlank() && !metadata.category.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.category
            },
            packageName = if (packageName.isBlank() && !metadata.packageName.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.packageName
            },
            model = if (model.isNullOrBlank() && !metadata.model.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.model
            },
            brand = if (brand.isNullOrBlank() && !metadata.brand.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.brand
            },
        ),
    )
}

data class ParsedImportDescription(
    val model: String? = null,
    val brand: String? = null,
    val sourceLabel: String? = null,
    val rawPayload: String? = null,
    val notes: List<String> = emptyList(),
)

data class ComponentLabelSeed(
    val sku: String,
    val name: String,
    val category: String,
    val packageName: String,
    val location: String,
    val quantity: Int,
    val minStock: Int,
    val model: String? = null,
    val brand: String? = null,
    val sourceLabel: String? = null,
    val rawPayload: String? = null,
    val notes: List<String> = emptyList(),
)

fun ComponentDraft.toLabelSeed(): ComponentLabelSeed {
    val parsed = parseImportDescription(description)
    return ComponentLabelSeed(
        sku = sku,
        name = name,
        category = category,
        packageName = packageName,
        location = location,
        quantity = quantity,
        minStock = minStock,
        model = parsed.model,
        brand = parsed.brand,
        sourceLabel = parsed.sourceLabel,
        rawPayload = parsed.rawPayload,
        notes = parsed.notes,
    )
}

fun ComponentRecord.toLabelSeed(): ComponentLabelSeed {
    val parsed = parseImportDescription(description)
    return ComponentLabelSeed(
        sku = sku,
        name = name,
        category = category,
        packageName = packageName,
        location = location,
        quantity = quantity,
        minStock = minStock,
        model = parsed.model,
        brand = parsed.brand,
        sourceLabel = parsed.sourceLabel,
        rawPayload = parsed.rawPayload,
        notes = parsed.notes,
    )
}

fun buildImportDescription(
    sourceLabel: String,
    rawPayload: String,
    model: String?,
    brand: String?,
    notes: List<String>,
): String {
    val descriptionLines = buildList {
        if (!model.isNullOrBlank()) {
            add("Model: ${model.trim()}")
        }
        if (!brand.isNullOrBlank()) {
            add("Brand: ${brand.trim()}")
        }
        addAll(notes.map(String::trim).filter(String::isNotBlank))
        add("Import source: ${sourceLabel.trim()}")
        add("Raw payload: ${rawPayload.trim()}")
    }
    return descriptionLines.joinToString(separator = "\n")
}

fun parseImportDescription(description: String): ParsedImportDescription {
    var model: String? = null
    var brand: String? = null
    var sourceLabel: String? = null
    var rawPayload: String? = null
    val notes = mutableListOf<String>()

    description.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            when {
                line.startsWith("Model: ") -> model = line.removePrefix("Model: ").trim().blankToNull()
                line.startsWith("Brand: ") -> brand = line.removePrefix("Brand: ").trim().blankToNull()
                line.startsWith("Import source: ") ->
                    sourceLabel = line.removePrefix("Import source: ").trim().blankToNull()
                line.startsWith("Raw payload: ") ->
                    rawPayload = line.removePrefix("Raw payload: ").trim().blankToNull()
                else -> notes += line
            }
        }

    return ParsedImportDescription(
        model = model,
        brand = brand,
        sourceLabel = sourceLabel,
        rawPayload = rawPayload,
        notes = notes,
    )
}

private fun MutableList<String>.addIfMissing(value: String) {
    if (none { it.equals(value, ignoreCase = true) }) {
        add(value)
    }
}

private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
