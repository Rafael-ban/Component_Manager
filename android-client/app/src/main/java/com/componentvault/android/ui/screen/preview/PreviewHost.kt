package com.componentvault.android.ui.screen.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.componentvault.android.ui.screen.ComponentVaultStrings
import com.componentvault.android.ui.theme.ComponentVaultTheme
import com.componentvault.android.ui.screen.ProvideComponentVaultStrings

@Composable
internal fun PreviewHost(
    strings: ComponentVaultStrings = PreviewComponentVaultStrings.Default,
    content: @Composable () -> Unit,
) {
    ComponentVaultTheme {
        ProvideComponentVaultStrings(strings) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
