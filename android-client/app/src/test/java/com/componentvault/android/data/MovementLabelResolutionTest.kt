package com.componentvault.android.data

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.MovementBatchStage
import com.componentvault.android.model.MovementQuickAction
import com.componentvault.android.model.MovementScanMatchStatus
import com.componentvault.android.model.StorageLocationSaveIntent
import com.componentvault.android.ui.screen.InventoryViewModel
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
class MovementLabelResolutionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun createInventory(): InventoryRepository {
        val helper = InventoryDatabaseHelper(context)
        try {
            context.deleteDatabase(helper.databaseName)
        } finally {
            helper.close()
        }
        return InventoryRepository(context).also { repository ->
            runBlocking {
                assertTrue(repository.saveStorageLocation("A", "A", StorageLocationSaveIntent.Create).isSuccess)
                assertTrue(repository.saveComponent(ComponentDraft(
                    sku = "C30926", name = "Local capacitor", category = "Capacitor",
                    packageName = "0603", location = "A", description = "",
                    quantity = 10, minStock = 1,
                )).isSuccess)
            }
        }
    }

    @Test
    fun originalJlcPackageResolvesBySkuAndQueuesOneForReview() {
        createInventory()
        val packageQr = "{on:SO25020715054,pc:c30926,pm:0603B104K500NT,qty:300,mc:null,cc:1,pdi:144390018,hp:11}"
        assertEquals(
            BarcodeSelection.Single(packageQr),
            ScannedBarcodeSelector.importCandidates(listOf(packageQr)),
        )

        val model = InventoryViewModel(context)
        compose.setContent { MaterialTheme { Text("Scanner host") } }
        compose.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            model.uiState.availableComponents.any { it.sku == "C30926" } || model.startupFailure != null
        }
        assertNull(model.startupFailure)
        compose.runOnIdle {
            model.openMovementScanner()
            model.resolveMovementComponentFromLabel(packageQr)
        }
        compose.waitUntil(10_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            model.uiState.movements.batchSession.totalScans == 1 || model.startupFailure != null
        }
        assertNull(model.startupFailure)
        compose.runOnIdle {
            val movements = model.uiState.movements
            assertFalse(movements.scan.isScannerVisible)
            assertEquals(MovementBatchStage.Review, movements.batchSession.stage)
            val queued = movements.batchSession.queuedItems.single()
            assertEquals("C30926", queued.componentSku)
            assertEquals("1", queued.quantityText)
            assertEquals(10, queued.currentStock)
            model.updateMovementBatchItemMovementType(queued.componentId, MovementQuickAction.Outbound.movementType)
            model.updateMovementBatchItemQuantity(queued.componentId, "2")
            assertEquals(MovementQuickAction.Outbound.movementType, model.uiState.movements.batchSession.queuedItems.single().movementType)
            assertEquals("2", model.uiState.movements.batchSession.queuedItems.single().quantityText)
        }
    }

    @Test
    fun warehouseLabelStillResolvesBeforeJlcFallback() = runBlocking {
        val repository = createInventory()
        val result = repository.resolveComponentByScannedLabel(
            "cvl2|C30926|Local%20capacitor|Capacitor|0603|||10",
        )
        assertEquals(MovementScanMatchStatus.Matched, result.matchStatus)
        assertEquals("C30926", result.matchedComponent?.sku)
    }

    @Test
    fun unknownSkuDoesNotMatchAComponentByModel() = runBlocking {
        val repository = createInventory()
        val result = repository.resolveComponentByScannedLabel(
            "{on:SO25020715054,pc:C99999,pm:0603B104K500NT,qty:300,mc:null,cc:1}",
        )
        assertEquals(MovementScanMatchStatus.NotFound, result.matchStatus)
        assertEquals("C99999", result.parsedSku)
    }

    @Test
    fun arbitraryTextAndInvalidPackageSkuAreRejected() = runBlocking {
        val repository = createInventory()
        for (raw in listOf(
            "unrelated QR content",
            "{on:SO25020715054,pc:NOT-A-SKU,pm:0603B104K500NT,qty:300}",
        )) {
            assertEquals(
                MovementScanMatchStatus.InvalidLabel,
                repository.resolveComponentByScannedLabel(raw).matchStatus,
            )
        }
    }
}