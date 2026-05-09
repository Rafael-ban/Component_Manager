package com.componentvault.android.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal enum class JlcQrScannerUiState {
    RequestingPermission,
    PermissionDenied,
    StartingCamera,
    Scanning,
    Failed,
}

@Composable
internal fun JlcQrScannerSurface(
    onDismiss: () -> Unit,
    onScanResult: (String) -> Unit,
) {
    val context = LocalContext.current
    val cameraPermissionGranted = remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scannerState by rememberSaveable { mutableStateOf(JlcQrScannerUiState.RequestingPermission) }
    var scannerError by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionId by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted.value = granted
        if (granted) {
            scannerState = JlcQrScannerUiState.StartingCamera
            scannerError = null
            sessionId += 1
        } else {
            scannerState = JlcQrScannerUiState.PermissionDenied
        }
    }

    LaunchedEffect(Unit) {
        if (cameraPermissionGranted.value) {
            scannerState = JlcQrScannerUiState.StartingCamera
            sessionId += 1
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onDismiss)

    JlcQrScannerContent(
        state = scannerState,
        errorMessage = scannerError,
        showCameraPreview = cameraPermissionGranted.value &&
            scannerState != JlcQrScannerUiState.PermissionDenied &&
            scannerState != JlcQrScannerUiState.Failed,
        onBack = onDismiss,
        onGrantCameraAccess = {
            scannerState = JlcQrScannerUiState.RequestingPermission
            permissionLauncher.launch(Manifest.permission.CAMERA)
        },
        onRetry = {
            scannerError = null
            if (cameraPermissionGranted.value) {
                scannerState = JlcQrScannerUiState.StartingCamera
                sessionId += 1
            } else {
                scannerState = JlcQrScannerUiState.RequestingPermission
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onReturnToImport = onDismiss,
        cameraPreview = {
            key(sessionId) {
                JlcQrCameraPreview(
                    onScannerReady = {
                        scannerState = JlcQrScannerUiState.Scanning
                    },
                    onScannerError = { throwable ->
                        scannerError = throwable.message
                        scannerState = JlcQrScannerUiState.Failed
                    },
                    onBarcodeScanned = onScanResult,
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun JlcQrScannerContent(
    state: JlcQrScannerUiState,
    errorMessage: String?,
    showCameraPreview: Boolean,
    onBack: () -> Unit,
    onGrantCameraAccess: () -> Unit,
    onRetry: () -> Unit,
    onReturnToImport: () -> Unit,
    cameraPreview: @Composable BoxScope.() -> Unit = {},
) {
    val strings = vaultStrings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.importer.actionScanQr) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(strings.common.actionBack)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            if (showCameraPreview) {
                cameraPreview()
            }

            when (state) {
                JlcQrScannerUiState.RequestingPermission -> {
                    ScannerMessagePane(
                        title = strings.importer.scannerPermissionTitle,
                        message = strings.importer.scannerPermissionDescription,
                        primaryAction = strings.importer.actionGrantCameraAccess to onGrantCameraAccess,
                        secondaryAction = strings.importer.actionReturnToImport to onReturnToImport,
                    )
                }

                JlcQrScannerUiState.PermissionDenied -> {
                    ScannerMessagePane(
                        title = strings.importer.scannerPermissionDeniedTitle,
                        message = strings.importer.scannerPermissionDeniedDescription,
                        primaryAction = strings.importer.actionGrantCameraAccess to onGrantCameraAccess,
                        secondaryAction = strings.importer.actionReturnToImport to onReturnToImport,
                    )
                }

                JlcQrScannerUiState.StartingCamera -> {
                    ScannerPreviewOverlay(
                        headline = strings.importer.scannerStarting,
                        supporting = strings.importer.scannerScanningHint,
                        showProgress = true,
                    )
                }

                JlcQrScannerUiState.Scanning -> {
                    ScannerPreviewOverlay(
                        headline = strings.importer.scannerScanningHint,
                        supporting = null,
                        showProgress = false,
                    )
                }

                JlcQrScannerUiState.Failed -> {
                    ScannerMessagePane(
                        title = strings.importer.scannerFailedTitle,
                        message = errorMessage ?: strings.importer.scannerFailedDescription,
                        primaryAction = strings.importer.actionRetryScan to onRetry,
                        secondaryAction = strings.importer.actionReturnToImport to onReturnToImport,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScannerMessagePane(
    title: String,
    message: String,
    primaryAction: Pair<String, () -> Unit>,
    secondaryAction: Pair<String, () -> Unit>,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SectionPane(
            title = title,
            supporting = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Button(
                onClick = primaryAction.second,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryAction.first)
            }
            OutlinedButton(
                onClick = secondaryAction.second,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(secondaryAction.first)
            }
        }
    }
}

@Composable
internal fun ScannerPreviewOverlay(
    headline: String,
    supporting: String?,
    showProgress: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(24.dp),
                ),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.65f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showProgress) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                if (!supporting.isNullOrBlank()) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.84f),
                    )
                }
            }
        }
    }
}

@Composable
private fun JlcQrCameraPreview(
    onScannerReady: () -> Unit,
    onScannerError: (Throwable) -> Unit,
    onBarcodeScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(context, lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val isProcessingFrame = AtomicBoolean(false)
        val hasCompleted = AtomicBoolean(false)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            analyzeQrFrame(
                                imageProxy = imageProxy,
                                barcodeScanner = barcodeScanner,
                                mainExecutor = mainExecutor,
                                isProcessingFrame = isProcessingFrame,
                                hasCompleted = hasCompleted,
                                onBarcodeScanned = onBarcodeScanned,
                            )
                        }
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                onScannerReady()
            } catch (throwable: Throwable) {
                onScannerError(throwable)
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

@ExperimentalGetImage
private fun analyzeQrFrame(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    mainExecutor: java.util.concurrent.Executor,
    isProcessingFrame: AtomicBoolean,
    hasCompleted: AtomicBoolean,
    onBarcodeScanned: (String) -> Unit,
) {
    if (hasCompleted.get() || !isProcessingFrame.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        isProcessingFrame.set(false)
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    barcodeScanner.process(image)
        .addOnSuccessListener(mainExecutor) { barcodes ->
            val rawValue = barcodes.firstNotNullOfOrNull { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }
            }
            if (rawValue != null && hasCompleted.compareAndSet(false, true)) {
                onBarcodeScanned(rawValue)
            }
        }
        .addOnCompleteListener(mainExecutor) {
            isProcessingFrame.set(false)
            imageProxy.close()
        }
}
