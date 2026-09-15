package com.componentvault.android.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.componentvault.android.BuildConfig
import com.componentvault.android.R

internal fun startupFailureDetails(error: Throwable): String = buildString {
    appendLine("Component Vault ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    var cause: Throwable? = error
    repeat(5) {
        val current = cause ?: return@repeat
        appendLine(current.javaClass.name)
        current.stackTrace.filter { frame ->
            frame.className.startsWith("com.componentvault.") ||
                frame.className.startsWith("android.database.")
        }.take(8).forEach { frame ->
            appendLine("  ${frame.className}.${frame.methodName}:${frame.lineNumber}")
        }
        cause = current.cause?.takeUnless { it === current }
    }
}

@Composable
internal fun StartupFailureScreen(details: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.startup_failure_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.startup_failure_data_preserved))
            Text(details, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text(stringResource(R.string.startup_failure_retry)) }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Component Vault startup diagnostics", details))
            }) { Text(stringResource(R.string.startup_failure_copy)) }
        }
    }
}
