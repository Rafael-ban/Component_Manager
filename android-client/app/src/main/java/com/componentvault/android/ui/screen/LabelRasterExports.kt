package com.componentvault.android.ui.screen

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.M1TestPaperProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.roundToInt

private data class LabelExportSnapshot(
    val bitmap: Bitmap,
    val paper: M1TestPaperProfile,
    val copies: Int,
    val sku: String,
)

/** Exports the exact rendered M1 canvas shown in the editor. */
@Composable
internal fun LabelRasterExportActions(
    bitmap: Bitmap?,
    paper: M1TestPaperProfile?,
    copies: Int,
    sku: String,
    enabled: Boolean,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val strings = vaultStrings().importer
    val latestFeedback by rememberUpdatedState(onFeedback)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<LabelExportSnapshot?>(null) }
    var writing by remember { mutableStateOf(false) }

    // The picker may be canceled or the editor may disappear while it is open.
    DisposableEffect(Unit) {
        onDispose {
            pending?.bitmap?.recycle()
            pending = null
        }
    }

    fun export(uri: android.net.Uri?, pdf: Boolean) {
        val snapshot = pending ?: return
        pending = null
        if (uri == null) {
            snapshot.bitmap.recycle()
            return
        }
        writing = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("Unable to open the selected file")
                    output.use { stream ->
                        if (pdf) writeLabelPdf(stream, snapshot) else {
                            check(snapshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                "PNG encoding failed"
                            }
                        }
                    }
                }
                latestFeedback(if (pdf) strings.exportPdfSuccess else strings.exportPngSuccess)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                latestFeedback(strings.exportFailed(error.message ?: strings.exportGenericError))
            } finally {
                snapshot.bitmap.recycle()
                writing = false
            }
        }
    }

    val pngPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) {
        export(it, pdf = false)
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        export(it, pdf = true)
    }
    val canExport = enabled && bitmap != null && !bitmap.isRecycled && paper != null &&
        pending == null && !writing

    fun launch(pdf: Boolean) {
        val source = bitmap ?: return
        val currentPaper = paper ?: return
        if (!canExport) return
        try {
            val ownedBitmap = source.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Unable to copy label image")
            val snapshot = LabelExportSnapshot(ownedBitmap, currentPaper, copies.coerceIn(1, 500), sku)
            pending = snapshot
            val safeName = snapshot.sku.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "label" }
            try {
                if (pdf) pdfPicker.launch("${safeName}_label.pdf")
                else pngPicker.launch("${safeName}_label.png")
            } catch (error: Exception) {
                pending = null
                ownedBitmap.recycle()
                throw error
            }
        } catch (error: Exception) {
            latestFeedback(strings.exportFailed(error.message ?: strings.exportGenericError))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { launch(pdf = false) }, enabled = canExport) {
            Text(strings.actionExportPng)
        }
        OutlinedButton(onClick = { launch(pdf = true) }, enabled = canExport) {
            Text(strings.actionExportPdf)
        }
    }
}

private fun writeLabelPdf(output: OutputStream, snapshot: LabelExportSnapshot) {
    val pageWidth = (snapshot.paper.widthMm * 72f / 25.4f).roundToInt().coerceAtLeast(1)
    val pageHeight = (snapshot.paper.heightMm * 72f / 25.4f).roundToInt().coerceAtLeast(1)
    val document = PdfDocument()
    try {
        repeat(snapshot.copies) { index ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create(),
            )
            page.canvas.drawBitmap(
                snapshot.bitmap,
                null,
                Rect(0, 0, pageWidth, pageHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            document.finishPage(page)
        }
        document.writeTo(output)
    } finally {
        document.close()
    }
}
