package com.componentvault.android.ui.screen.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.componentvault.android.ui.screen.JlcQrScannerContent
import com.componentvault.android.ui.screen.JlcQrScannerUiState

@InventoryPhonePreview
@Composable
private fun QrScannerScanningPreview() {
    PreviewHost {
        JlcQrScannerContent(
            state = JlcQrScannerUiState.Scanning,
            errorMessage = null,
            showCameraPreview = true,
            onBack = {},
            onGrantCameraAccess = {},
            onRetry = {},
            onReturnToImport = {},
            cameraPreview = {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B1B1F)),
                )
            },
        )
    }
}

@InventoryLargeFontPreview
@Composable
private fun QrScannerPermissionDeniedPreview() {
    PreviewHost(strings = PreviewComponentVaultStrings.ZhCn) {
        JlcQrScannerContent(
            state = JlcQrScannerUiState.PermissionDenied,
            errorMessage = null,
            showCameraPreview = false,
            onBack = {},
            onGrantCameraAccess = {},
            onRetry = {},
            onReturnToImport = {},
        )
    }
}

@InventoryDarkPreview
@Composable
private fun QrScannerFailedPreview() {
    PreviewHost {
        JlcQrScannerContent(
            state = JlcQrScannerUiState.Failed,
            errorMessage = "Camera initialization timeout.",
            showCameraPreview = false,
            onBack = {},
            onGrantCameraAccess = {},
            onRetry = {},
            onReturnToImport = {},
        )
    }
}
