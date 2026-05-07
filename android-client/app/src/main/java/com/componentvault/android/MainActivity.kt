package com.componentvault.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.componentvault.android.ui.screen.ComponentVaultApp
import com.componentvault.android.ui.screen.InventoryViewModel
import com.componentvault.android.ui.theme.ComponentVaultTheme

class MainActivity : ComponentActivity() {
    private val inventoryViewModel: InventoryViewModel by viewModels {
        InventoryViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ComponentVaultTheme {
                ComponentVaultApp(viewModel = inventoryViewModel)
            }
        }
    }
}
