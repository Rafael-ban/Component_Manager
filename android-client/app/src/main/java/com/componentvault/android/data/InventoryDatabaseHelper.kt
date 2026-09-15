package com.componentvault.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class InventoryDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE components (
                id TEXT PRIMARY KEY,
                sku TEXT NOT NULL,
                name TEXT NOT NULL,
                category TEXT NOT NULL,
                package_name TEXT NOT NULL,
                location TEXT NOT NULL,
                description TEXT,
                quantity INTEGER NOT NULL CHECK (quantity >= 0),
                min_stock INTEGER NOT NULL CHECK (min_stock >= 0),
                updated_at TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX idx_components_sku_active
            ON components(sku)
            WHERE deleted = 0
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE stock_movements (
                id TEXT PRIMARY KEY,
                component_id TEXT NOT NULL,
                movement_type TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                reason TEXT NOT NULL,
                note TEXT,
                happened_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE sync_queue (
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                entity_updated_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                PRIMARY KEY (entity_type, entity_id)
            )
            """.trimIndent(),
        )
        createImportLearningTables(db)
        createBatchOperationTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createImportLearningTables(db)
        }
        if (oldVersion < 3) {
            createBatchOperationTables(db)
        }
    }

    private fun createImportLearningTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS import_learning_mappings (
                id TEXT PRIMARY KEY,
                source_type TEXT NOT NULL,
                source_sku TEXT,
                source_mpn TEXT,
                resolved_name TEXT,
                resolved_category TEXT,
                resolved_package_name TEXT,
                resolved_model TEXT,
                resolved_brand TEXT,
                resolved_description TEXT,
                confidence INTEGER NOT NULL DEFAULT 100,
                last_used_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_import_learning_source_sku
            ON import_learning_mappings(source_sku)
            WHERE source_sku IS NOT NULL AND TRIM(source_sku) != ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS idx_import_learning_source_mpn
            ON import_learning_mappings(source_mpn)
            WHERE source_mpn IS NOT NULL AND TRIM(source_mpn) != ''
            """.trimIndent(),
        )
    }

    private fun createBatchOperationTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bom_releases (
                release_id TEXT PRIMARY KEY,
                batch_id TEXT NOT NULL,
                project_name TEXT NOT NULL,
                production_runs INTEGER NOT NULL CHECK (production_runs > 0),
                source_fingerprint TEXT NOT NULL,
                source_sheet TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_bom_releases_batch_id
            ON bom_releases(batch_id)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS component_hub_imports (
                source_fingerprint TEXT PRIMARY KEY,
                imported_count INTEGER NOT NULL CHECK (imported_count >= 0),
                skipped_count INTEGER NOT NULL CHECK (skipped_count >= 0),
                created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val DATABASE_NAME = "component-vault.db"
        const val DATABASE_VERSION = 3
    }
}
