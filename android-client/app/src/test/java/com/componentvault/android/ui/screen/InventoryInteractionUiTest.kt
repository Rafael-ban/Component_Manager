package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.componentvault.android.R
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.InventoryFiltersUiState
import com.componentvault.android.model.InventoryListItemUiState
import com.componentvault.android.model.InventoryListUiState
import com.componentvault.android.model.InventoryScreenUiState
import com.componentvault.android.model.StorageLocationRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InventoryInteractionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private val item = InventoryListItemUiState(
        id = "part-1",
        name = "Ceramic capacitor",
        sku = "C-100",
        category = "Capacitor",
        packageName = "0603",
        location = "A",
        quantity = 10,
        minStock = 1,
        isLowStock = false,
        updatedAt = "2026-09-27T00:00:00Z",
    )

    @Test
    fun searchStaysInlineAndResultsRemainVisibleWhileTyping() {
        val query = mutableStateOf("")
        compose.setContent {
            MaterialTheme {
                InventoryContent(
                    contentPadding = PaddingValues(),
                    uiState = InventoryScreenUiState(
                        filters = InventoryFiltersUiState(query = query.value),
                        list = InventoryListUiState(items = listOf(item)),
                    ),
                    statusMessage = "",
                    layoutMode = compactMode,
                    onQueryChange = { query.value = it },
                    onStockFilterChange = {},
                    onCategoryChange = {},
                    onLocationChange = {},
                    onSortChange = {},
                    onSelectComponent = {},
                    onOpenComponentDetail = {},
                    onImportComponent = {},
                    onGenerateLabel = {},
                    onEditComponent = {},
                    onRequestDeleteComponent = {},
                    onRecordMovement = {},
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Ceramic")
        compose.onNodeWithText(item.name).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.inventory_ui_clear_search)).performClick()
        compose.runOnIdle { assertEquals("", query.value) }
    }

    @Test
    fun selectionShowsTransferOnlyAfterAnItemIsSelected() {
        compose.setContent {
            MaterialTheme {
                InventoryContent(
                    contentPadding = PaddingValues(),
                    uiState = InventoryScreenUiState(list = InventoryListUiState(items = listOf(item))),
                    statusMessage = "",
                    layoutMode = compactMode,
                    onQueryChange = {},
                    onStockFilterChange = {},
                    onCategoryChange = {},
                    onLocationChange = {},
                    onSortChange = {},
                    onSelectComponent = {},
                    onOpenComponentDetail = {},
                    onImportComponent = {},
                    onGenerateLabel = {},
                    onEditComponent = {},
                    onRequestDeleteComponent = {},
                    onRecordMovement = {},
                    onBatchTransfer = { _, _, _, _ -> },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.batch_transfer_select)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.inventory_ui_select)).performClick()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_select)).assertExists()
        compose.onNodeWithText(item.name).performClick()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_selected_count, 1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_select)).performClick()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_title)).assertIsDisplayed()
    }

    @Test
    fun transferReviewKeepsConfirmVisibleAndSubmitsReviewedLines() {
        var submitted = 0
        val locations = listOf(
            StorageLocationRecord("A", "Shelf A", ""),
            StorageLocationRecord("B", "Shelf B", ""),
        )
        compose.setContent {
            MaterialTheme {
                BatchTransferDialog(
                    items = listOf(item),
                    allocations = listOf(ComponentAllocationRecord(item.id, "A", "Shelf A", 10)),
                    locations = locations,
                    onDismiss = {},
                    onSubmit = { source, destination, lines, _ ->
                        assertEquals("A", source)
                        assertEquals("B", destination)
                        assertEquals(10, lines.single().quantity)
                        submitted++
                    },
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.batch_transfer_review)).performClick()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_submit)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, submitted) }
    }

    private val compactMode = InventoryLayoutMode(
        widthClass = InventoryWidthClass.Compact,
        supportsListDetail = false,
        formPresentation = InventoryFormPresentation.FullScreenRoute,
        secondaryPanePresentation = InventorySecondaryPanePresentation.FullScreenRoute,
    )
}
