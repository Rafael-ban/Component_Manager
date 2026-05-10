package com.componentvault.android.data

internal enum class ComponentLabelTemplate(
    val width: Int,
    val height: Int,
    val qrSize: Int,
    val fileSuffix: String,
    val showsSecondaryFields: Boolean,
) {
    Compact(
        width = 720,
        height = 360,
        qrSize = 196,
        fileSuffix = "compact",
        showsSecondaryFields = false,
    ),
    Standard(
        width = 960,
        height = 480,
        qrSize = 260,
        fileSuffix = "standard",
        showsSecondaryFields = true,
    ),
    Large(
        width = 1280,
        height = 640,
        qrSize = 344,
        fileSuffix = "large",
        showsSecondaryFields = true,
    ),
}
