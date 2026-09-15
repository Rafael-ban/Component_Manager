package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord

@Composable
internal fun StorageLocationsScreen(
    locations: List<StorageLocationRecord>,
    onDismiss: () -> Unit,
    onSave: (String, String, (OperationResult) -> Unit) -> Unit,
    onDelete: (String, (OperationResult) -> Unit) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDismiss) { Text("返回") }
            Text("库位管理")
        }
        OutlinedTextField(code, { code = it.trim() }, Modifier.fillMaxWidth(), label = { Text("稳定库位编码（创建后不可改）") })
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("库位名称") })
        Button(onClick = {
            onSave(code, name) { result ->
                message = result.message
                if (result.isSuccess) { code = ""; name = "" }
            }
        }, enabled = code.isNotBlank() && name.isNotBlank()) { Text("创建空库位 / 保存名称") }
        if (message.isNotBlank()) Text(message)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(locations, key = { it.id }) { location ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${location.name} · ${location.id}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { code = location.id; name = location.name }) { Text("编辑名称") }
                            OutlinedButton(onClick = { onDelete(location.id) { message = it.message } }) { Text("删除空库位") }
                        }
                    }
                }
            }
        }
    }
}
