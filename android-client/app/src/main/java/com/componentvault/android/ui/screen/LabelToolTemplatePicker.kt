package com.componentvault.android.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.R
import androidx.compose.ui.res.stringResource

internal enum class LabelToolTemplate { Component, Text, Qr, Free }

@Composable
internal fun LabelToolTemplatePicker(
    onSelect: (LabelToolTemplate) -> Unit,
    onBack: () -> Unit,
) {
    SecondaryPageScaffold(title = stringResource(R.string.label_tool_title), onBack = onBack) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabelToolTemplate.entries.forEach { template ->
                val title = when (template) {
                    LabelToolTemplate.Component -> R.string.label_tool_component
                    LabelToolTemplate.Text -> R.string.label_tool_text
                    LabelToolTemplate.Qr -> R.string.label_tool_qr
                    LabelToolTemplate.Free -> R.string.label_tool_free
                }
                Card(Modifier.fillMaxWidth().clickable { onSelect(template) }) {
                    Text(stringResource(title), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
