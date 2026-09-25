package com.componentvault.android.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.componentvault.android.data.LabelDesign
import com.componentvault.android.data.M1TestPaperProfile

/** The raster is exactly what the printer receives. Selection frames live in this UI layer only. */
@Composable
internal fun LabelEditorCanvas(
    bitmap: Bitmap,
    design: LabelDesign?,
    paper: M1TestPaperProfile,
    selectedId: String?,
    editable: Boolean,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val paperWidth = maxWidth
        val paperHeight = paperWidth * paper.heightMm / paper.widthMm
        val pxPerMm = with(density) { paperWidth.toPx() } / paper.widthMm
        val dpPerMm = paperWidth / paper.widthMm
        Box(Modifier.size(paperWidth, paperHeight).clipToBounds().testTag("print_label_preview")) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(paper.widthMm / paper.heightMm),
                contentScale = ContentScale.FillBounds,
            )
            if (editable && design != null) {
                design.elements.forEach { element ->
                    key(element.id) {
                    val currentElement by rememberUpdatedState(element)
                    val currentSelect by rememberUpdatedState(onSelect)
                    val currentMove by rememberUpdatedState(onMove)
                    // Use the exact box for dragging so adjacent rows never steal gestures.
                    // The element chips in the editor provide the larger accessible tap targets.
                    val hitWidth = (element.widthMm * dpPerMm.value).dp
                    val hitHeight = (element.heightMm * dpPerMm.value).dp
                    val visibleX = (element.xMm + paper.offsetXmm) * dpPerMm.value
                    val visibleY = (element.yMm + paper.offsetYmm) * dpPerMm.value
                    var dragX = element.xMm
                    var dragY = element.yMm
                    Box(
                        Modifier
                            .offset(x = visibleX.dp, y = visibleY.dp)
                            .size(hitWidth, hitHeight)
                            .border(if (selectedId == element.id) 2.dp else 1.dp,
                                if (selectedId == element.id) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .background(Color.Transparent)
                            .semantics { contentDescription = element.id }
                            .testTag("label_element_${element.id}")
                            .pointerInput(element.id, editable, pxPerMm) {
                                if (editable) detectDragGestures(
                                    onDragStart = {
                                        currentSelect(element.id)
                                        dragX = currentElement.xMm
                                        dragY = currentElement.yMm
                                    },
                                    onDrag = { change, amount: Offset ->
                                        change.consume()
                                        dragX += amount.x / pxPerMm
                                        dragY += amount.y / pxPerMm
                                        currentMove(element.id, dragX, dragY)
                                    },
                                )
                            }
                            .pointerInput(element.id, editable) {
                                if (editable) androidx.compose.foundation.gestures.detectTapGestures { currentSelect(element.id) }
                            },
                    )
                    }
                }
            }
        }
    }
}
