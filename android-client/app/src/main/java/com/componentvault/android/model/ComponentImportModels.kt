package com.componentvault.android.model

enum class ComponentImportSourceType {
    JlcText,
    JlcQr,
    SupplierOcr,
    WarehouseLabel,
}

enum class ComponentImportFieldOrigin {
    Parsed,
    Rule,
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
    val vendor: String? = null,
    val modelFamily: String? = null,
    val recognitionConfidence: String? = null,
    val matchedBy: String? = null,
    val normalizedPackageKey: String? = null,
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
    val source: String? = null,
    val sku: String? = null,
    val name: String? = null,
    val packageName: String? = null,
    val category: String? = null,
    val model: String? = null,
    val brand: String? = null,
    val vendor: String? = null,
    val modelFamily: String? = null,
    val categoryPath: String? = null,
    val officialUrl: String? = null,
    val matchedBy: String? = null,
    val confidence: String? = null,
    val ruleVersion: String? = null,
)

data class ComponentRecognitionMetadata(
    val source: String = "local_rules",
    val packageName: String? = null,
    val category: String? = null,
    val vendor: String? = null,
    val modelFamily: String? = null,
    val matchedBy: String? = null,
    val confidence: String? = null,
    val ruleVersion: String? = null,
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

fun ComponentImportCandidate.referenceDisplayName(): String? {
    val referenceBrand = (vendor ?: brand).orEmpty().trim()
    val referenceModel = model.orEmpty().trim()
    val referenceSku = sku.trim()

    return when {
        referenceModel.isNotBlank() && referenceBrand.isNotBlank() &&
            !referenceModel.contains(referenceBrand, ignoreCase = true) ->
            "$referenceBrand $referenceModel"
        referenceModel.isNotBlank() -> referenceModel
        referenceSku.isNotBlank() -> referenceSku
        else -> null
    }
}

fun ComponentImportCandidate.withLearningMapping(
    learningMatch: ComponentImportLearningMatch,
): ComponentImportCandidate {
    val mapping = learningMatch.mapping
    val mergedNotes = buildList {
        addAll(notes)
        addIfMissing(
            "本地导入学习：匹配方式 ${
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

fun ComponentImportCandidate.withRecognitionMetadata(
    metadata: ComponentRecognitionMetadata,
): ComponentImportCandidate {
    val resolvedPackageName = when {
        !metadata.packageName.isNullOrBlank() && packageName.isBlank() -> metadata.packageName
        !metadata.packageName.isNullOrBlank() && packageName.equals(sku, ignoreCase = true) -> metadata.packageName
        !metadata.packageName.isNullOrBlank() && !model.isNullOrBlank() &&
            packageName.equals(model, ignoreCase = true) -> metadata.packageName
        else -> packageName
    }.orEmpty()

    val resolvedCategory = when {
        !metadata.category.isNullOrBlank() && category.isBlank() -> metadata.category
        !metadata.category.isNullOrBlank() && category.equals("General", ignoreCase = true) -> metadata.category
        !metadata.category.isNullOrBlank() && category.count { it == '/' } < metadata.category.count { it == '/' } ->
            metadata.category
        else -> category
    }.orEmpty()

    val mergedNotes = buildList {
        addAll(notes)
        metadata.vendor?.let { addIfMissing("识别厂商：$it") }
        metadata.modelFamily?.let { addIfMissing("识别型号族：$it") }
        metadata.matchedBy?.let { addIfMissing("识别命中方式：$it") }
        metadata.confidence?.let { addIfMissing("识别置信度：$it") }
        metadata.ruleVersion?.let { addIfMissing("识别规则版本：$it") }
    }

    val resolvedBrand = brand?.takeIf { it.isNotBlank() }
        ?: vendor?.takeIf { it.isNotBlank() }
        ?: metadata.vendor?.takeIf { it.isNotBlank() }
    val resolvedVendor = vendor?.takeIf { it.isNotBlank() }
        ?: metadata.vendor?.takeIf { it.isNotBlank() }

    return copy(
        packageName = resolvedPackageName,
        category = resolvedCategory,
        brand = resolvedBrand,
        vendor = resolvedVendor,
        modelFamily = modelFamily?.takeIf { it.isNotBlank() } ?: metadata.modelFamily?.takeIf { it.isNotBlank() },
        recognitionConfidence = metadata.confidence?.takeIf { it.isNotBlank() } ?: recognitionConfidence,
        matchedBy = metadata.matchedBy?.takeIf { it.isNotBlank() } ?: matchedBy,
        normalizedPackageKey = metadata.packageName?.takeIf { it.isNotBlank() } ?: normalizedPackageKey,
        notes = mergedNotes,
        fieldOrigins = fieldOrigins.copy(
            category = if (resolvedCategory != category) {
                ComponentImportFieldOrigin.Rule
            } else {
                fieldOrigins.category
            },
            packageName = if (resolvedPackageName != packageName) {
                ComponentImportFieldOrigin.Rule
            } else {
                fieldOrigins.packageName
            },
            brand = if (brand.isNullOrBlank() && !resolvedBrand.isNullOrBlank()) {
                ComponentImportFieldOrigin.Rule
            } else {
                fieldOrigins.brand
            },
        ),
    )
}

fun ComponentImportCandidate.withOfficialMetadata(
    metadata: ComponentOfficialMetadata,
): ComponentImportCandidate {
    val resolvedName = when {
        metadata.name.isNullOrBlank() -> name
        name.isBlank() -> metadata.name
        name.isLikelyModelLike(sku = sku, model = model) -> metadata.name
        name.equals(sku, ignoreCase = true) -> metadata.name
        else -> name
    }.orEmpty()

    val resolvedPackageName = when {
        packageName.isBlank() && !metadata.packageName.isNullOrBlank() -> metadata.packageName
        !metadata.packageName.isNullOrBlank() && packageName.equals(sku, ignoreCase = true) -> metadata.packageName
        !metadata.packageName.isNullOrBlank() && !model.isNullOrBlank() &&
            packageName.equals(model, ignoreCase = true) -> metadata.packageName
        !metadata.packageName.isNullOrBlank() && recognitionConfidence.equals("fallback", ignoreCase = true) ->
            metadata.packageName
        else -> packageName
    }.orEmpty()

    val resolvedCategory = when {
        category.isBlank() && !metadata.category.isNullOrBlank() -> metadata.category
        category.equals("General", ignoreCase = true) && !metadata.category.isNullOrBlank() -> metadata.category
        !metadata.category.isNullOrBlank() && category.count { it == '/' } < metadata.category.count { it == '/' } ->
            metadata.category
        !metadata.category.isNullOrBlank() && recognitionConfidence.equals("fallback", ignoreCase = true) ->
            metadata.category
        else -> category
    }.orEmpty()

    val mergedNotes = buildList {
        addAll(notes)
        metadata.source?.let { addIfMissing("识别来源：$it") }
        metadata.vendor?.let { addIfMissing("识别厂商：$it") }
        metadata.modelFamily?.let { addIfMissing("识别型号族：$it") }
        metadata.matchedBy?.let { addIfMissing("官方查询：匹配方式 ${it.uppercase()}") }
        metadata.categoryPath?.let { addIfMissing("官方分类路径：$it") }
        metadata.officialUrl?.let { addIfMissing("官方链接：$it") }
        metadata.ruleVersion?.let { addIfMissing("识别规则版本：$it") }
    }

    val resolvedBrand = brand?.takeIf { it.isNotBlank() }
        ?: vendor?.takeIf { it.isNotBlank() }
        ?: metadata.brand?.takeIf { it.isNotBlank() }
        ?: metadata.vendor?.takeIf { it.isNotBlank() }
    val resolvedVendor = vendor?.takeIf { it.isNotBlank() }
        ?: metadata.vendor?.takeIf { it.isNotBlank() }
        ?: metadata.brand?.takeIf { it.isNotBlank() }

    return copy(
        sku = sku.ifBlank { metadata.sku?.takeIf { it.isNotBlank() }.orEmpty() },
        name = resolvedName,
        packageName = resolvedPackageName,
        category = resolvedCategory,
        model = model?.takeIf { it.isNotBlank() } ?: metadata.model?.takeIf { it.isNotBlank() },
        brand = resolvedBrand,
        vendor = resolvedVendor,
        modelFamily = modelFamily?.takeIf { it.isNotBlank() } ?: metadata.modelFamily?.takeIf { it.isNotBlank() },
        recognitionConfidence = metadata.confidence?.takeIf { it.isNotBlank() } ?: recognitionConfidence,
        matchedBy = metadata.matchedBy?.takeIf { it.isNotBlank() } ?: matchedBy,
        normalizedPackageKey = metadata.packageName?.takeIf { it.isNotBlank() } ?: normalizedPackageKey,
        notes = mergedNotes,
        fieldOrigins = fieldOrigins.copy(
            sku = if (sku.isBlank() && !metadata.sku.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.sku
            },
            name = if (resolvedName != name && !metadata.name.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.name
            },
            category = if (resolvedCategory != category && !metadata.category.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.category
            },
            packageName = if (resolvedPackageName != packageName && !metadata.packageName.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.packageName
            },
            model = if (model.isNullOrBlank() && !metadata.model.isNullOrBlank()) {
                ComponentImportFieldOrigin.Server
            } else {
                fieldOrigins.model
            },
            brand = if (brand.isNullOrBlank() && !resolvedBrand.isNullOrBlank()) {
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
            add("型号：${model.trim()}")
        }
        if (!brand.isNullOrBlank()) {
            add("品牌：${brand.trim()}")
        }
        addAll(notes.map(String::trim).filter(String::isNotBlank))
        add("导入来源：${sourceLabel.trim()}")
        add("原始载荷：${rawPayload.trim()}")
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
                line.startsWith("Model: ") || line.startsWith("型号：") ->
                    model = line
                        .removePrefix("Model: ")
                        .removePrefix("型号：")
                        .trim()
                        .blankToNull()
                line.startsWith("Brand: ") || line.startsWith("品牌：") ->
                    brand = line
                        .removePrefix("Brand: ")
                        .removePrefix("品牌：")
                        .trim()
                        .blankToNull()
                line.startsWith("Import source: ") || line.startsWith("导入来源：") ->
                    sourceLabel = line
                        .removePrefix("Import source: ")
                        .removePrefix("导入来源：")
                        .trim()
                        .blankToNull()
                line.startsWith("Raw payload: ") || line.startsWith("原始载荷：") ->
                    rawPayload = line
                        .removePrefix("Raw payload: ")
                        .removePrefix("原始载荷：")
                        .trim()
                        .blankToNull()
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

private fun String.isLikelyModelLike(
    sku: String,
    model: String?,
): Boolean {
    val normalized = trim()
    if (normalized.isBlank()) {
        return false
    }
    if (normalized.equals(sku.trim(), ignoreCase = true)) {
        return true
    }
    if (!model.isNullOrBlank() && normalized.equals(model.trim(), ignoreCase = true)) {
        return true
    }
    if (normalized.any { it.code in 0x4E00..0x9FFF } || normalized.any(Char::isWhitespace)) {
        return false
    }
    val alphaNumericCount = normalized.count(Char::isLetterOrDigit)
    val hasSeparator = normalized.any { it == '-' || it == '_' || it == '/' || it == '.' }
    return alphaNumericCount >= 5 && (hasSeparator || normalized.any(Char::isDigit))
}

private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
