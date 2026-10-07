package com.componentvault.android.data

import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.toLabelSeed

internal enum class LabelWorkbookColumn(val header: String) {
    Name("名称"),
    Sku("SKU"),
    Model("型号"),
    PackageName("封装"),
    Category("分类"),
    Location("库位"),
    Quantity("当前数量"),
    LongQrText("长二维码文本"),
    ShortQrText("短二维码文本"),
}

internal object LabelWorkbookExporter {
    // null identifies stock without a location; the outer null selection means all components.
    data class Row(val component: ComponentRecord, val location: String, val quantity: Int, val locationId: String?)

    val defaultColumns = setOf(
        LabelWorkbookColumn.Name,
        LabelWorkbookColumn.Sku,
        LabelWorkbookColumn.LongQrText,
        LabelWorkbookColumn.ShortQrText,
    )

    fun rows(
        components: List<ComponentRecord>,
        allocations: List<ComponentAllocationRecord> = emptyList(),
        selectedLocationIds: Set<String?>? = null,
    ): List<Row> {
        val active = components.filterNot(ComponentRecord::deleted)
        if (selectedLocationIds == null) return active.map { Row(it, it.location, it.quantity, null) }
        if (selectedLocationIds.isEmpty()) return emptyList()
        val byComponent = allocations.groupBy(ComponentAllocationRecord::componentId)
        return active.flatMap { component ->
            val assigned = byComponent[component.id].orEmpty()
            if (assigned.isNotEmpty()) {
                assigned.filter { it.locationId.takeIf(String::isNotBlank) in selectedLocationIds }
                    .map { Row(component, it.locationId, it.quantity, it.locationId.takeIf(String::isNotBlank)) }
            } else {
                val locationId = component.location.takeIf(String::isNotBlank)
                if (locationId in selectedLocationIds) listOf(Row(component, component.location, component.quantity, locationId))
                else emptyList()
            }
        }
    }

    fun export(components: List<ComponentRecord>, columns: Set<LabelWorkbookColumn>): ByteArray =
        exportRows(rows(components), columns)

    fun exportRows(exportRows: List<Row>, columns: Set<LabelWorkbookColumn>): ByteArray {
        require(columns.isNotEmpty()) { "请至少选择一个导出字段。" }
        val orderedColumns = LabelWorkbookColumn.entries.filter(columns::contains)
        val rows = buildList {
            add(orderedColumns.map { it.header })
            exportRows.forEach { row ->
                val component = row.component
                val seed = component.toLabelSeed().copy(location = row.location, quantity = row.quantity)
                add(orderedColumns.map { column ->
                    when (column) {
                        LabelWorkbookColumn.Name -> component.name
                        LabelWorkbookColumn.Sku -> component.sku
                        LabelWorkbookColumn.Model -> seed.model.orEmpty()
                        LabelWorkbookColumn.PackageName -> component.packageName
                        LabelWorkbookColumn.Category -> component.category
                        LabelWorkbookColumn.Location -> row.location
                        LabelWorkbookColumn.Quantity -> row.quantity
                        LabelWorkbookColumn.LongQrText -> runCatching {
                            ComponentLabelCodec.buildQrPayload(seed, ComponentLabelTemplate.Qr30x40)?.rawValue.orEmpty()
                        }.getOrDefault("")
                        LabelWorkbookColumn.ShortQrText -> runCatching {
                            ComponentLabelCodec.buildQrPayload(seed, ComponentLabelTemplate.Qr10x40)?.rawValue.orEmpty()
                        }.getOrDefault("")
                    }
                })
            }
        }
        return InventoryWorkbookWriter.writeSheets(listOf("标签打印数据" to rows))
    }
}
