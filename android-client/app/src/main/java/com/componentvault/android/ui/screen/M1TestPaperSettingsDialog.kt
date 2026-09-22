package com.componentvault.android.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import com.componentvault.android.data.M1TestLabelRenderer
import com.componentvault.android.data.M1TestPaperProfile
import androidx.compose.ui.res.stringResource

/** Edits the app-side M1 test raster only; no printer configuration command is sent. */
@Composable
internal fun M1TestPaperSettingsDialog(
    current: M1TestPaperProfile,
    onSave: (M1TestPaperProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var widthText by remember(current) { mutableStateOf(formatNumber(current.widthMm)) }
    var heightText by remember(current) { mutableStateOf(formatNumber(current.heightMm)) }
    var rotation by remember(current) { mutableStateOf(current.rotationDegrees) }
    var offsetXText by remember(current) { mutableStateOf(formatNumber(current.offsetXmm)) }
    var offsetYText by remember(current) { mutableStateOf(formatNumber(current.offsetYmm)) }

    val edited = remember(widthText, heightText, rotation, offsetXText, offsetYText) {
        runCatching {
            M1TestPaperProfile(
                widthMm = widthText.toFloat(),
                heightMm = heightText.toFloat(),
                rotationDegrees = rotation,
                offsetXmm = offsetXText.toFloat(),
                offsetYmm = offsetYText.toFloat(),
            )
        }.getOrNull()
    }
    val previewProfile = edited ?: current
    val preview = remember(previewProfile) { M1TestLabelRenderer.render(previewProfile) }
    DisposableEffect(preview) { onDispose { preview.recycle() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.m1_paper_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.m1_paper_local_only),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            value = widthText,
                            onValueChange = { widthText = it },
                            label = stringResource(R.string.m1_paper_width),
                            supportingText = stringResource(R.string.m1_paper_width_range),
                            modifier = Modifier.weight(1f),
                        )
                        DecimalField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = stringResource(R.string.m1_paper_height),
                            supportingText = stringResource(R.string.m1_paper_height_range),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Text(stringResource(R.string.m1_paper_rotation), style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(M1TestPaperProfile.Rotations.size) { index ->
                            val degrees = M1TestPaperProfile.Rotations.elementAt(index)
                            FilterChip(
                                selected = rotation == degrees,
                                onClick = { rotation = degrees },
                                label = { Text("$degrees°") },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.m1_paper_rotation_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField(
                            value = offsetXText,
                            onValueChange = { offsetXText = it },
                            label = stringResource(R.string.m1_paper_offset_x),
                            supportingText = stringResource(R.string.m1_paper_offset_x_range),
                            signed = true,
                            modifier = Modifier.weight(1f),
                        )
                        DecimalField(
                            value = offsetYText,
                            onValueChange = { offsetYText = it },
                            label = stringResource(R.string.m1_paper_offset_y),
                            supportingText = stringResource(R.string.m1_paper_offset_y_range),
                            signed = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.m1_paper_offset_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    Text(stringResource(R.string.m1_paper_preview), style = MaterialTheme.typography.labelLarge)
                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = stringResource(R.string.m1_paper_preview_description),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(preview.width.toFloat() / preview.height)
                                .heightIn(max = 220.dp),
                        )
                    }
                }
                if (edited == null) {
                    item {
                        Text(
                            stringResource(R.string.m1_paper_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { edited?.let(onSave) }, enabled = edited != null) {
                Text(stringResource(R.string.m1_paper_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.m1_paper_cancel)) } },
    )
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String,
    signed: Boolean = false,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (signed) KeyboardType.Ascii else KeyboardType.Decimal,
        ),
        singleLine = true,
        modifier = modifier,
    )
}

private fun formatNumber(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
