package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.componentvault.android.R
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.InventoryDetailUiState
import com.componentvault.android.model.InventoryFiltersUiState
import com.componentvault.android.model.InventoryListItemUiState
import com.componentvault.android.model.InventoryListUiState
import com.componentvault.android.model.InventoryScreenUiState
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StorageLocationRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InventoryInteractionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun setInventoryContent(content: @Composable () -> Unit) {
        compose.setContent {
            ProvideComponentVaultStrings(runtimeComponentVaultStrings()) { content() }
        }
    }

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
    fun detailShowsStoredCapacitanceAndVoltageInBasicInformation() {
        val component = ComponentRecord(id = item.id, sku = item.sku, name = "Maker C0603",
            category = "电容", packageName = "0603", location = "A",
            description = "参数：容量：100nF\n参数：耐压：50V", quantity = 10,
            minStock = 1, updatedAt = item.updatedAt, deleted = false)
        setInventoryContent {
            MaterialTheme {
                InventoryDetailPane(InventoryDetailUiState(component = component),
                    onEditComponent = {}, onGenerateLabel = {}, onRequestDeleteComponent = {},
                    onRecordMovement = {})
            }
        }
        val capacitanceLabel = context.getString(R.string.inventory_param_capacitance)
        compose.onNodeWithTag("inventory_detail_list").performScrollToNode(hasText(capacitanceLabel))
        compose.onNodeWithText(capacitanceLabel)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("100nF").performScrollTo().assertIsDisplayed()
        val voltageLabel = context.getString(R.string.inventory_param_voltage)
        compose.onNodeWithTag("inventory_detail_list").performScrollToNode(hasText(voltageLabel))
        compose.onNodeWithText(voltageLabel)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("50V").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun swipeRevealsFourBoundedActionsAndTapOrRightSwipeCloses() {
        var opens = 0
        var inbound = 0
        var outbound = 0
        var transfers = 0
        var deletes = 0
        setInventoryContent {
            MaterialTheme {
                InventoryListRow(item, selected = false, onClick = { opens++ },
                    onInbound = { inbound++ }, onOutbound = { outbound++ },
                    onTransfer = { transfers++ }, onDelete = { deletes++ })
            }
        }
        val card = compose.onNodeWithTag("inventory_reveal_card")
        compose.onNodeWithTag("inventory_quick_actions").assertDoesNotExist()
        val closedLeft = card.getUnclippedBoundsInRoot().left
        card.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val openLeft = card.getUnclippedBoundsInRoot().left
        val actionBounds = compose.onNodeWithTag("inventory_quick_actions").getUnclippedBoundsInRoot()
        val actionWidth = actionBounds.right - actionBounds.left
        assertTrue(abs(((closedLeft - openLeft) - actionWidth).value) < 2f)
        card.assertIsDisplayed()
        val cardBounds = card.getUnclippedBoundsInRoot()
        assertTrue((cardBounds.right - cardBounds.left - actionWidth).value >= 48f)
        listOf("inventory_quick_inbound", "inventory_quick_outbound",
            "inventory_quick_transfer", "inventory_quick_delete").forEach {
            val button = compose.onNodeWithTag(it).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue((button.right - button.left).value >= 48f)
        }
        compose.runOnIdle { assertEquals(listOf(0, 0, 0, 0), listOf(inbound, outbound, transfers, deletes)) }

        card.performClick()
        compose.waitForIdle()
        assertTrue(abs((card.getUnclippedBoundsInRoot().left - closedLeft).value) < 2f)
        compose.onNodeWithTag("inventory_quick_actions").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, opens) }

        card.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        card.performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(abs((card.getUnclippedBoundsInRoot().left - closedLeft).value) < 2f)
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("inventory_quick_inbound").performClick()
        compose.runOnIdle {
            assertEquals(1, inbound)
            assertEquals(listOf(0, 0, 0), listOf(outbound, transfers, deletes))
            assertEquals(0, opens)
        }
    }

    @Test
    fun selectionModeHasNoSwipeActions() {
        setInventoryContent {
            MaterialTheme {
                InventoryListRow(item, selected = true, selectionMode = true,
                    onClick = {}, onInbound = {}, onOutbound = {}, onTransfer = {}, onDelete = {})
            }
        }
        compose.onNodeWithTag("inventory_quick_actions").assertDoesNotExist()
        compose.onNodeWithText(item.name).assertIsDisplayed()
    }

    @Test
    fun quickActionsOpenFormsDirectlyAndDeleteUsesExistingCallback() {
        var deletedId: String? = null
        setInventoryContent {
            MaterialTheme {
                InventoryContent(
                    contentPadding = PaddingValues(),
                    uiState = InventoryScreenUiState(
                        list = InventoryListUiState(items = listOf(item)),
                        storageLocations = listOf(StorageLocationRecord("A", "Shelf A", ""),
                            StorageLocationRecord("B", "Shelf B", "")),
                        allocations = listOf(ComponentAllocationRecord(item.id, "A", "Shelf A", 10)),
                    ),
                    statusMessage = "", layoutMode = compactMode,
                    onQueryChange = {}, onStockFilterChange = {}, onCategoryChange = {},
                    onLocationChange = {}, onSortChange = {}, onSelectComponent = {},
                    onOpenComponentDetail = {}, onImportComponent = {}, onGenerateLabel = {},
                    onEditComponent = {}, onRequestDeleteComponent = { deletedId = it },
                    onRecordMovement = {},
                    onQuickMovement = { _, _ -> },
                    onBatchTransfer = { _, _, _, _ -> },
                )
            }
        }
        val card = compose.onNodeWithTag("inventory_reveal_card")
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("inventory_quick_inbound").performClick()
        compose.onNodeWithText(context.getString(R.string.inventory_quick_inbound) + " · " + item.sku)
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        card.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("inventory_quick_outbound").performClick()
        compose.onNodeWithText(context.getString(R.string.inventory_quick_outbound) + " · " + item.sku)
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        card.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("inventory_quick_transfer").performClick()
        compose.onNodeWithText(context.getString(R.string.inventory_quick_transfer)).assertIsDisplayed()
        compose.onNodeWithText(item.sku).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.batch_transfer_count, 1)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        card.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("inventory_quick_delete").performClick()
        compose.runOnIdle { assertEquals(item.id, deletedId) }
    }
    @Test
    fun quickStockFormKeepsInputOnFailureAndBlocksDuplicateSubmit() {
        var draft: MovementEntryDraft? = null
        var completion: ((OperationResult) -> Unit)? = null
        var dismissals = 0
        setInventoryContent {
            MaterialTheme {
                QuickStockActionDialog(item, inbound = true, allocations = emptyList(),
                    locations = listOf(StorageLocationRecord("A", "Shelf A", "")),
                    onDismiss = { dismissals++ }, onSubmit = { submitted, callback ->
                        draft = submitted; completion = callback
                    })
            }
        }
        compose.onNodeWithTag("quick_quantity").performTextReplacement("3")
        compose.onNodeWithTag("quick_submit").performClick()
        compose.runOnIdle {
            assertEquals("inbound", draft?.movementType)
            assertEquals(item.id, draft?.componentId)
            assertEquals("A", draft?.locationId)
            assertEquals(3, draft?.quantity)
            assertEquals(0, dismissals)
        }
        compose.onNodeWithTag("quick_submit").assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).assertIsNotEnabled()
        compose.runOnIdle { completion?.invoke(OperationResult(false, "temporary failure")) }
        compose.onNodeWithText("temporary failure").assertIsDisplayed()
        assertEquals("3", compose.onNodeWithTag("quick_quantity")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("quick_submit").performClick()
        compose.runOnIdle { completion?.invoke(OperationResult(true, "saved")) }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun quickOutboundRejectsMoreThanSourceAllocation() {
        var submitted = false
        setInventoryContent {
            MaterialTheme {
                QuickStockActionDialog(item, inbound = false,
                    allocations = listOf(ComponentAllocationRecord(item.id, "A", "Shelf A", 2)),
                    locations = listOf(StorageLocationRecord("A", "Shelf A", "")),
                    onDismiss = {}, onSubmit = { _, _ -> submitted = true })
            }
        }
        compose.onNodeWithTag("quick_quantity").performTextReplacement("3")
        compose.onNodeWithText(context.getString(R.string.inventory_quick_exceeds_stock)).assertIsDisplayed()
        compose.onNodeWithTag("quick_submit").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(false, submitted) }
    }

    @Test
    fun searchStaysInlineAndResultsRemainVisibleWhileTyping() {
        val query = mutableStateOf("")
        setInventoryContent {
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
        setInventoryContent {
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
        setInventoryContent {
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
