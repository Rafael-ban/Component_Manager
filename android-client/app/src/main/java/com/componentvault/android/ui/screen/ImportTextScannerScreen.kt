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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ImportTextScannerUiState {
    RequestingPermission,
    PermissionDenied,
    StartingCamera,
    Scanning,
    Failed,
}

@Composable
internal fun ImportTextScannerSurface(
    onDismiss: () -> Unit,
    onTextScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val cameraPermissionGranted = remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scannerState by rememberSaveable { mutableStateOf(ImportTextScannerUiState.RequestingPermission) }
    var scannerError by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionId by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted.value = granted
        if (granted) {
            scannerState = ImportTextScannerUiState.StartingCamera
            scannerError = null
            sessionId += 1
        } else {
            scannerState = ImportTextScannerUiState.PermissionDenied
        }
    }

    LaunchedEffect(Unit) {
        if (cameraPermissionGranted.value) {
            scannerState = ImportTextScannerUiState.StartingCamera
            sessionId += 1
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            ImportScannerTopBar(
                title = vaultStrings().importer.actionScanSupplierText,
                onBack = onDismiss,
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
            if (
                cameraPermissionGranted.value &&
                scannerState != ImportTextScannerUiState.PermissionDenied &&
                scannerState != ImportTextScannerUiState.Failed
            ) {
                key(sessionId) {
                    ImportTextCameraPreview(
                        onScannerReady = { scannerState = ImportTextScannerUiState.Scanning },
                        onScannerError = { throwable ->
                            scannerError = throwable.message
                            scannerState = ImportTextScannerUiState.Failed
                        },
                        onTextScanned = onTextScanned,
                    )
                }
            }

            when (scannerState) {
                ImportTextScannerUiState.RequestingPermission -> ScannerMessagePane(
                    title = vaultStrings().importer.scannerPermissionTitle,
                    message = vaultStrings().importer.scannerPermissionDescription,
                    primaryAction = vaultStrings().importer.actionGrantCameraAccess to {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    secondaryAction = vaultStrings().importer.actionReturnToImport to onDismiss,
                )

                ImportTextScannerUiState.PermissionDenied -> ScannerMessagePane(
                    title = vaultStrings().importer.scannerPermissionDeniedTitle,
                    message = vaultStrings().importer.scannerPermissionDeniedDescription,
                    primaryAction = vaultStrings().importer.actionGrantCameraAccess to {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    secondaryAction = vaultStrings().importer.actionReturnToImport to onDismiss,
                )

                ImportTextScannerUiState.StartingCamera -> ScannerPreviewOverlay(
                    headline = vaultStrings().importer.supplierScannerStarting,
                    supporting = vaultStrings().importer.supplierScannerHint,
                    showProgress = true,
                )

                ImportTextScannerUiState.Scanning -> ScannerPreviewOverlay(
                    headline = vaultStrings().importer.supplierScannerHint,
                    supporting = null,
                    showProgress = false,
                )

                ImportTextScannerUiState.Failed -> ScannerMessagePane(
                    title = vaultStrings().importer.supplierScannerFailedTitle,
                    message = scannerError ?: vaultStrings().importer.supplierScanFailedDescription,
                    primaryAction = vaultStrings().importer.actionRetryScan to {
                        scannerError = null
                        if (cameraPermissionGranted.value) {
                            scannerState = ImportTextScannerUiState.StartingCamera
                            sessionId += 1
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    secondaryAction = vaultStrings().importer.actionReturnToImport to onDismiss,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportScannerTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val strings = vaultStrings()
    TopAppBar(
        title = { Text(title) },
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
}

@Composable
private fun ImportTextCameraPreview(
    onScannerReady: () -> Unit,
    onScannerError: (Throwable) -> Unit,
    onTextScanned: (String) -> Unit,
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
        val textRecognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build(),
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
                            analyzeTextFrame(
                                imageProxy = imageProxy,
                                textRecognizer = textRecognizer,
                                mainExecutor = mainExecutor,
                                isProcessingFrame = isProcessingFrame,
                                hasCompleted = hasCompleted,
                                onTextScanned = onTextScanned,
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
            textRecognizer.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}

@ExperimentalGetImage
private fun analyzeTextFrame(
    imageProxy: ImageProxy,
    textRecognizer: TextRecognizer,
    mainExecutor: java.util.concurrent.Executor,
    isProcessingFrame: AtomicBoolean,
    hasCompleted: AtomicBoolean,
    onTextScanned: (String) -> Unit,
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
    textRecognizer.process(image)
        .addOnSuccessListener(mainExecutor) { result ->
            val rawText = result.toNormalizedText()
            if (rawText.isNotBlank() && looksLikeImportableText(rawText) && hasCompleted.compareAndSet(false, true)) {
                onTextScanned(rawText)
            }
        }
        .addOnCompleteListener(mainExecutor) {
            isProcessingFrame.set(false)
            imageProxy.close()
        }
}

private fun Text.toNormalizedText(): String {
    return textBlocks
        .mapNotNull { block ->
            block.lines
                .map { it.text.trim() }
                .filter(String::isNotBlank)
                .takeIf(List<String>::isNotEmpty)
                ?.joinToString(separator = "\n")
        }
        .joinToString(separator = "\n")
        .trim()
}

private fun looksLikeImportableText(rawText: String): Boolean {
    val hasFieldMarker = listOf(
        "\u540D\u79F0",
        "\u578B\u53F7",
        "\u54C1\u724C",
        "\u5C01\u88C5",
        "QTY",
        "Qty",
        "Model",
        "Package",
    ).any { rawText.contains(it, ignoreCase = true) }
    return hasFieldMarker || rawText.length >= 18 || rawText.count { it == '\n' } >= 1
}
