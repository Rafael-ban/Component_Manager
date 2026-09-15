package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.componentvault.android.R
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ImportLearningSummary
import com.componentvault.android.model.SyncConfiguration
import com.componentvault.android.ui.screen.preview.CompactPreviewLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SettingsDraftUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testingEditedUrlDoesNotSaveAndSyncRequiresExplicitSave() {
        val context: Application = RuntimeEnvironment.getApplication()
        var saves = 0
        var tested: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                SettingsContent(
                    contentPadding = PaddingValues(),
                    syncConfiguration = SyncConfiguration("device", "https://saved.example", "saved-token", false, "", ""),
                    appPreferences = AppPreferences(), importLearningSummary = ImportLearningSummary(),
                    isBusy = false, statusMessage = "", layoutMode = CompactPreviewLayout,
                    selectedSection = SettingsSection.Sync, onSelectSection = {},
                    onSaveSyncSettings = { _, _, _ -> saves++ }, onSaveAppPreferences = { saves++ },
                    onTestConnection = { url, token -> tested = url to token }, onSyncNow = {},
                    onClearImportLearningMappings = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.field_server_url)).performTextReplacement("https://draft.example")
        val testLabel = context.getString(R.string.action_test_connection)
        compose.onNodeWithTag("settings_detail_list").performScrollToNode(hasText(testLabel))
        compose.onNodeWithText(testLabel).performClick()
        compose.runOnIdle {
            assertEquals("https://draft.example" to "saved-token", tested)
            assertEquals(0, saves)
        }
        val syncLabel = context.getString(R.string.action_sync_now)
        compose.onNodeWithTag("settings_detail_list").performScrollToNode(hasText(syncLabel))
        compose.onNodeWithText(syncLabel).assertIsNotEnabled()
    }
}
