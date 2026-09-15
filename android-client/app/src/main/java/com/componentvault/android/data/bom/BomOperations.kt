package com.componentvault.android.data.bom

data class BomReleaseLine(
    val requirement: BomRequirement,
    val componentId: String?,
    val componentSku: String?,
    val availableQuantity: Int,
    val expectedUpdatedAt: String?,
    val candidates: List<InventoryMatchCandidate>,
)

data class BomReleasePreview(
    val parsed: BomParseResult,
    val lines: List<BomReleaseLine>,
) {
    val canCommit: Boolean
        get() = lines.isNotEmpty() &&
            lines.mapNotNull { it.componentId }.distinct().size == lines.size &&
            lines.all {
            it.componentId != null &&
                it.requirement.requiredQuantity > 0 &&
                it.candidates.count { candidate -> candidate.inventoryId == it.componentId } == 1 &&
                it.availableQuantity >= it.requirement.requiredQuantity
            }
}

enum class BomReleaseOutcome {
    APPLIED,
    ALREADY_APPLIED,
    REJECTED,
}

data class BomReleaseResult(
    val outcome: BomReleaseOutcome,
    val message: String,
)

enum class ComponentHubImportOutcome { APPLIED, ALREADY_APPLIED, REJECTED }

data class ComponentHubImportResult(
    val outcome: ComponentHubImportOutcome,
    val message: String,
    val importedCount: Int = 0,
    val skippedCount: Int = 0,
)
