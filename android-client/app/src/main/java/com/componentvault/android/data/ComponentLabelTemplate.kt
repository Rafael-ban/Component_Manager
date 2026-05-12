package com.componentvault.android.data

internal enum class ComponentLabelKind {
    Qr,
    TextOnly,
}

internal enum class ComponentLabelRole {
    Primary,
    Companion,
}

internal enum class ComponentLabelPayloadMode {
    None,
    CompactOffline,
    StandardWarehouse,
    JlcCompatible,
}

internal enum class ComponentLabelOrientation {
    Portrait,
    Landscape,
}

internal enum class ComponentLabelLayoutMode {
    LandscapeRightQr,
    LeftQrRightDetails,
    TextOnly,
}

internal enum class ComponentTextLabelTemplate(
    val id: String,
    val fileSuffix: String,
) {
    NameSku(
        id = "name-sku",
        fileSuffix = "name-sku",
    ),
    NamePackageSku(
        id = "name-package-sku",
        fileSuffix = "name-package-sku",
    ),
    NameModel(
        id = "name-model",
        fileSuffix = "name-model",
    );

    companion object {
        val default: ComponentTextLabelTemplate = NameSku

        fun fromId(id: String?): ComponentTextLabelTemplate =
            entries.firstOrNull { it.id == id } ?: default
    }
}

internal data class ComponentLabelTemplate(
    val id: String,
    val kind: ComponentLabelKind,
    val role: ComponentLabelRole,
    val widthMm: Float?,
    val heightMm: Float,
    val nominalWidthMm: Float? = widthMm,
    val nominalHeightMm: Float = heightMm,
    val orientation: ComponentLabelOrientation = ComponentLabelOrientation.Portrait,
    val textHeightMm: Float? = null,
    val qrSizeMm: Float? = null,
    val quietZoneMm: Float = 0f,
    val payloadMode: ComponentLabelPayloadMode = ComponentLabelPayloadMode.None,
    val layoutMode: ComponentLabelLayoutMode,
    val fileSuffix: String,
) {
    val isQrLabel: Boolean
        get() = kind == ComponentLabelKind.Qr

    val supportsCompanionTextLabel: Boolean
        get() = isQrLabel

    companion object {
        val Qr10x40 = ComponentLabelTemplate(
            id = "qr-10x40",
            kind = ComponentLabelKind.Qr,
            role = ComponentLabelRole.Primary,
            widthMm = 40f,
            heightMm = 10f,
            nominalWidthMm = 10f,
            nominalHeightMm = 40f,
            orientation = ComponentLabelOrientation.Landscape,
            qrSizeMm = 8f,
            quietZoneMm = 0.8f,
            payloadMode = ComponentLabelPayloadMode.CompactOffline,
            layoutMode = ComponentLabelLayoutMode.LandscapeRightQr,
            fileSuffix = "10x40-qr",
        )

        val Qr30x40 = ComponentLabelTemplate(
            id = "qr-30x40",
            kind = ComponentLabelKind.Qr,
            role = ComponentLabelRole.Primary,
            widthMm = 30f,
            heightMm = 40f,
            nominalWidthMm = 30f,
            nominalHeightMm = 40f,
            qrSizeMm = 16f,
            quietZoneMm = 1.2f,
            payloadMode = ComponentLabelPayloadMode.StandardWarehouse,
            layoutMode = ComponentLabelLayoutMode.LeftQrRightDetails,
            fileSuffix = "30x40-qr",
        )

        val TextOnly = ComponentLabelTemplate(
            id = "text-auto",
            kind = ComponentLabelKind.TextOnly,
            role = ComponentLabelRole.Companion,
            widthMm = null,
            heightMm = 4f,
            textHeightMm = 0.5f,
            layoutMode = ComponentLabelLayoutMode.TextOnly,
            fileSuffix = "text-strip",
        )

        val entries: List<ComponentLabelTemplate> = listOf(
            Qr10x40,
            Qr30x40,
            TextOnly,
        )

        val default: ComponentLabelTemplate = Qr30x40

        fun fromId(id: String?): ComponentLabelTemplate =
            entries.firstOrNull { it.id == id } ?: default
    }
}
