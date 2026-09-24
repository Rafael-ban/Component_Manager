package com.componentvault.android.ui.screen

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.captureToImage
import com.componentvault.android.R
import com.componentvault.android.data.LabelPrintItemState
import com.componentvault.android.data.LabelPrintQueue
import com.componentvault.android.data.LabelPrintQueueStore
import com.componentvault.android.model.ComponentLabelSeed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BluetoothLabelPrintUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun replacingPaperPreviewKeepsTheDisplayedBitmapDrawable() {
        LabelPrintQueueStore(context).clear()
        val seed = ComponentLabelSeed("C393939", "TYPE-C16PIN", "Connector", "SMD", "A", 10, 1)
        compose.setContent {
            MaterialTheme { BluetoothLabelPrintScreen(emptyList(), seed, onDismiss = {}) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("print_label_preview").fetchSemanticsNodes().isNotEmpty() }
        for (height in listOf("61", "60", "62", "60")) {
            compose.onNodeWithText(context.getString(R.string.bluetooth_label_print_height))
                .performScrollTo().performTextReplacement(height)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("print_label_preview").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("print_label_preview").performScrollTo().captureToImage()
        }
        assertFalse(compose.activity.isFinishing)
    }

    @Test
    fun restoredHundredLabelQueueCanReviewLastItemAndReturnToParent() {
        val selection = (1..100).map { index ->
            ComponentLabelSeed(
                sku = "UI-$index",
                name = "元件 $index",
                category = "IC",
                packageName = "SOT-23",
                location = "A",
                quantity = index,
                minStock = 1,
            ) to 1
        }
        val original = LabelPrintQueue.create(selection)
        val last = original.items.last()
        LabelPrintQueueStore(context).save(original.update(last.id, LabelPrintItemState.Uncertain, "print_check_label"))
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) BluetoothLabelPrintScreen(emptyList(), null, onDismiss = { visible.value = false })
                else Text("库存父页面")
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("print_queue_list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("print_queue_list").performScrollTo()
        compose.onNodeWithTag("print_queue_list").performScrollToNode(hasTestTag("print_queue_item_${last.id}"))
        compose.onNodeWithTag("print_queue_item_${last.id}").assertIsDisplayed()
        compose.onNodeWithTag("print_queue_start").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bluetooth_label_print_review)).performClick()
        compose.onNodeWithText(context.getString(R.string.bluetooth_label_print_retry)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bluetooth_label_print_mark_printed)).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(context.getString(R.string.bluetooth_label_print_state_sent), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("库存父页面").assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
        LabelPrintQueueStore(context).clear()
    }
}
