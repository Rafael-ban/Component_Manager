package com.componentvault.android.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.componentvault.android.data.ocr.OcrEngineFactory
import com.componentvault.android.data.ocr.OcrResult
import com.componentvault.android.data.ocr.normalizedText
import com.componentvault.android.model.OcrEngineMode
import kotlinx.coroutines.launch

internal enum class ImportTextScannerUiState {
    RequestingPermission,
    PermissionDenied,
    StartingCamera,
    Aligning,
    Recognizing,
    Failed,
}

@Composable
internal fun ImportTextScannerSurface(
    onDismiss: () -> Unit,
    preferredOcrEngineMode: OcrEngineMode,
    onOcrScanned: (OcrResult) -> Unit,
) {
    val context = LocalContext.current
    val strings = vaultStrings()
    val scope = rememberCoroutineScope()
    val ocrEngine = remember(preferredOcrEngineMode) {
        OcrEngineFactory.create(preferredOcrEngineMode)
    }
    val cameraPermissionGranted = remember(context) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scannerState by rememberSaveable { mutableStateOf(ImportTextScannerUiState.RequestingPermission) }
    var scannerError by rememberSaveable { mutableStateOf<String?>(null) }
    var sessionId by rememberSaveable { mutableIntStateOf(0) }
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }
    var captureFrame by remember { mutableStateOf<(() -> Bitmap?)?>(null) }

    fun restartScanner() {
        scannerError = null
        frozenFrame = null
        captureFrame = null
        if (cameraPermissionGranted.value) {
            scannerState = ImportTextScannerUiState.StartingCamera
            sessionId += 1
        } else {
            scannerState = ImportTextScannerUiState.RequestingPermission
        }
    }

    fun startRecognition() {
        val bitmap = captureFrame?.invoke()
        if (bitmap == null) {
            scannerError = strings.importer.supplierScannerFrameUnavailable
            scannerState = ImportTextScannerUiState.Failed
            return
        }

        frozenFrame = bitmap
        scannerError = null
        scannerState = ImportTextScannerUiState.Recognizing

        scope.launch {
            runCatching { ocrEngine.recognize(bitmap) }
                .onSuccess { result ->
                    if (result.normalizedText().isBlank()) {
                        scannerError = strings.importer.supplierScanNoTextDescription
                        scannerState = ImportTextScannerUiState.Failed
                    } else {
                        onOcrScanned(result)
                    }
                }
                .onFailure { throwable ->
                    scannerError = throwable.message ?: strings.importer.supplierScanFailedDescription
                    scannerState = ImportTextScannerUiState.Failed
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraPermissionGranted.value = granted
        if (granted) {
            restartScanner()
        } else {
            scannerState = ImportTextScannerUiState.PermissionDenied
        }
    }

    LaunchedEffect(Unit) {
        if (cameraPermissionGranted.value) {
            restartScanner()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            ImportScannerTopBar(
                title = strings.importer.actionScanSupplierText,
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
                        onScannerReady = {
                            if (scannerState == ImportTextScannerUiState.StartingCamera) {
                                scannerState = ImportTextScannerUiState.Aligning
                            }
                        },
                        onScannerError = { throwable ->
                            scannerError = throwable.message
                            scannerState = ImportTextScannerUiState.Failed
                        },
                        onCaptureFrameReady = { capture -> captureFrame = capture },
                    )
                }
            }

            frozenFrame?.takeIf { scannerState == ImportTextScannerUiState.Recognizing }?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            when (scannerState) {
                ImportTextScannerUiState.RequestingPermission -> ScannerMessagePane(
                    title = strings.importer.scannerPermissionTitle,
                    message = strings.importer.scannerPermissionDescription,
                    primaryAction = strings.importer.actionGrantCameraAccess to {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    secondaryAction = strings.importer.actionReturnToImport to onDismiss,
                )

                ImportTextScannerUiState.PermissionDenied -> ScannerMessagePane(
                    title = strings.importer.scannerPermissionDeniedTitle,
                    message = strings.importer.scannerPermissionDeniedDescription,
                    primaryAction = strings.importer.actionGrantCameraAccess to {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    secondaryAction = strings.importer.actionReturnToImport to onDismiss,
                )

                ImportTextScannerUiState.StartingCamera -> SupplierTextScannerOverlay(
                    headline = strings.importer.supplierScannerStarting,
                    supporting = strings.importer.supplierScannerHint,
                    engineLabel = strings.importer.supplierScannerEngine(ocrEngine.engineLabel),
                    showProgress = true,
                )

                ImportTextScannerUiState.Aligning -> SupplierTextScannerOverlay(
                    headline = strings.importer.supplierScannerHint,
                    supporting = strings.importer.supplierScannerCaptureHint,
                    engineLabel = strings.importer.supplierScannerEngine(ocrEngine.engineLabel),
                    showProgress = false,
                    primaryAction = strings.importer.actionCaptureText to ::startRecognition,
                )

                ImportTextScannerUiState.Recognizing -> SupplierTextScannerOverlay(
                    headline = strings.importer.supplierScannerRecognizing,
                    supporting = strings.importer.supplierScannerCaptureHint,
                    engineLabel = strings.importer.supplierScannerEngine(ocrEngine.engineLabel),
                    showProgress = true,
                )

                ImportTextScannerUiState.Failed -> ScannerMessagePane(
                    title = strings.importer.supplierScannerFailedTitle,
                    message = scannerError ?: strings.importer.supplierScanFailedDescription,
                    primaryAction = strings.importer.actionRetryScan to {
                        if (cameraPermissionGranted.value) {
                            restartScanner()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    secondaryAction = strings.importer.actionReturnToImport to onDismiss,
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
private fun SupplierTextScannerOverlay(
    headline: String,
    supporting: String?,
    engineLabel: String,
    showProgress: Boolean,
    primaryAction: Pair<String, () -> Unit>? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ScannerPreviewOverlay(
            headline = headline,
            supporting = supporting,
            showProgress = showProgress,
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.6f),
        ) {
            Text(
                text = engineLabel,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        if (primaryAction != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 120.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = primaryAction.second,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(primaryAction.first)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportTextCameraPreview(
    onScannerReady: () -> Unit,
    onScannerError: (Throwable) -> Unit,
    onCaptureFrameReady: ((() -> Bitmap?) -> Unit),
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

    DisposableEffect(context, lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                )
                onCaptureFrameReady {
                    val previewBitmap = previewView.bitmap ?: return@onCaptureFrameReady null
                    previewBitmap.copy(
                        previewBitmap.config ?: Bitmap.Config.ARGB_8888,
                        false,
                    )
                }
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
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
