package com.componentvault.android.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import com.componentvault.android.model.ComponentDraft
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
class BatchJlcDatabaseTest {
    private lateinit var repository: InventoryRepository
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() = runBlocking {
        InventoryDatabaseHelper(context).use { helper -> context.deleteDatabase(helper.databaseName) }
        repository = InventoryRepository(context)
        assertTrue(repository.saveStorageLocation("A", "A").isSuccess)
        assertTrue(repository.saveStorageLocation("B", "B").isSuccess)
        assertTrue(repository.saveComponent(ComponentDraft(
            sku = "C70565", name = "Existing part", category = "IC",
            packageName = "SOT-23", location = "A", description = "Keep existing data",
            quantity = 10, minStock = 1,
        )).isSuccess)
    }

    @Test
    fun mixedInboundUpdatesAllocationsAndReplayDoesNotDuplicate() = runBlocking {
        val rows = listOf(row("one", "C70565", "5", "B"),
            row("two", "C30926", "3", "A"), row("three", "C70565", "2", "A"))
        assertTrue(repository.commitBatchJlc("session", rows).isSuccess)
        assertEquals(17, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(12, number("SELECT quantity FROM component_allocations WHERE component_id=(SELECT id FROM components WHERE sku='C70565') AND location_id='A'"))
        assertEquals(5, number("SELECT quantity FROM component_allocations WHERE component_id=(SELECT id FROM components WHERE sku='C70565') AND location_id='B'"))
        assertEquals(3, number("SELECT quantity FROM components WHERE sku='C30926'"))
        assertEquals(1, number("SELECT COUNT(*) FROM components WHERE sku='C70565' AND name='Existing part' AND description='Keep existing data'"))
        assertEquals(3, number("SELECT COUNT(*) FROM stock_movements WHERE movement_type='inbound'"))
        assertEquals(3, number("SELECT COUNT(*) FROM sync_queue WHERE entity_type='stock_movement'"))
        assertEquals(rows.map { it.id }.toSet(), repository.loadBatchJlcReceipts("session"))

        val reopened = InventoryRepository(context)
        assertTrue(reopened.commitBatchJlc("session", rows).isSuccess)
        assertEquals(17, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(3, number("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(3, number("SELECT COUNT(*) FROM batch_jlc_receipts"))
    }

    @Test
    fun laterOverflowRollsBackNewPartMovementsQueueAndReceipts() = runBlocking {
        val queueBefore = number("SELECT COUNT(*) FROM sync_queue")
        val result = repository.commitBatchJlc("rollback", listOf(
            row("new-first", "C30926", "3", "A"),
            row("overflow-last", "C70565", Int.MAX_VALUE.toString(), "B"),
        ))
        assertFalse(result.isSuccess)
        assertEquals(0, number("SELECT COUNT(*) FROM components WHERE sku='C30926'"))
        assertEquals(10, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(10, number("SELECT SUM(quantity) FROM component_allocations"))
        assertEquals(0, number("SELECT COUNT(*) FROM stock_movements"))
        assertEquals(0, number("SELECT COUNT(*) FROM batch_jlc_receipts"))
        assertEquals(queueBefore, number("SELECT COUNT(*) FROM sync_queue"))
        assertFalse(repository.commitBatchJlc("zero", listOf(row("zero", "C70565", "0", "A"))).isSuccess)
        assertFalse(repository.commitBatchJlc("missing-bin", listOf(row("missing", "C70565", "1", "MISSING"))).isSuccess)
        assertEquals(10, number("SELECT quantity FROM components WHERE sku='C70565'"))
    }

    @Test
    fun singleImportAppendsAndRejectsStaleConfirmationWithoutReplacingMetadata() = runBlocking {
        val target = requireNotNull(repository.findExistingImportTarget("C70565"))
        assertEquals(10, target.quantity)
        assertTrue(repository.appendImportedStock(target, 5, "B").isSuccess)
        assertEquals(15, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(5, number("SELECT quantity FROM component_allocations WHERE location_id='B'"))
        assertEquals(1, number("SELECT COUNT(*) FROM components WHERE name='Existing part' AND description='Keep existing data'"))
        assertFalse(repository.appendImportedStock(target, 5, "B").isSuccess)
        val refreshed = requireNotNull(repository.findExistingImportTarget("C70565"))
        assertFalse(repository.appendImportedStock(refreshed, 0, "B").isSuccess)
        assertFalse(repository.appendImportedStock(refreshed, Int.MAX_VALUE, "B").isSuccess)
        assertFalse(repository.appendImportedStock(refreshed, 1, "MISSING").isSuccess)
        assertTrue(repository.appendImportedStock(refreshed, 15, "A").isSuccess)
        assertEquals(30, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(30, number("SELECT SUM(quantity) FROM component_allocations"))
        assertEquals(2, number("SELECT COUNT(*) FROM stock_movements"))
    }

    @Test
    fun versionFourUpgradeCreatesReceiptsAndPreservesStock() = runBlocking {
        val backupDirectory = java.io.File(context.noBackupFilesDir, "database-preupgrade")
        backupDirectory.listFiles()?.forEach { it.delete() }
        val sourceHelper = InventoryDatabaseHelper(context)
        val source = sourceHelper.writableDatabase
        source.rawQuery("PRAGMA journal_mode=WAL", null).use { assertTrue(it.moveToFirst()) }
        source.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { assertTrue(it.moveToFirst()) }
        source.execSQL("DROP TABLE batch_jlc_receipts")
        source.version = 4
        source.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { assertTrue(it.moveToFirst()) }
        source.execSQL("UPDATE components SET description='WAL pre-upgrade marker' WHERE sku='C70565'")
        val sourceWal = java.io.File(context.getDatabasePath("component-vault.db").path + "-wal")
        assertTrue(sourceWal.isFile && sourceWal.length() > 32)

        try {
            val upgraded = InventoryRepository(context)
            assertTrue(upgraded.loadBatchJlcReceipts("new-session").isEmpty())
        } finally {
            sourceHelper.close()
        }
        assertEquals(10, number("SELECT quantity FROM components WHERE sku='C70565'"))
        assertEquals(10, number("SELECT SUM(quantity) FROM component_allocations"))
        assertEquals(5, number("PRAGMA user_version"))

        val backup = requireNotNull(backupDirectory.listFiles()?.singleOrNull { it.name.endsWith(".db") })
        SQLiteDatabase.openDatabase(backup.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT description FROM components WHERE sku='C70565'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("WAL pre-upgrade marker", cursor.getString(0))
            }
            assertEquals(4, db.version)
        }
    }

    private fun row(id: String, sku: String, quantity: String, location: String) = BatchJlcRow(
        id = java.util.UUID.nameUUIDFromBytes(id.toByteArray()).toString(),
        raw = "{pc:$sku,qty:$quantity,pdi:sample-$id}", status = BatchJlcStatus.Ready,
        sku = sku, name = "New part", category = "IC", packageName = "SOT-23",
        quantityText = quantity, location = location,
    )

    private fun number(sql: String): Int = InventoryDatabaseHelper(context).use { helper ->
        helper.readableDatabase.use { db -> db.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        } }
    }
}
