package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.componentvault.android.R
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28],
    application = Application::class,
)
class LocationManagementUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun addFailureStaysInDialogAndCanBeCorrected() {
        val locations = mutableStateOf(emptyList<StorageLocationRecord>())
        var attemptedCode = ""
        var attemptedName = ""
        var attempts = 0
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(
                    locations = locations.value,
                    onDismiss = {},
                    onSave = { code, name, complete ->
                        attemptedCode = code
                        attemptedName = name
                        attempts++
                        if (attempts == 1) complete(OperationResult(false, "Code already exists"))
                        else {
                            locations.value = listOf(StorageLocationRecord(code, name, "now"))
                            complete(OperationResult(true, "Saved", code))
                        }
                    },
                    onDelete = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("locations_add").performClick()
        compose.onNodeWithTag("locations_code").performTextInput(" A-01 ")
        compose.onNodeWithTag("locations_name").performTextInput("Main shelf")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.onNodeWithTag("locations_editor_error").assertTextEquals("Code already exists")
        assertEquals("A-01", attemptedCode)
        assertEquals("Main shelf", attemptedName)

        compose.onNodeWithTag("locations_name").performTextReplacement("Primary shelf")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.onNodeWithTag("locations_operation_message").assertTextEquals("Saved")
        compose.onNodeWithText("Primary shelf").assertIsDisplayed()
    }

    @Test
    fun editKeepsStableCodeAndSavesOnlyTheNewName() {
        val location = StorageLocationRecord("BIN-42", "Old name", "now")
        var savedCode = ""
        var savedName = ""
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(
                    locations = listOf(location),
                    onDismiss = {},
                    onSave = { code, name, complete ->
                        savedCode = code
                        savedName = name
                        complete(OperationResult(true, "Saved", code))
                    },
                    onDelete = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("locations_edit_BIN-42").performClick()
        compose.onNodeWithTag("locations_code").assert(hasText("BIN-42") and !hasSetTextAction())
        compose.onNodeWithTag("locations_name").performTextReplacement("New name")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        assertEquals("BIN-42", savedCode)
        assertEquals("New name", savedName)
    }

    @Test
    fun dialogBackClosesEditorBeforeLeavingPage() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(emptyList(), { dismissed = true }, { _, _, _ -> }, { _, _ -> })
            }
        }
        compose.onNodeWithTag("locations_add").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("locations_add").assertIsDisplayed()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(true, dismissed) }
    }

    @Test
    fun backIsConsumedWhileSaveCallbackIsPending() {
        var dismissed = false
        var completion: ((OperationResult) -> Unit)? = null
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(
                    emptyList(),
                    { dismissed = true },
                    { _, _, complete -> completion = complete },
                    { _, _ -> },
                )
            }
        }
        compose.onNodeWithTag("locations_add").performClick()
        compose.onNodeWithTag("locations_code").performTextInput("A")
        compose.onNodeWithTag("locations_name").performTextInput("Shelf A")
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText(context.getString(R.string.locations_saving)).assertIsDisplayed()
        compose.runOnIdle { completion?.invoke(OperationResult(true, "Saved", "A")) }
    }

    @Test
    fun manyLocationsCanScrollToLastEntryAndEditIt() {
        val locations = (1..100).map { index ->
            StorageLocationRecord("BIN-$index", "Shelf $index", "now")
        }
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(locations, {}, { _, _, _ -> }, { _, _ -> })
            }
        }
        compose.onNodeWithTag("locations_list")
            .performScrollToNode(hasTestTag("locations_edit_BIN-100"))
        compose.onNodeWithTag("locations_edit_BIN-100").assertIsDisplayed().performClick()
        compose.onNodeWithTag("locations_code").assert(hasText("BIN-100") and !hasSetTextAction())
    }
}
