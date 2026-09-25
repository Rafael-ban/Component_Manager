package com.componentvault.android.ui.screen

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.componentvault.android.data.InventoryDatabaseHelper
import com.componentvault.android.data.InventoryRepository
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.MovementBatchQueueItemUiState
import com.componentvault.android.model.MovementBatchSessionUiState
import com.componentvault.android.model.MovementBatchStage
import com.componentvault.android.model.MovementsUiState
import com.componentvault.android.model.StorageLocationSaveIntent
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MovementScanFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun waitForModel(model: InventoryViewModel, condition: () -> Boolean) {
        compose.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition() || model.startupFailure != null
        }
        assertNull(model.startupFailure, "InventoryViewModel failed to load test inventory")
    }

    @Test
    fun successfulScanClosesCameraAndOnlyExplicitRescanAddsAnotherCount() {
        val helper = InventoryDatabaseHelper(context)
        try { context.deleteDatabase(helper.databaseName) } finally { helper.close() }
        val repository = InventoryRepository(context)
        runBlocking {
            assertTrue(repository.saveStorageLocation("A", "A", StorageLocationSaveIntent.Create).isSuccess)
            assertTrue(repository.saveComponent(ComponentDraft(
                sku = "C70565", name = "Existing part", category = "IC",
                packageName = "SOT-23", location = "A", description = "",
                quantity = 10, minStock = 1,
            )).isSuccess)
        }
        val model = InventoryViewModel(context)
        compose.setContent { MaterialTheme { Text("Scanner host") } }
        waitForModel(model) { model.uiState.availableComponents.any { it.sku == "C70565" } }

        val label = "cvl2|C70565|Existing%20part|IC|SOT-23|||10"
        compose.runOnIdle {
            model.openMovementScanner()
            model.resolveMovementComponentFromLabel(label)
            model.resolveMovementComponentFromLabel(label)
        }
        waitForModel(model) { model.uiState.movements.batchSession.totalScans == 1 }
        compose.runOnIdle {
            assertFalse(model.uiState.movements.scan.isScannerVisible)
            assertEquals(MovementBatchStage.Review, model.uiState.movements.batchSession.stage)
            assertEquals(1, model.uiState.movements.batchSession.queuedItems.single().scanCount)
            model.openMovementScanner()
            model.resolveMovementComponentFromLabel(label)
        }
        waitForModel(model) { model.uiState.movements.batchSession.totalScans == 2 }
        compose.runOnIdle {
            assertFalse(model.uiState.movements.scan.isScannerVisible)
            assertEquals(2, model.uiState.movements.batchSession.queuedItems.single().scanCount)
        }
    }

    @Test
    fun reviewRouteBackKeepsDraftAndCanBeResumed() {
        val showingReview = mutableStateOf(true)
        val session = MovementBatchSessionUiState(
            stage = MovementBatchStage.Review,
            queuedItems = listOf(MovementBatchQueueItemUiState(
                componentId = "part-1", componentName = "Existing part", componentSku = "C70565",
                category = "IC", packageName = "SOT-23", location = "A",
                currentStock = 10, minStock = 1,
            )),
            totalScans = 1,
        )
        compose.setContent {
            MaterialTheme {
                ProvideComponentVaultStrings(runtimeComponentVaultStrings()) {
                    if (showingReview.value) {
                        MovementBatchReviewRoute(
                            uiState = MovementsUiState(batchSession = session),
                            onDismiss = { showingReview.value = false },
                            onScanMovementLabel = {},
                            onCommitMovementBatch = {},
                            onDiscardMovementBatch = {},
                            onUpdateMovementBatchItemMovementType = { _, _ -> },
                            onUpdateMovementBatchItemQuantity = { _, _ -> },
                            onUpdateMovementBatchItemReason = { _, _ -> },
                            onUpdateMovementBatchItemNote = { _, _ -> },
                            onRemoveMovementBatchItem = {},
                        )
                    } else {
                        androidx.compose.material3.TextButton(onClick = { showingReview.value = true }) {
                            Text("Resume review")
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Existing part").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Resume review").assertIsDisplayed().performClick()
        compose.onNodeWithText("Existing part").performScrollTo().assertIsDisplayed()
    }
}
