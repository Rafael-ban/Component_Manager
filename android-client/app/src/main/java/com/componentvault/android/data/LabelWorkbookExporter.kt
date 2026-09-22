package com.componentvault.android.data

import com.componentvault.android.model.ComponentRecord
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
    val defaultColumns = setOf(
        LabelWorkbookColumn.Name,
        LabelWorkbookColumn.Sku,
        LabelWorkbookColumn.LongQrText,
        LabelWorkbookColumn.ShortQrText,
    )

    fun export(components: List<ComponentRecord>, columns: Set<LabelWorkbookColumn>): ByteArray {
        require(columns.isNotEmpty()) { "请至少选择一个导出字段。" }
        val orderedColumns = LabelWorkbookColumn.entries.filter(columns::contains)
        val rows = buildList {
            add(orderedColumns.map { it.header })
            components.asSequence().filterNot(ComponentRecord::deleted).forEach { component ->
                val seed = component.toLabelSeed()
                add(orderedColumns.map { column ->
                    when (column) {
                        LabelWorkbookColumn.Name -> component.name
                        LabelWorkbookColumn.Sku -> component.sku
                        LabelWorkbookColumn.Model -> seed.model.orEmpty()
                        LabelWorkbookColumn.PackageName -> component.packageName
                        LabelWorkbookColumn.Category -> component.category
                        LabelWorkbookColumn.Location -> component.location
                        LabelWorkbookColumn.Quantity -> component.quantity
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
