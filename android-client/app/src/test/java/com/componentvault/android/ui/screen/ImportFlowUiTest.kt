package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.componentvault.android.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImportFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun chooserPresentsFourDistinctWorkflowsAndSecondaryManualEntry() {
        var selected = ""
        compose.setContent {
            MaterialTheme {
                AddComponentEntrySheet(
                    onDismiss = {},
                    onImportComponent = { selected = "single" },
                    onAddComponent = { selected = "manual" },
                    onImportBom = { selected = "bom" },
                    onImportMigration = { selected = "migration" },
                    onBatchJlc = { selected = "batch" },
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.import_menu_single)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_menu_batch)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_menu_bom)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.import_menu_migration)).assertIsDisplayed().performClick()
        assertEquals("migration", selected)
        compose.onNodeWithText(context.getString(R.string.import_menu_manual)).assertIsDisplayed()
    }

    @Test
    fun chooserBackReturnsToInventoryHost() {
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) {
                    AddComponentEntrySheet({ visible.value = false }, {}, {}, {}, {}, {})
                } else {
                    androidx.compose.material3.Text("Inventory host")
                }
            }
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Inventory host").assertIsDisplayed()
    }

    @Test
    fun bomWorkflowShowsOnlyFileStageBeforeAFileIsChosen() {
        compose.setContent {
            MaterialTheme {
                BomImportScreen(InventoryViewModel(context), {}, BomImportMode.Bom)
            }
        }

        compose.onNodeWithText(context.getString(R.string.bom_choose_file)).assertIsDisplayed()
        compose.onNodeWithText("项目名").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.bom_generate_preview)).assertDoesNotExist()
    }

    @Test
    fun migrationWorkflowDoesNotExposeBomConfiguration() {
        compose.setContent {
            MaterialTheme {
                BomImportScreen(InventoryViewModel(context), {}, BomImportMode.Migration)
            }
        }

        compose.onNodeWithText(context.getString(R.string.bom_task_migration)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bom_choose_file)).assertIsDisplayed()
        compose.onNodeWithText("生产套数").assertDoesNotExist()
    }
}
