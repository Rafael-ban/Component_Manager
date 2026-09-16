package com.componentvault.android.data.bom

data class BomReleaseLine(
    val requirement: BomRequirement,
    val componentId: String?,
    val componentSku: String?,
    val availableQuantity: Int,
    val expectedUpdatedAt: String?,
    val candidates: List<InventoryMatchCandidate>,
    val allocationPlan: List<BomAllocationPlan> = emptyList(),
    val selectionKeys: List<String> = listOf(requirement.identity.canonicalKey),
)

object BomReleaseAggregator {
    fun aggregate(lines: List<BomReleaseLine>): List<BomReleaseLine> = lines.groupBy { line ->
        line.componentId?.let { "component:$it" }
            ?: "requirement:${line.requirement.identity.canonicalKey}"
    }.map { (key, grouped) ->
        val first = grouped.first()
        if (grouped.size == 1) first else {
            val quantityPerSet = grouped.sumOf { it.requirement.quantityPerSet.toLong() }
            val requiredQuantity = grouped.sumOf { it.requirement.requiredQuantity.toLong() }
            check(quantityPerSet <= Int.MAX_VALUE && requiredQuantity <= Int.MAX_VALUE) {
                "BOM 聚合数量超过整数范围。"
            }
            first.copy(
                requirement = first.requirement.copy(
                    identity = BomRequirementIdentity(key),
                    quantityPerSet = quantityPerSet.toInt(),
                    requiredQuantity = requiredQuantity.toInt(),
                    sourceRows = grouped.flatMap { it.requirement.sourceRows },
                ),
                candidates = grouped.flatMap { it.candidates }.distinctBy { it.inventoryId },
                selectionKeys = grouped.flatMap { it.selectionKeys }.distinct(),
            )
        }
    }
}

data class BomAllocationPlan(val locationId: String, val quantity: Int)

object BomAllocationPlanner {
    fun plan(required: Int, available: List<Pair<String, Int>>): List<BomAllocationPlan> {
        require(required > 0)
        var remaining = required
        val result = mutableListOf<BomAllocationPlan>()
        available.filter { it.second > 0 }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .forEach { (location, quantity) ->
                if (remaining > 0) {
                    val take = minOf(remaining, quantity)
                    result += BomAllocationPlan(location, take)
                    remaining -= take
                }
            }
        require(remaining == 0) { "Allocated stock is insufficient." }
        return result
    }
}

data class BomReleasePreview(
    val parsed: BomParseResult,
    val lines: List<BomReleaseLine>,
    val matchingLines: List<BomReleaseLine> = lines,
) {
    val canCommit: Boolean
        get() = lines.isNotEmpty() &&
            lines.mapNotNull { it.componentId }.distinct().size == lines.size &&
            lines.all {
            it.componentId != null &&
                it.requirement.requiredQuantity > 0 &&
                it.candidates.count { candidate -> candidate.inventoryId == it.componentId } == 1 &&
                it.availableQuantity >= it.requirement.requiredQuantity &&
                it.allocationPlan.sumOf(BomAllocationPlan::quantity) == it.requirement.requiredQuantity
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
