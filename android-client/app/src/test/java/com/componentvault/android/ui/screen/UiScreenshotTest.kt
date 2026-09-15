package com.componentvault.android.ui.screen

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.componentvault.android.model.StorageLocationRecord
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    application = Application::class,
    qualifiers = "zh-rCN-w360dp-h640dp-xhdpi",
)
class UiScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun captureImportWorkflowChooserLight() {
        compose.setContent {
            MaterialTheme {
                AddComponentEntrySheet({}, {}, {}, {}, {}, {})
            }
        }

        saveScreenshot("import-workflow-chooser-light.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureBomTaskChooserLight() {
        compose.setContent {
            MaterialTheme {
                BomImportScreen(InventoryViewModel(context), {})
            }
        }

        saveScreenshot("bom-task-chooser-light.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureBomFileStageLight() {
        compose.setContent {
            MaterialTheme {
                BomImportScreen(InventoryViewModel(context), {}, BomImportMode.Bom)
            }
        }

        saveScreenshot("bom-file-stage-light.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureMigrationFileStageLight() {
        compose.setContent {
            MaterialTheme {
                BomImportScreen(InventoryViewModel(context), {}, BomImportMode.Migration)
            }
        }

        saveScreenshot("migration-file-stage-light.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureStorageLocationListLight() {
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(sampleLocations, {}, { _, _, _ -> }, { _, _ -> })
            }
        }

        saveScreenshot("storage-locations-list-light.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureStorageLocationCreateDialogLight() {
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(sampleLocations, {}, { _, _, _ -> }, { _, _ -> })
            }
        }
        compose.onNodeWithTag("locations_add").performClick()

        saveScreenshot("storage-locations-create-dialog-light.png") {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureStorageLocationEditDialogLight() {
        compose.setContent {
            MaterialTheme {
                StorageLocationsScreen(sampleLocations, {}, { _, _, _ -> }, { _, _ -> })
            }
        }
        compose.onNodeWithTag("locations_edit_BIN-A01").performClick()

        saveScreenshot("storage-locations-edit-dialog-light.png") {
            compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        }
    }

    @Test
    fun captureStorageLocationListDark() {
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                StorageLocationsScreen(sampleLocations, {}, { _, _, _ -> }, { _, _ -> })
            }
        }

        saveScreenshot("storage-locations-list-dark.png") {
            compose.onRoot().captureToImage().asAndroidBitmap()
        }
    }

    private fun saveScreenshot(fileName: String, capture: () -> Bitmap) {
        val output = File(screenshotDirectory(), fileName)
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { stream ->
            assertTrue(capture().compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue(output.isFile && output.length() > 0, "Screenshot was not written: ${output.absolutePath}")
    }

    private fun screenshotDirectory(): File {
        val moduleDirectory = listOf(File("."), File("app"), File("android-client/app"))
            .firstOrNull { File(it, "build.gradle.kts").isFile }
            ?: error("Cannot locate the Android app module from ${File(".").absolutePath}")
        return File(moduleDirectory, "build/reports/ui-screenshots")
    }

    private companion object {
        val sampleLocations = listOf(
            StorageLocationRecord("BIN-A01", "常用元件抽屉", "2026-09-16T08:00:00Z"),
            StorageLocationRecord("BIN-A02", "贴片电阻与电容", "2026-09-16T08:00:00Z"),
            StorageLocationRecord("SHELF-B", "开发板与工具", "2026-09-16T08:00:00Z"),
            StorageLocationRecord("CABINET-C", "连接器与线材", "2026-09-16T08:00:00Z"),
            StorageLocationRecord("ARCHIVE-D", "低频使用库存", "2026-09-16T08:00:00Z"),
        )
    }
}
