package com.componentvault.android.data

import android.app.Application
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.StorageLocationSaveIntent
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StorageLocationPersistenceTest {
    private lateinit var repository: InventoryRepository
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        val helper = InventoryDatabaseHelper(context)
        try {
            context.deleteDatabase(helper.databaseName)
        } finally {
            helper.close()
        }
        repository = InventoryRepository(context)
    }

    @Test
    fun createRejectsTrimmedDuplicateWithoutOverwritingActiveLocation() = runBlocking {
        assertTrue(repository.saveStorageLocation("BIN-A", "Original", StorageLocationSaveIntent.Create).isSuccess)
        assertTrue(repository.saveComponent(ComponentDraft(
            sku = "SKU-DUPLICATE",
            name = "Allocated part",
            category = "IC",
            packageName = "SOT-23",
            location = "BIN-A",
            description = "",
            quantity = 5,
            minStock = 0,
        )).isSuccess)
        val locationBefore = location("BIN-A")
        val componentBefore = component("SKU-DUPLICATE")

        val duplicate = repository.saveStorageLocation("  BIN-A  ", "Replacement", StorageLocationSaveIntent.Create)

        assertFalse(duplicate.isSuccess)
        assertEquals("库位编号已存在。", duplicate.message)
        assertEquals(locationBefore, location("BIN-A"))
        assertEquals(componentBefore, component("SKU-DUPLICATE"))
        assertEquals(5, scalar("SELECT quantity FROM component_allocations WHERE location_id = 'BIN-A'"))
    }

    @Test
    fun createRejectsDeletedDuplicateWithoutRevivingOrOverwritingIt() = runBlocking {
        assertTrue(repository.saveStorageLocation("BIN-D", "Deleted name", StorageLocationSaveIntent.Create).isSuccess)
        assertTrue(repository.deleteStorageLocation("BIN-D").isSuccess)
        val deletedBefore = location("BIN-D")

        val duplicate = repository.saveStorageLocation(" BIN-D ", "Revived name", StorageLocationSaveIntent.Create)

        assertFalse(duplicate.isSuccess)
        assertEquals("库位编号已存在。", duplicate.message)
        assertEquals(deletedBefore, location("BIN-D"))
        assertEquals(true, deletedBefore?.deleted)
    }

    @Test
    fun editRequiresActiveLocationAndPreservesItsAllocation() = runBlocking {
        assertTrue(repository.saveStorageLocation("BIN-A", "Old name", StorageLocationSaveIntent.Create).isSuccess)
        assertTrue(repository.saveComponent(ComponentDraft(
            sku = "SKU-1",
            name = "Part",
            category = "IC",
            packageName = "SOT-23",
            location = "BIN-A",
            description = "",
            quantity = 7,
            minStock = 0,
        )).isSuccess)

        assertTrue(repository.saveStorageLocation(" BIN-A ", "New name", StorageLocationSaveIntent.Edit).isSuccess)

        assertEquals("New name", location("BIN-A")?.name)
        assertEquals(false, location("BIN-A")?.deleted)
        assertEquals(7, scalar("SELECT quantity FROM component_allocations WHERE location_id = 'BIN-A'"))
        assertEquals(1, scalar("SELECT COUNT(*) FROM component_allocations WHERE location_id = 'BIN-A'"))

        assertFalse(repository.saveStorageLocation("MISSING", "No row", StorageLocationSaveIntent.Edit).isSuccess)
        assertEquals(0, scalar("SELECT COUNT(*) FROM storage_locations WHERE id = 'MISSING'"))
    }

    @Test
    fun editRejectsDeletedLocationWithoutRevivingIt() = runBlocking {
        assertTrue(repository.saveStorageLocation("BIN-D", "Before delete", StorageLocationSaveIntent.Create).isSuccess)
        assertTrue(repository.deleteStorageLocation("BIN-D").isSuccess)

        val edit = repository.saveStorageLocation("BIN-D", "After delete", StorageLocationSaveIntent.Edit)

        assertFalse(edit.isSuccess)
        assertEquals("库位不存在或已删除。", edit.message)
        assertEquals("Before delete", location("BIN-D")?.name)
        assertEquals(true, location("BIN-D")?.deleted)
    }

    private fun location(id: String): LocationRow? {
        val helper = InventoryDatabaseHelper(context)
        try {
            return helper.readableDatabase.rawQuery(
                "SELECT name, deleted, updated_at FROM storage_locations WHERE id = ?",
                arrayOf(id),
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else LocationRow(
                    cursor.getString(0),
                    cursor.getInt(1) != 0,
                    cursor.getString(2),
                )
            }
        } finally {
            helper.close()
        }
    }

    private fun scalar(sql: String): Int {
        val helper = InventoryDatabaseHelper(context)
        try {
            return helper.readableDatabase.rawQuery(sql, null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        } finally {
            helper.close()
        }
    }

    private fun component(sku: String): ComponentRow? {
        val helper = InventoryDatabaseHelper(context)
        try {
            return helper.readableDatabase.rawQuery(
                "SELECT quantity, updated_at FROM components WHERE sku = ?",
                arrayOf(sku),
            ).use { cursor ->
                if (!cursor.moveToFirst()) null else ComponentRow(cursor.getInt(0), cursor.getString(1))
            }
        } finally {
            helper.close()
        }
    }

    private data class LocationRow(val name: String, val deleted: Boolean, val updatedAt: String)
    private data class ComponentRow(val quantity: Int, val updatedAt: String)
}
