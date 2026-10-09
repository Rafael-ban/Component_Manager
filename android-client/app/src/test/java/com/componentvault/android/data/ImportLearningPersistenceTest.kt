package com.componentvault.android.data

import android.app.Application
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportFieldOrigin
import com.componentvault.android.model.StorageLocationSaveIntent
import com.componentvault.android.model.SyncConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImportLearningPersistenceTest {
    @Test
    fun confirmedModelNameAndCategorySurviveRepositoryRecreation() = runBlocking {
        val context: Application = RuntimeEnvironment.getApplication()
        val helper = InventoryDatabaseHelper(context)
        helper.close()
        context.deleteDatabase(helper.databaseName)
        val repository = InventoryRepository(context)
        assertTrue(repository.saveStorageLocation("A", "A", StorageLocationSaveIntent.Create).isSuccess)
        val source = ComponentImportParser.parseScannedQr(
            "{on:SO1,pc:C30926,pm:0603B104K500NT,qty:300}",
        )
        val preferences = AppPreferences(enableLocalAutoRecognition = false, enablePublicJlcLookup = false)
        val saved = repository.saveImportedComponent(
            ComponentDraft(
                sku = "C30926", name = "0603B104K500NT", category = "My capacitors",
                packageName = "0603", location = "A", description = "",
                quantity = 10, minStock = 1,
            ), source, preferences,
        )
        assertTrue(saved.isSuccess, saved.message)
        val resolved = InventoryRepository(context).enrichImportCandidate(
            source, preferences, SyncConfiguration("test", "", "", false, "", ""),
        ).candidate
        assertEquals("0603B104K500NT", resolved.name)
        assertEquals("My capacitors", resolved.category)
        assertEquals("0603", resolved.packageName)
        assertEquals(ComponentImportFieldOrigin.Learned, resolved.fieldOrigins.name)
    }
}
