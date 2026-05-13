package com.componentvault.android.model

enum class MovementQuickAction(
    val movementType: String,
) {
    Inbound("inbound"),
    Outbound("outbound"),
    Adjustment("adjustment"),
}

enum class MovementScanMatchStatus {
    Idle,
    Matched,
    InvalidLabel,
    NotFound,
    Ambiguous,
}

data class MovementScanResolutionUiState(
    val matchStatus: MovementScanMatchStatus = MovementScanMatchStatus.Idle,
    val rawValue: String = "",
    val parsedSku: String = "",
    val parsedName: String = "",
    val parsedPackageName: String = "",
    val parsedLocation: String = "",
    val matchedComponent: ComponentRecord? = null,
)

data class MovementScanUiState(
    val isScannerVisible: Boolean = false,
    val isResolving: Boolean = false,
    val resolution: MovementScanResolutionUiState = MovementScanResolutionUiState(),
)
