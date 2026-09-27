package com.componentvault.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight

internal enum class LabelToolTemplate { Component, Text, Qr, Free }

@Composable
internal fun LabelToolTemplatePicker(
    onSelect: (LabelToolTemplate) -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPageScaffold(title = stringResource(R.string.label_tool_title), onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabelToolTemplate.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { template ->
                        val title = when (template) {
                            LabelToolTemplate.Component -> R.string.label_tool_component
                            LabelToolTemplate.Text -> R.string.label_tool_text
                            LabelToolTemplate.Qr -> R.string.label_tool_qr
                            LabelToolTemplate.Free -> R.string.label_tool_free
                        }
                        val description = when (template) {
                            LabelToolTemplate.Component -> R.string.label_ui_component_desc
                            LabelToolTemplate.Text -> R.string.label_ui_text_desc
                            LabelToolTemplate.Qr -> R.string.label_ui_qr_desc
                            LabelToolTemplate.Free -> R.string.label_ui_free_desc
                        }
                        Card(Modifier.weight(1f).clickable { onSelect(template) }) {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(92.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) {
                                    Box(Modifier.fillMaxWidth(0.78f).height(64.dp)
                                        .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center) {
                                        Text(when (template) {
                                            LabelToolTemplate.Component -> "R  0603   ▦"
                                            LabelToolTemplate.Text -> "Aa  123"
                                            LabelToolTemplate.Qr -> "▦"
                                            LabelToolTemplate.Free -> "Aa     ▦"
                                        }, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(stringResource(description), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
