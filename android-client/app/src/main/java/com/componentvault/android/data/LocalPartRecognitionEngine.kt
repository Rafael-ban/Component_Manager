package com.componentvault.android.data

import android.content.Context
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentRecognitionMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal class LocalPartRecognitionEngine(
    context: Context,
) {
    private val rules = loadRules(context.applicationContext)

    fun recognitionVersion(): String = rules.version

    fun recognize(
        candidate: ComponentImportCandidate,
        aggressive: Boolean,
    ): ComponentRecognitionMetadata? {
        val values = candidate.recognitionValues()
        val normalizedPackage = detectPackage(values)
        val modelMatch = rules.modelPatterns
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { it.match(values) }
        val modelRule = modelMatch?.rule
        val keywordRule = rules.keywordRules
            .sortedByDescending { it.priority }
            .firstOrNull { rule ->
                (modelRule == null || aggressive || rule.priority >= modelRule.priority) && rule.matches(values)
            }

        val recognizedCategory = modelRule?.category
            ?: keywordRule?.category
            ?: if (aggressive && normalizedPackage != null) {
                val legacyCategory = ComponentCategoryInferencer.infer(
                    candidate.name,
                    normalizedPackage,
                    candidate.model,
                    candidate.brand,
                    candidate.rawPayload,
                )
                legacyCategory.takeUnless { it.equals("General", ignoreCase = true) }
            } else {
                null
            }
        val recognizedPackage = modelMatch?.resolvedPackageName ?: normalizedPackage
        val recognizedVendor = candidate.vendor?.takeIf { it.isNotBlank() }
            ?: candidate.brand?.takeIf { it.isNotBlank() }
            ?: modelRule?.vendor
            ?: keywordRule?.vendor
        val recognizedModelFamily = candidate.modelFamily?.takeIf { it.isNotBlank() }
            ?: modelRule?.modelFamily
            ?: keywordRule?.modelFamily
        val confidence = modelRule?.confidence
            ?: keywordRule?.confidence
            ?: if (recognizedPackage != null || recognizedCategory != null) {
                if (aggressive) {
                    "fallback"
                } else {
                    "low"
                }
            } else {
                null
            }
        val matchedBy = modelRule?.id
            ?: keywordRule?.id
            ?: if (recognizedPackage != null) {
                "package_pattern"
            } else {
                null
            }

        if (
            recognizedPackage.isNullOrBlank() &&
            recognizedCategory.isNullOrBlank() &&
            recognizedVendor.isNullOrBlank() &&
            recognizedModelFamily.isNullOrBlank()
        ) {
            return null
        }

        return ComponentRecognitionMetadata(
            packageName = recognizedPackage,
            category = recognizedCategory,
            vendor = recognizedVendor,
            modelFamily = recognizedModelFamily,
            matchedBy = matchedBy,
            confidence = confidence,
            ruleVersion = rules.version,
        )
    }

    private fun detectPackage(values: RecognitionValues): String? {
        return rules.packagePatterns.firstNotNullOfOrNull { pattern ->
            val regex = pattern.compiled ?: return@firstNotNullOfOrNull null
            if (regex.containsMatchIn(values.combined)) {
                pattern.normalized
            } else {
                null
            }
        }
    }

    private fun ComponentImportCandidate.recognitionValues(): RecognitionValues {
        val combined = buildList {
            add(rawPayload)
            add(name)
            add(packageName)
            add(model.orEmpty())
            add(brand.orEmpty())
            add(vendor.orEmpty())
            add(sku)
        }
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
        return RecognitionValues(
            combined = combined,
            combinedLowercase = combined.lowercase(Locale.US),
        )
    }

    private fun loadRules(context: Context): RecognitionRuleSet {
        val payload = context.assets.open("recognition_rules.json").bufferedReader().use { it.readText() }
        return JSONObject(payload).toRecognitionRuleSet()
    }

    private data class RecognitionValues(
        val combined: String,
        val combinedLowercase: String,
    )

    private data class RecognitionRuleSet(
        val version: String,
        val packagePatterns: List<PackagePattern>,
        val modelPatterns: List<ModelPattern>,
        val keywordRules: List<KeywordRule>,
    )

    private fun JSONObject.toRecognitionRuleSet(): RecognitionRuleSet {
        return RecognitionRuleSet(
            version = optString("version").ifBlank { "unknown" },
            packagePatterns = optJSONArray("package_patterns").toPackagePatterns(),
            modelPatterns = optJSONArray("model_patterns").toModelPatterns(),
            keywordRules = optJSONArray("keyword_rules").toKeywordRules(),
        )
    }

    private data class PackagePattern(
        val normalized: String,
        val compiled: Regex?,
    )

    private data class ModelPattern(
        val id: String,
        val matchType: String,
        val pattern: String,
        val vendor: String?,
        val modelFamily: String?,
        val packageName: String?,
        val packageTemplate: String?,
        val category: String?,
        val confidence: String?,
        val priority: Int,
        val compiled: Regex?,
    ) {
        fun match(values: RecognitionValues): ModelPatternMatch? = when (matchType) {
            "contains" -> if (values.combinedLowercase.contains(pattern.lowercase(Locale.US))) {
                ModelPatternMatch(this, packageName)
            } else {
                null
            }

            "prefix" -> if (
                values.combinedLowercase.contains(" ${pattern.lowercase(Locale.US)}") ||
                values.combinedLowercase.startsWith(pattern.lowercase(Locale.US))
            ) {
                ModelPatternMatch(this, packageName)
            } else {
                null
            }

            else -> compiled?.find(values.combined)?.let { match ->
                val resolvedPackageName = if (packageTemplate.isNullOrBlank()) {
                    packageName
                } else {
                    var templateValue = packageTemplate
                    match.groupValues.forEachIndexed { index, value ->
                        templateValue = requireNotNull(templateValue).replace("\$$index", value)
                    }
                    templateValue?.takeIf { it.isNotBlank() } ?: packageName
                }
                ModelPatternMatch(
                    rule = this,
                    resolvedPackageName = resolvedPackageName,
                )
            }
        }
    }

    private data class ModelPatternMatch(
        val rule: ModelPattern,
        val resolvedPackageName: String?,
    )

    private data class KeywordRule(
        val id: String,
        val keywords: List<String>,
        val vendor: String?,
        val modelFamily: String?,
        val category: String?,
        val confidence: String?,
        val priority: Int,
    ) {
        fun matches(values: RecognitionValues): Boolean {
            return keywords.any { keyword ->
                values.combinedLowercase.contains(keyword.lowercase(Locale.US))
            }
        }
    }

    private fun JSONArray?.toPackagePatterns(): List<PackagePattern> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val normalized = item.optString("normalized").takeIf { it.isNotBlank() } ?: continue
                val pattern = item.optString("pattern").takeIf { it.isNotBlank() }
                add(
                    PackagePattern(
                        normalized = normalized,
                        compiled = pattern?.let { Regex(it, RegexOption.IGNORE_CASE) },
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toModelPatterns(): List<ModelPattern> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val matchType = item.optString("match_type").ifBlank { "regex" }
                val pattern = item.optString("pattern")
                add(
                    ModelPattern(
                        id = item.optString("id").ifBlank { "rule_$index" },
                        matchType = matchType,
                        pattern = pattern,
                        vendor = item.optString("vendor").blankToNull(),
                        modelFamily = item.optString("model_family").blankToNull(),
                        packageName = item.optString("package_name").blankToNull(),
                        packageTemplate = item.optString("package_template").blankToNull(),
                        category = item.optString("category").blankToNull(),
                        confidence = item.optString("confidence").blankToNull(),
                        priority = item.optInt("priority", 0),
                        compiled = if (matchType == "regex" && pattern.isNotBlank()) {
                            Regex(pattern, RegexOption.IGNORE_CASE)
                        } else {
                            null
                        },
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toKeywordRules(): List<KeywordRule> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val keywords = item.optJSONArray("keywords").toStringList()
                if (keywords.isEmpty()) {
                    continue
                }
                add(
                    KeywordRule(
                        id = item.optString("id").ifBlank { "keyword_$index" },
                        keywords = keywords,
                        vendor = item.optString("vendor").blankToNull(),
                        modelFamily = item.optString("model_family").blankToNull(),
                        category = item.optString("category").blankToNull(),
                        confidence = item.optString("confidence").blankToNull(),
                        priority = item.optInt("priority", 0),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).blankToNull() ?: continue
                add(value)
            }
        }
    }

    private fun String.blankToNull(): String? = takeIf { it.isNotBlank() }
}
