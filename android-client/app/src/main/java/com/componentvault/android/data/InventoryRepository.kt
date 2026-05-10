package com.componentvault.android.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import androidx.annotation.StringRes
import com.componentvault.android.R
import com.componentvault.android.model.AppPreferences
import com.componentvault.android.model.ComponentDraft
import com.componentvault.android.model.ComponentImportCandidate
import com.componentvault.android.model.ComponentImportLearningMapping
import com.componentvault.android.model.ComponentImportLearningMatch
import com.componentvault.android.model.ComponentImportLearningMatchType
import com.componentvault.android.model.ComponentImportResolution
import com.componentvault.android.model.ComponentOfficialLookupOutcome
import com.componentvault.android.model.ComponentOfficialLookupResult
import com.componentvault.android.model.ComponentOfficialMetadata
import com.componentvault.android.model.ComponentRecord
import com.componentvault.android.model.DashboardSnapshot
import com.componentvault.android.model.ImportLearningSummary
import com.componentvault.android.model.MovementEntryDraft
import com.componentvault.android.model.OperationResult
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.SyncConfiguration
import com.componentvault.android.model.isJlcSource
import com.componentvault.android.model.parseImportDescription
import com.componentvault.android.model.withRecognitionMetadata
import com.componentvault.android.model.withLearningMapping
import com.componentvault.android.model.withOfficialMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class InventoryRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val databaseHelper = InventoryDatabaseHelper(appContext)
    private val localPartRecognitionEngine by lazy { LocalPartRecognitionEngine(appContext) }
    private val preferences: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    init {
        ensureDefaultSettings()
    }

    suspend fun loadDashboardSnapshot(): DashboardSnapshot = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT
                    COUNT(*) AS component_count,
                    COALESCE(SUM(quantity), 0) AS total_units,
                    COALESCE(SUM(CASE WHEN quantity <= min_stock THEN 1 ELSE 0 END), 0) AS low_stock_count
                FROM components
                WHERE deleted = 0
                """.trimIndent(),
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                DashboardSnapshot(
                    componentCount = cursor.getInt(cursor.getColumnIndexOrThrow("component_count")),
                    totalUnits = cursor.getInt(cursor.getColumnIndexOrThrow("total_units")),
                    lowStockCount = cursor.getInt(cursor.getColumnIndexOrThrow("low_stock_count")),
                    movementCount = countMovements(db),
                )
            }
        }
    }

    suspend fun loadComponents(): List<ComponentRecord> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT
                    id,
                    sku,
                    name,
                    category,
                    package_name,
                    location,
                    COALESCE(description, '') AS description,
                    quantity,
                    min_stock,
                    updated_at,
                    deleted
                FROM components
                WHERE deleted = 0
                ORDER BY updated_at DESC, name COLLATE NOCASE ASC
                """.trimIndent(),
                null,
            ).use(::readComponents)
        }
    }

    suspend fun loadLowStockComponents(): List<ComponentRecord> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT
                    id,
                    sku,
                    name,
                    category,
                    package_name,
                    location,
                    COALESCE(description, '') AS description,
                    quantity,
                    min_stock,
                    updated_at,
                    deleted
                FROM components
                WHERE deleted = 0 AND quantity <= min_stock
                ORDER BY quantity ASC, updated_at DESC
                """.trimIndent(),
                null,
            ).use(::readComponents)
        }
    }

    suspend fun loadMovements(limit: Int = 200): List<StockMovementRecord> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT
                    m.id,
                    m.component_id,
                    COALESCE(c.sku, m.component_id) AS component_sku,
                    COALESCE(c.name, m.component_id) AS component_name,
                    m.movement_type,
                    m.quantity,
                    m.reason,
                    COALESCE(m.note, '') AS note,
                    m.happened_at,
                    m.updated_at,
                    m.deleted
                FROM stock_movements m
                LEFT JOIN components c ON c.id = m.component_id
                WHERE m.deleted = 0
                ORDER BY m.happened_at DESC, m.updated_at DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(limit.toString()),
            ).use(::readMovements)
        }
    }

    suspend fun loadImportLearningSummary(): ImportLearningSummary = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT COUNT(*) AS mapping_count
                FROM import_learning_mappings
                """.trimIndent(),
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                ImportLearningSummary(
                    mappingCount = cursor.getInt(cursor.getColumnIndexOrThrow("mapping_count")),
                )
            }
        }
    }

    fun loadSyncConfiguration(): SyncConfiguration {
        ensureDefaultSettings()
        return SyncConfiguration(
            deviceId = preferences.getString(KEY_DEVICE_ID, "") ?: "",
            serverBaseUrl = preferences.getString(KEY_SERVER_BASE_URL, "") ?: "",
            apiToken = preferences.getString(KEY_API_TOKEN, "") ?: "",
            autoSyncEnabled = preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false),
            lastSyncedAt = preferences.getString(KEY_LAST_SYNCED_AT, text(R.string.sync_never))
                ?: text(R.string.sync_never),
            lastSyncMessage = preferences.getString(KEY_LAST_SYNC_MESSAGE, text(R.string.sync_no_sync_yet))
                ?: text(R.string.sync_no_sync_yet),
        )
    }

    fun loadAppPreferences(): AppPreferences {
        ensureDefaultSettings()
        return AppPreferences(
            defaultImportLocation = preferences.getString(KEY_DEFAULT_IMPORT_LOCATION, "") ?: "",
            lastImportLocation = preferences.getString(KEY_LAST_IMPORT_LOCATION, "") ?: "",
            defaultImportMinStock = preferences.getInt(KEY_DEFAULT_IMPORT_MIN_STOCK, 0),
            rememberLastImportLocation = preferences.getBoolean(KEY_REMEMBER_LAST_IMPORT_LOCATION, true),
            syncAfterLocalChanges = preferences.getBoolean(
                KEY_SYNC_AFTER_LOCAL_CHANGES,
                preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false),
            ),
            enableLocalAutoRecognition = preferences.getBoolean(KEY_ENABLE_LOCAL_AUTO_RECOGNITION, true),
            preferAggressiveAutoRecognition = preferences.getBoolean(
                KEY_PREFER_AGGRESSIVE_AUTO_RECOGNITION,
                true,
            ),
            enableLocalImportLearning = preferences.getBoolean(KEY_ENABLE_LOCAL_IMPORT_LEARNING, true),
            enableServerJlcLookup = if (preferences.contains(KEY_ENABLE_SERVER_JLC_LOOKUP)) {
                preferences.getBoolean(KEY_ENABLE_SERVER_JLC_LOOKUP, false)
            } else {
                preferences.getBoolean(KEY_AUTO_ENRICH_JLC_IMPORTS, false)
            },
        )
    }

    fun saveSyncConfiguration(
        serverBaseUrl: String,
        apiToken: String,
        autoSyncEnabled: Boolean,
    ): OperationResult {
        ensureDefaultSettings()
        preferences.edit()
            .putString(KEY_SERVER_BASE_URL, serverBaseUrl.trim().trimEnd('/'))
            .putString(KEY_API_TOKEN, apiToken.trim())
            .putBoolean(KEY_AUTO_SYNC_ENABLED, autoSyncEnabled)
            .apply()

        return OperationResult(
            isSuccess = true,
            message = text(R.string.sync_settings_saved_local),
        )
    }

    fun saveAppPreferences(
        preferencesState: AppPreferences,
    ): OperationResult {
        ensureDefaultSettings()
        preferences.edit()
            .putString(KEY_DEFAULT_IMPORT_LOCATION, preferencesState.defaultImportLocation.trim())
            .putInt(KEY_DEFAULT_IMPORT_MIN_STOCK, preferencesState.defaultImportMinStock.coerceAtLeast(0))
            .putBoolean(KEY_REMEMBER_LAST_IMPORT_LOCATION, preferencesState.rememberLastImportLocation)
            .putBoolean(KEY_SYNC_AFTER_LOCAL_CHANGES, preferencesState.syncAfterLocalChanges)
            .putBoolean(KEY_ENABLE_LOCAL_AUTO_RECOGNITION, preferencesState.enableLocalAutoRecognition)
            .putBoolean(
                KEY_PREFER_AGGRESSIVE_AUTO_RECOGNITION,
                preferencesState.preferAggressiveAutoRecognition,
            )
            .putBoolean(KEY_ENABLE_LOCAL_IMPORT_LEARNING, preferencesState.enableLocalImportLearning)
            .putBoolean(KEY_ENABLE_SERVER_JLC_LOOKUP, preferencesState.enableServerJlcLookup)
            .putBoolean(KEY_AUTO_ENRICH_JLC_IMPORTS, preferencesState.enableServerJlcLookup)
            .apply()

        return OperationResult(
            isSuccess = true,
            message = text(R.string.sync_settings_saved_local),
        )
    }

    fun rememberLastImportLocation(location: String) {
        ensureDefaultSettings()
        preferences.edit()
            .putString(KEY_LAST_IMPORT_LOCATION, location.trim())
            .apply()
    }

    suspend fun enrichImportCandidate(
        candidate: ComponentImportCandidate,
        appPreferences: AppPreferences,
        syncConfiguration: SyncConfiguration,
    ): ComponentImportResolution = withContext(Dispatchers.IO) {
        if (candidate.sourceType == com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel) {
            return@withContext ComponentImportResolution(candidate = candidate)
        }

        var enrichedCandidate = candidate
        if (appPreferences.enableLocalAutoRecognition) {
            localPartRecognitionEngine.recognize(
                candidate = candidate,
                aggressive = appPreferences.preferAggressiveAutoRecognition,
            )?.let { metadata ->
                enrichedCandidate = enrichedCandidate.withRecognitionMetadata(metadata)
            }
        }

        val learningMatch = if (appPreferences.enableLocalImportLearning) {
            databaseHelper.writableDatabase.use { db ->
                findImportLearningMatch(
                    db = db,
                    sku = candidate.sku,
                    mpn = candidate.model,
                )?.also { match ->
                    touchImportLearningMapping(
                        db = db,
                        mappingId = match.mapping.id,
                    )
                }
            }
        } else {
            null
        }

        if (learningMatch != null) {
            enrichedCandidate = enrichedCandidate.withLearningMapping(learningMatch)
        }

        val officialLookupResult = if (appPreferences.enableServerJlcLookup) {
            lookupPartMetadata(
                syncConfiguration = syncConfiguration,
                sku = enrichedCandidate.sku,
                mpn = enrichedCandidate.model,
                name = enrichedCandidate.name,
                brand = enrichedCandidate.vendor ?: enrichedCandidate.brand,
                packageHint = enrichedCandidate.normalizedPackageKey ?: enrichedCandidate.packageName,
                sourceType = enrichedCandidate.sourceType.name,
            ).also { lookupResult ->
                if (lookupResult.outcome == ComponentOfficialLookupOutcome.Success) {
                    lookupResult.metadata?.let { metadata ->
                        enrichedCandidate = enrichedCandidate.withOfficialMetadata(metadata)
                    }
                }
            }
        } else {
            null
        }

        ComponentImportResolution(
            candidate = enrichedCandidate,
            learningMatch = learningMatch,
            officialLookupResult = officialLookupResult,
        )
    }

    suspend fun lookupPartMetadata(
        syncConfiguration: SyncConfiguration,
        sku: String?,
        mpn: String?,
        name: String?,
        brand: String?,
        packageHint: String?,
        sourceType: String?,
    ): ComponentOfficialLookupResult = withContext(Dispatchers.IO) {
        val normalizedSku = sku?.trim().orEmpty()
        val normalizedMpn = mpn?.trim().orEmpty()
        val normalizedName = name?.trim().orEmpty()
        val normalizedBrand = brand?.trim().orEmpty()
        val normalizedPackageHint = packageHint?.trim().orEmpty()
        val normalizedSourceType = sourceType?.trim().orEmpty()
        if (
            normalizedSku.isBlank() &&
            normalizedMpn.isBlank() &&
            normalizedName.isBlank() &&
            normalizedBrand.isBlank() &&
            normalizedPackageHint.isBlank()
        ) {
            return@withContext ComponentOfficialLookupResult(
                outcome = ComponentOfficialLookupOutcome.NoMatch,
            )
        }

        readCachedLookup(normalizedSku, normalizedMpn)?.let { cached ->
            return@withContext ComponentOfficialLookupResult(
                outcome = ComponentOfficialLookupOutcome.Success,
                metadata = cached,
                fromCache = true,
                message = text(R.string.importer_lookup_cache_success),
            )
        }

        if (syncConfiguration.serverBaseUrl.isBlank() || syncConfiguration.apiToken.isBlank()) {
            return@withContext ComponentOfficialLookupResult(
                outcome = ComponentOfficialLookupOutcome.NotConfigured,
                message = text(R.string.importer_lookup_not_configured),
            )
        }

        return@withContext try {
            val queryParts = buildList {
                if (normalizedSku.isNotBlank()) {
                    add("sku=${java.net.URLEncoder.encode(normalizedSku, Charsets.UTF_8.name())}")
                }
                if (normalizedMpn.isNotBlank()) {
                    add("mpn=${java.net.URLEncoder.encode(normalizedMpn, Charsets.UTF_8.name())}")
                }
                if (normalizedName.isNotBlank()) {
                    add("name=${java.net.URLEncoder.encode(normalizedName, Charsets.UTF_8.name())}")
                }
                if (normalizedBrand.isNotBlank()) {
                    add("brand=${java.net.URLEncoder.encode(normalizedBrand, Charsets.UTF_8.name())}")
                }
                if (normalizedPackageHint.isNotBlank()) {
                    add("package_hint=${java.net.URLEncoder.encode(normalizedPackageHint, Charsets.UTF_8.name())}")
                }
                if (normalizedSourceType.isNotBlank()) {
                    add("source_type=${java.net.URLEncoder.encode(normalizedSourceType, Charsets.UTF_8.name())}")
                }
            }
            val response = callJson(
                settings = syncConfiguration,
                method = "GET",
                path = "/admin-api/part-lookup?${queryParts.joinToString("&")}",
                body = null,
            )
            if (!response.optBoolean("found")) {
                ComponentOfficialLookupResult(
                    outcome = ComponentOfficialLookupOutcome.NoMatch,
                    message = text(R.string.importer_lookup_no_match),
                )
            } else {
                val rawName = response.optString("name").blankToNull()
                val rawSku = response.optString("sku").blankToNull()
                val rawMpn = response.optString("mpn").blankToNull()
                val rawCategory = response.optString("category").blankToNull()
                val rawCategoryPath = response.optString("category_path").blankToNull()
                val rawBrand = response.optString("brand").blankToNull()
                val rawVendor = response.optString("vendor").blankToNull()
                val rawModelFamily = response.optString("model_family").blankToNull()
                val rawPackage = response.optString("package_name").blankToNull()
                    ?: ComponentPackageInferencer.infer(
                        rawName,
                        rawMpn,
                        rawSku,
                        rawCategory,
                        rawCategoryPath,
                    )
                val metadata = ComponentOfficialMetadata(
                    source = response.optString("source").blankToNull(),
                    sku = rawSku,
                    name = rawName,
                    packageName = rawPackage,
                    category = ComponentCategoryInferencer.infer(
                        rawCategoryPath,
                        rawCategory,
                        rawName,
                        rawPackage,
                        rawMpn,
                        rawBrand,
                    ),
                    model = rawMpn,
                    brand = rawBrand,
                    vendor = rawVendor ?: rawBrand,
                    modelFamily = rawModelFamily,
                    categoryPath = rawCategoryPath,
                    officialUrl = response.optString("official_url").blankToNull(),
                    matchedBy = response.optString("matched_by").blankToNull(),
                    confidence = response.optString("confidence").blankToNull(),
                    ruleVersion = response.optString("rule_version").blankToNull(),
                )
                cacheLookup(metadata)
                ComponentOfficialLookupResult(
                    outcome = ComponentOfficialLookupOutcome.Success,
                    metadata = metadata,
                    fromCache = response.optBoolean("cache_hit"),
                    message = if (response.optBoolean("cache_hit")) {
                        text(R.string.importer_lookup_cache_success)
                    } else {
                        text(R.string.importer_lookup_success)
                    },
                )
            }
        } catch (exception: Exception) {
            ComponentOfficialLookupResult(
                outcome = ComponentOfficialLookupOutcome.Failed,
                message = text(
                    R.string.importer_lookup_failed_pattern,
                    exception.message ?: text(R.string.importer_scanner_failed_description),
                ),
            )
        }
    }

    suspend fun clearImportLearningMappings(): OperationResult = withContext(Dispatchers.IO) {
        databaseHelper.writableDatabase.use { db ->
            db.delete("import_learning_mappings", null, null)
        }
        OperationResult(
            isSuccess = true,
            message = text(R.string.settings_local_learning_cleared),
        )
    }

    suspend fun saveComponent(draft: ComponentDraft): OperationResult = withContext(Dispatchers.IO) {
        try {
            validateComponentDraft(draft)

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    upsertComponent(db, draft)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            OperationResult(
                isSuccess = true,
                message = if (draft.id == null) {
                    text(R.string.sync_component_created_local)
                } else {
                    text(R.string.sync_component_saved_local)
                },
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_component_save_failed),
            )
        }
    }

    suspend fun saveImportedComponent(
        draft: ComponentDraft,
        sourceCandidate: ComponentImportCandidate?,
        appPreferences: AppPreferences,
    ): OperationResult = withContext(Dispatchers.IO) {
        try {
            validateComponentDraft(draft)
            val parsedDescription = parseImportDescription(draft.description)

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    upsertComponent(db, draft)
                    if (
                        appPreferences.enableLocalImportLearning &&
                        sourceCandidate != null &&
                        sourceCandidate.sourceType != com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel
                    ) {
                        upsertImportLearningMapping(
                            db = db,
                            sourceCandidate = sourceCandidate,
                            draft = draft,
                            parsedDescription = parsedDescription,
                        )
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            OperationResult(
                isSuccess = true,
                message = if (draft.id == null) {
                    text(R.string.sync_component_created_local)
                } else {
                    text(R.string.sync_component_saved_local)
                },
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_component_save_failed),
            )
        }
    }

    suspend fun softDeleteComponent(componentId: String): OperationResult = withContext(Dispatchers.IO) {
        if (componentId.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_select_component_first))
        }

        databaseHelper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                val updatedAt = utcNow()
                val values = ContentValues().apply {
                    put("deleted", 1)
                    put("updated_at", updatedAt)
                }
                val affectedRows = db.update(
                    "components",
                    values,
                    "id = ?",
                    arrayOf(componentId),
                )
                if (affectedRows == 0) {
                    return@withContext OperationResult(false, text(R.string.sync_component_not_found))
                }

                enqueueEntity(db, "component", componentId, updatedAt)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        OperationResult(
            isSuccess = true,
            message = text(R.string.sync_component_deleted_local),
        )
    }

    suspend fun recordMovement(draft: MovementEntryDraft): OperationResult = withContext(Dispatchers.IO) {
        try {
            validateMovementDraft(draft)

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    val component = getComponentById(db, draft.componentId)
                        ?: return@withContext OperationResult(
                            isSuccess = false,
                            message = text(R.string.sync_choose_active_component_first),
                        )

                    val delta = calculateQuantityDelta(draft.movementType, draft.quantity)
                    val newQuantity = component.quantity + delta
                    if (newQuantity < 0) {
                        return@withContext OperationResult(
                            isSuccess = false,
                            message = text(R.string.sync_negative_stock_error),
                        )
                    }

                    val updatedAt = utcNow()
                    val movementId = "mov-${randomId()}"
                    val movementValues = ContentValues().apply {
                        put("id", movementId)
                        put("component_id", draft.componentId)
                        put("movement_type", draft.movementType.lowercase(Locale.US))
                        put("quantity", normalizeMovementQuantity(draft.movementType, draft.quantity))
                        put("reason", draft.reason.trim())
                        put("note", draft.note.trim())
                        put("happened_at", updatedAt)
                        put("updated_at", updatedAt)
                        put("deleted", 0)
                    }
                    db.insertWithOnConflict(
                        "stock_movements",
                        null,
                        movementValues,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )

                    val componentValues = ContentValues().apply {
                        put("quantity", newQuantity)
                        put("updated_at", updatedAt)
                    }
                    db.update(
                        "components",
                        componentValues,
                        "id = ?",
                        arrayOf(draft.componentId),
                    )

                    enqueueEntity(db, "component", draft.componentId, updatedAt)
                    enqueueEntity(db, "stock_movement", movementId, updatedAt)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            OperationResult(
                isSuccess = true,
                message = text(R.string.sync_movement_recorded_local),
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_movement_record_failed),
            )
        }
    }

    suspend fun testConnection(): OperationResult = withContext(Dispatchers.IO) {
        val settings = loadSyncConfiguration()
        if (settings.serverBaseUrl.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_server_url_first))
        }
        if (settings.apiToken.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_api_token_first))
        }

        return@withContext try {
            val response = callJson(
                settings = settings,
                method = "POST",
                path = "/auth/ping",
                body = null,
            )
            OperationResult(
                isSuccess = true,
                message = text(R.string.sync_connection_ok, response.optString("server_time")),
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_connection_failed),
            )
        }
    }

    suspend fun runSync(): OperationResult = withContext(Dispatchers.IO) {
        val settings = loadSyncConfiguration()
        if (settings.serverBaseUrl.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_server_url_before_sync))
        }
        if (settings.apiToken.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_api_token_before_sync))
        }

        try {
            val pushPayload = buildPushPayload(settings.deviceId)
            val pushResponse = callJson(
                settings = settings,
                method = "POST",
                path = "/sync/push",
                body = pushPayload.payload,
            )
            val since = readStoredLastSyncedAt()
            val pullResponse = callJson(
                settings = settings,
                method = "GET",
                path = if (since == null) {
                    "/sync/pull"
                } else {
                    "/sync/pull?since=${java.net.URLEncoder.encode(since, Charsets.UTF_8.name())}"
                },
                body = null,
            )

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    applyPullResponse(
                        db = db,
                        pullResponse = pullResponse,
                        pushedEntities = pushPayload.queuedEntities,
                    )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            val acceptedComponents = pushResponse.optInt("accepted_components")
            val acceptedMovements = pushResponse.optInt("accepted_stock_movements")
            val pulledComponents = pullResponse.optJSONArray("components")?.length() ?: 0
            val pulledMovements = pullResponse.optJSONArray("stock_movements")?.length() ?: 0
            val serverTime = pullResponse.optString("server_time")
            val successMessage = text(
                R.string.sync_complete_summary,
                acceptedComponents,
                acceptedMovements,
                pulledComponents,
                pulledMovements,
            )

            updateSyncStatus(
                lastSyncedAt = serverTime,
                message = successMessage,
                preserveTimestamp = false,
            )

            OperationResult(
                isSuccess = true,
                message = successMessage,
            )
        } catch (exception: Exception) {
            updateSyncStatus(
                lastSyncedAt = null,
                message = exception.message ?: text(R.string.sync_failed),
                preserveTimestamp = true,
            )
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_failed),
            )
        }
    }

    private fun countMovements(db: SQLiteDatabase): Int {
        db.rawQuery(
            "SELECT COUNT(*) AS movement_count FROM stock_movements WHERE deleted = 0",
            null,
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(cursor.getColumnIndexOrThrow("movement_count"))
        }
    }

    private fun readComponents(cursor: Cursor): List<ComponentRecord> {
        val items = mutableListOf<ComponentRecord>()
        while (cursor.moveToNext()) {
            items += ComponentRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                sku = cursor.getString(cursor.getColumnIndexOrThrow("sku")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                minStock = cursor.getInt(cursor.getColumnIndexOrThrow("min_stock")),
                updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at")),
                deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1,
            )
        }
        return items
    }

    private fun readMovements(cursor: Cursor): List<StockMovementRecord> {
        val items = mutableListOf<StockMovementRecord>()
        while (cursor.moveToNext()) {
            items += StockMovementRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                componentId = cursor.getString(cursor.getColumnIndexOrThrow("component_id")),
                componentSku = cursor.getString(cursor.getColumnIndexOrThrow("component_sku")),
                componentName = cursor.getString(cursor.getColumnIndexOrThrow("component_name")),
                movementType = cursor.getString(cursor.getColumnIndexOrThrow("movement_type")),
                quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                reason = cursor.getString(cursor.getColumnIndexOrThrow("reason")),
                note = cursor.getString(cursor.getColumnIndexOrThrow("note")),
                happenedAt = cursor.getString(cursor.getColumnIndexOrThrow("happened_at")),
                updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at")),
                deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1,
            )
        }
        return items
    }

    private fun ensureDefaultSettings() {
        val editor = preferences.edit()
        var changed = false

        if (!preferences.contains(KEY_DEVICE_ID)) {
            editor.putString(KEY_DEVICE_ID, defaultDeviceId())
            changed = true
        }
        if (!preferences.contains(KEY_LAST_SYNCED_AT)) {
            editor.putString(KEY_LAST_SYNCED_AT, text(R.string.sync_never))
            changed = true
        }
        if (!preferences.contains(KEY_LAST_SYNC_MESSAGE)) {
            editor.putString(KEY_LAST_SYNC_MESSAGE, text(R.string.sync_no_sync_yet))
            changed = true
        }
        if (!preferences.contains(KEY_DEFAULT_IMPORT_LOCATION)) {
            editor.putString(KEY_DEFAULT_IMPORT_LOCATION, "")
            changed = true
        }
        if (!preferences.contains(KEY_LAST_IMPORT_LOCATION)) {
            editor.putString(KEY_LAST_IMPORT_LOCATION, "")
            changed = true
        }
        if (!preferences.contains(KEY_DEFAULT_IMPORT_MIN_STOCK)) {
            editor.putInt(KEY_DEFAULT_IMPORT_MIN_STOCK, 0)
            changed = true
        }
        if (!preferences.contains(KEY_REMEMBER_LAST_IMPORT_LOCATION)) {
            editor.putBoolean(KEY_REMEMBER_LAST_IMPORT_LOCATION, true)
            changed = true
        }
        if (!preferences.contains(KEY_SYNC_AFTER_LOCAL_CHANGES)) {
            editor.putBoolean(
                KEY_SYNC_AFTER_LOCAL_CHANGES,
                preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false),
            )
            changed = true
        }
        if (!preferences.contains(KEY_ENABLE_LOCAL_AUTO_RECOGNITION)) {
            editor.putBoolean(KEY_ENABLE_LOCAL_AUTO_RECOGNITION, true)
            changed = true
        }
        if (!preferences.contains(KEY_PREFER_AGGRESSIVE_AUTO_RECOGNITION)) {
            editor.putBoolean(KEY_PREFER_AGGRESSIVE_AUTO_RECOGNITION, true)
            changed = true
        }
        if (!preferences.contains(KEY_ENABLE_LOCAL_IMPORT_LEARNING)) {
            editor.putBoolean(KEY_ENABLE_LOCAL_IMPORT_LEARNING, true)
            changed = true
        }
        if (!preferences.contains(KEY_ENABLE_SERVER_JLC_LOOKUP)) {
            editor.putBoolean(
                KEY_ENABLE_SERVER_JLC_LOOKUP,
                if (preferences.contains(KEY_AUTO_ENRICH_JLC_IMPORTS)) {
                    preferences.getBoolean(KEY_AUTO_ENRICH_JLC_IMPORTS, false)
                } else {
                    false
                },
            )
            changed = true
        }
        if (!preferences.contains(KEY_AUTO_ENRICH_JLC_IMPORTS)) {
            editor.putBoolean(KEY_AUTO_ENRICH_JLC_IMPORTS, false)
            changed = true
        }
        if (changed) {
            editor.apply()
        }
    }

    private fun readCachedLookup(
        sku: String,
        mpn: String,
    ): ComponentOfficialMetadata? {
        val now = System.currentTimeMillis()
        val cacheKeys = buildList {
            if (sku.isNotBlank()) {
                add("${LOOKUP_CACHE_PREFIX}sku:${sku.lowercase(Locale.US)}")
            }
            if (mpn.isNotBlank()) {
                add("${LOOKUP_CACHE_PREFIX}mpn:${mpn.lowercase(Locale.US)}")
            }
        }

        cacheKeys.forEach { cacheKey ->
            val raw = preferences.getString(cacheKey, null) ?: return@forEach
            val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val fetchedAt = payload.optLong("fetched_at", 0L)
            if (fetchedAt <= 0L || now - fetchedAt > LOOKUP_CACHE_MAX_AGE_MS) {
                preferences.edit().remove(cacheKey).apply()
                return@forEach
            }
            return ComponentOfficialMetadata(
                source = payload.optString("source").blankToNull(),
                sku = payload.optString("sku").blankToNull(),
                name = payload.optString("name").blankToNull(),
                packageName = payload.optString("package_name").blankToNull(),
                category = payload.optString("category").blankToNull(),
                model = payload.optString("model").blankToNull(),
                brand = payload.optString("brand").blankToNull(),
                vendor = payload.optString("vendor").blankToNull(),
                modelFamily = payload.optString("model_family").blankToNull(),
                categoryPath = payload.optString("category_path").blankToNull(),
                officialUrl = payload.optString("official_url").blankToNull(),
                matchedBy = payload.optString("matched_by").blankToNull(),
                confidence = payload.optString("confidence").blankToNull(),
                ruleVersion = payload.optString("rule_version").blankToNull(),
            )
        }

        return null
    }

    private fun cacheLookup(metadata: ComponentOfficialMetadata) {
        val payload = JSONObject().apply {
            put("source", metadata.source)
            put("sku", metadata.sku)
            put("name", metadata.name)
            put("package_name", metadata.packageName)
            put("category", metadata.category)
            put("model", metadata.model)
            put("brand", metadata.brand)
            put("vendor", metadata.vendor)
            put("model_family", metadata.modelFamily)
            put("category_path", metadata.categoryPath)
            put("official_url", metadata.officialUrl)
            put("matched_by", metadata.matchedBy)
            put("confidence", metadata.confidence)
            put("rule_version", metadata.ruleVersion)
            put("fetched_at", System.currentTimeMillis())
        }.toString()

        val editor = preferences.edit()
        metadata.sku?.takeIf { it.isNotBlank() }?.let { value ->
            editor.putString("${LOOKUP_CACHE_PREFIX}sku:${value.lowercase(Locale.US)}", payload)
        }
        metadata.model?.takeIf { it.isNotBlank() }?.let { value ->
            editor.putString("${LOOKUP_CACHE_PREFIX}mpn:${value.lowercase(Locale.US)}", payload)
        }
        editor.apply()
    }

    private fun defaultDeviceId(): String {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID,
        )
        return "android-${androidId?.takeLast(8) ?: "device"}"
    }

    private fun upsertComponent(
        db: SQLiteDatabase,
        draft: ComponentDraft,
    ) {
        ensureUniqueActiveSku(db, draft.sku.trim(), draft.id)
        val updatedAt = utcNow()
        val componentId = draft.id ?: "cmp-${randomId()}"

        val values = ContentValues().apply {
            put("id", componentId)
            put("sku", draft.sku.trim())
            put("name", draft.name.trim())
            put("category", draft.category.trim())
            put("package_name", draft.packageName.trim())
            put("location", draft.location.trim())
            put("description", draft.description.trim())
            put("quantity", draft.quantity)
            put("min_stock", draft.minStock)
            put("updated_at", updatedAt)
            put("deleted", 0)
        }
        db.insertWithOnConflict(
            "components",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        enqueueEntity(db, "component", componentId, updatedAt)
    }

    private fun findImportLearningMatch(
        db: SQLiteDatabase,
        sku: String,
        mpn: String?,
    ): ComponentImportLearningMatch? {
        val normalizedSku = sku.trim()
        if (normalizedSku.isNotBlank()) {
            db.rawQuery(
                """
                SELECT
                    id,
                    source_type,
                    COALESCE(source_sku, '') AS source_sku,
                    COALESCE(source_mpn, '') AS source_mpn,
                    COALESCE(resolved_name, '') AS resolved_name,
                    COALESCE(resolved_category, '') AS resolved_category,
                    COALESCE(resolved_package_name, '') AS resolved_package_name,
                    COALESCE(resolved_model, '') AS resolved_model,
                    COALESCE(resolved_brand, '') AS resolved_brand,
                    COALESCE(resolved_description, '') AS resolved_description,
                    confidence,
                    last_used_at
                FROM import_learning_mappings
                WHERE source_sku = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalizedSku),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return ComponentImportLearningMatch(
                        mapping = readImportLearningMapping(cursor),
                        matchedBy = ComponentImportLearningMatchType.Sku,
                    )
                }
            }
        }

        val normalizedMpn = mpn?.trim().orEmpty()
        if (normalizedMpn.isNotBlank()) {
            db.rawQuery(
                """
                SELECT
                    id,
                    source_type,
                    COALESCE(source_sku, '') AS source_sku,
                    COALESCE(source_mpn, '') AS source_mpn,
                    COALESCE(resolved_name, '') AS resolved_name,
                    COALESCE(resolved_category, '') AS resolved_category,
                    COALESCE(resolved_package_name, '') AS resolved_package_name,
                    COALESCE(resolved_model, '') AS resolved_model,
                    COALESCE(resolved_brand, '') AS resolved_brand,
                    COALESCE(resolved_description, '') AS resolved_description,
                    confidence,
                    last_used_at
                FROM import_learning_mappings
                WHERE source_mpn = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalizedMpn),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return ComponentImportLearningMatch(
                        mapping = readImportLearningMapping(cursor),
                        matchedBy = ComponentImportLearningMatchType.Mpn,
                    )
                }
            }
        }

        return null
    }

    private fun readImportLearningMapping(
        cursor: Cursor,
    ): ComponentImportLearningMapping {
        return ComponentImportLearningMapping(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            sourceType = cursor.getString(cursor.getColumnIndexOrThrow("source_type")).toImportSourceType(),
            sourceSku = cursor.getString(cursor.getColumnIndexOrThrow("source_sku")).blankToNull(),
            sourceMpn = cursor.getString(cursor.getColumnIndexOrThrow("source_mpn")).blankToNull(),
            resolvedName = cursor.getString(cursor.getColumnIndexOrThrow("resolved_name")).blankToNull(),
            resolvedCategory = cursor.getString(cursor.getColumnIndexOrThrow("resolved_category")).blankToNull(),
            resolvedPackageName = cursor.getString(cursor.getColumnIndexOrThrow("resolved_package_name")).blankToNull(),
            resolvedModel = cursor.getString(cursor.getColumnIndexOrThrow("resolved_model")).blankToNull(),
            resolvedBrand = cursor.getString(cursor.getColumnIndexOrThrow("resolved_brand")).blankToNull(),
            resolvedDescription = cursor.getString(cursor.getColumnIndexOrThrow("resolved_description")).blankToNull(),
            confidence = cursor.getInt(cursor.getColumnIndexOrThrow("confidence")),
            lastUsedAt = cursor.getString(cursor.getColumnIndexOrThrow("last_used_at")).blankToNull(),
        )
    }

    private fun touchImportLearningMapping(
        db: SQLiteDatabase,
        mappingId: String,
    ) {
        db.update(
            "import_learning_mappings",
            ContentValues().apply {
                put("last_used_at", utcNow())
            },
            "id = ?",
            arrayOf(mappingId),
        )
    }

    private fun upsertImportLearningMapping(
        db: SQLiteDatabase,
        sourceCandidate: ComponentImportCandidate,
        draft: ComponentDraft,
        parsedDescription: com.componentvault.android.model.ParsedImportDescription,
    ) {
        val normalizedSourceSku = sourceCandidate.sku.trim().blankToNull() ?: draft.sku.trim().blankToNull()
        val normalizedSourceMpn = sourceCandidate.model?.trim()?.blankToNull()
            ?: parsedDescription.model?.trim()?.blankToNull()
        if (normalizedSourceSku == null && normalizedSourceMpn == null) {
            return
        }

        val now = utcNow()
        val existingRecord = findExistingImportLearningRecord(
            db = db,
            sourceSku = normalizedSourceSku,
            sourceMpn = normalizedSourceMpn,
        )
        val mappingId = existingRecord?.first ?: "ilm-${randomId()}"
        val createdAt = existingRecord?.second ?: now

        val values = ContentValues().apply {
            put("id", mappingId)
            put("source_type", sourceCandidate.sourceType.toStorageValue())
            put("source_sku", normalizedSourceSku)
            put("source_mpn", normalizedSourceMpn)
            put("resolved_name", draft.name.trim().blankToNull())
            put("resolved_category", draft.category.trim().blankToNull())
            put("resolved_package_name", draft.packageName.trim().blankToNull())
            put("resolved_model", parsedDescription.model?.trim()?.blankToNull() ?: sourceCandidate.model?.trim()?.blankToNull())
            put("resolved_brand", parsedDescription.brand?.trim()?.blankToNull() ?: sourceCandidate.brand?.trim()?.blankToNull())
            put("resolved_description", draft.description.trim().blankToNull())
            put("confidence", 100)
            put("last_used_at", now)
            put("created_at", createdAt)
            put("updated_at", now)
        }
        db.insertWithOnConflict(
            "import_learning_mappings",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun findExistingImportLearningRecord(
        db: SQLiteDatabase,
        sourceSku: String?,
        sourceMpn: String?,
    ): Pair<String, String>? {
        sourceSku?.let { sku ->
            db.rawQuery(
                """
                SELECT id, created_at
                FROM import_learning_mappings
                WHERE source_sku = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(sku),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow("id")) to
                        cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
                }
            }
        }

        sourceMpn?.let { mpn ->
            db.rawQuery(
                """
                SELECT id, created_at
                FROM import_learning_mappings
                WHERE source_mpn = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(mpn),
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow("id")) to
                        cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
                }
            }
        }

        return null
    }

    private fun validateComponentDraft(draft: ComponentDraft) {
        if (
            draft.sku.isBlank() ||
            draft.name.isBlank() ||
            draft.category.isBlank() ||
            draft.packageName.isBlank() ||
            draft.location.isBlank()
        ) {
            throw IllegalStateException(text(R.string.sync_component_required_fields))
        }
        if (draft.quantity < 0 || draft.minStock < 0) {
            throw IllegalStateException(text(R.string.sync_component_non_negative))
        }
    }

    private fun validateMovementDraft(draft: MovementEntryDraft) {
        if (draft.componentId.isBlank() || draft.reason.isBlank() || draft.movementType.isBlank()) {
            throw IllegalStateException(text(R.string.sync_choose_component_type_reason))
        }
        if (draft.movementType == "adjustment") {
            if (draft.quantity == 0) {
                throw IllegalStateException(text(R.string.sync_adjustment_non_zero))
            }
        } else if (draft.quantity <= 0) {
            throw IllegalStateException(text(R.string.sync_movement_quantity_positive))
        }
    }

    private fun ensureUniqueActiveSku(
        db: SQLiteDatabase,
        sku: String,
        componentId: String?,
    ) {
        db.rawQuery(
            """
            SELECT COUNT(*) AS duplicate_count
            FROM components
            WHERE deleted = 0 AND sku = ? AND id != COALESCE(?, '')
            """.trimIndent(),
            arrayOf(sku, componentId ?: ""),
        ).use { cursor ->
            cursor.moveToFirst()
            if (cursor.getInt(cursor.getColumnIndexOrThrow("duplicate_count")) > 0) {
                throw IllegalStateException(text(R.string.sync_duplicate_active_sku))
            }
        }
    }

    private fun getComponentById(
        db: SQLiteDatabase,
        componentId: String,
    ): ComponentRecord? {
        db.rawQuery(
            """
            SELECT
                id,
                sku,
                name,
                category,
                package_name,
                location,
                COALESCE(description, '') AS description,
                quantity,
                min_stock,
                updated_at,
                deleted
            FROM components
            WHERE id = ?
            """.trimIndent(),
            arrayOf(componentId),
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                ComponentRecord(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    sku = cursor.getString(cursor.getColumnIndexOrThrow("sku")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity")),
                    minStock = cursor.getInt(cursor.getColumnIndexOrThrow("min_stock")),
                    updatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at")),
                    deleted = cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1,
                )
            } else {
                null
            }
        }
    }

    private fun enqueueEntity(
        db: SQLiteDatabase,
        entityType: String,
        entityId: String,
        entityUpdatedAt: String,
    ) {
        val values = ContentValues().apply {
            put("entity_type", entityType)
            put("entity_id", entityId)
            put("entity_updated_at", entityUpdatedAt)
            put("created_at", utcNow())
        }
        db.insertWithOnConflict(
            "sync_queue",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun buildPushPayload(deviceId: String): SyncPayload {
        databaseHelper.readableDatabase.use { db ->
            val queuedEntities = mutableListOf<QueuedEntity>()
            db.rawQuery(
                """
                SELECT entity_type, entity_id, entity_updated_at
                FROM sync_queue
                ORDER BY created_at ASC
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    queuedEntities += QueuedEntity(
                        entityType = cursor.getString(cursor.getColumnIndexOrThrow("entity_type")),
                        entityId = cursor.getString(cursor.getColumnIndexOrThrow("entity_id")),
                        entityUpdatedAt = cursor.getString(cursor.getColumnIndexOrThrow("entity_updated_at")),
                    )
                }
            }

            val payload = JSONObject().apply {
                put("device_id", deviceId)
                put("components", JSONArray())
                put("stock_movements", JSONArray())
            }

            queuedEntities.forEach { entity ->
                when (entity.entityType) {
                    "component" -> {
                        getComponentJson(db, entity.entityId)?.let {
                            payload.getJSONArray("components").put(it)
                        }
                    }
                    "stock_movement" -> {
                        getMovementJson(db, entity.entityId)?.let {
                            payload.getJSONArray("stock_movements").put(it)
                        }
                    }
                }
            }

            return SyncPayload(
                payload = payload,
                queuedEntities = queuedEntities,
            )
        }
    }

    private fun getComponentJson(
        db: SQLiteDatabase,
        componentId: String,
    ): JSONObject? {
        db.rawQuery(
            """
            SELECT
                id,
                sku,
                name,
                category,
                package_name,
                location,
                description,
                quantity,
                min_stock,
                updated_at,
                deleted
            FROM components
            WHERE id = ?
            """.trimIndent(),
            arrayOf(componentId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            return JSONObject().apply {
                put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                put("sku", cursor.getString(cursor.getColumnIndexOrThrow("sku")))
                put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                put("category", cursor.getString(cursor.getColumnIndexOrThrow("category")))
                put("package_name", cursor.getString(cursor.getColumnIndexOrThrow("package_name")))
                put("location", cursor.getString(cursor.getColumnIndexOrThrow("location")))
                put("description", cursor.getString(cursor.getColumnIndexOrThrow("description")))
                put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")))
                put("min_stock", cursor.getInt(cursor.getColumnIndexOrThrow("min_stock")))
                put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
                put("deleted", cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1)
            }
        }
    }

    private fun getMovementJson(
        db: SQLiteDatabase,
        movementId: String,
    ): JSONObject? {
        db.rawQuery(
            """
            SELECT
                id,
                component_id,
                movement_type,
                quantity,
                reason,
                note,
                happened_at,
                updated_at,
                deleted
            FROM stock_movements
            WHERE id = ?
            """.trimIndent(),
            arrayOf(movementId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            return JSONObject().apply {
                put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                put("component_id", cursor.getString(cursor.getColumnIndexOrThrow("component_id")))
                put("movement_type", cursor.getString(cursor.getColumnIndexOrThrow("movement_type")))
                put("quantity", cursor.getInt(cursor.getColumnIndexOrThrow("quantity")))
                put("reason", cursor.getString(cursor.getColumnIndexOrThrow("reason")))
                put("note", cursor.getString(cursor.getColumnIndexOrThrow("note")))
                put("happened_at", cursor.getString(cursor.getColumnIndexOrThrow("happened_at")))
                put("updated_at", cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
                put("deleted", cursor.getInt(cursor.getColumnIndexOrThrow("deleted")) == 1)
            }
        }
    }

    private fun callJson(
        settings: SyncConfiguration,
        method: String,
        path: String,
        body: JSONObject?,
    ): JSONObject {
        val connection = URL("${settings.serverBaseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer ${settings.apiToken}")
        connection.setRequestProperty("Accept", "application/json")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body.toString())
            }
        }

        return try {
            val responseCode = connection.responseCode
            val responseText = readResponseText(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException(buildErrorMessage(responseCode, responseText))
            }
            if (responseText.isBlank()) {
                JSONObject()
            } else {
                JSONObject(responseText)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readResponseText(
        connection: HttpURLConnection,
        responseCode: Int,
    ): String {
        val stream = if (responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return stream.bufferedReader().use { it.readText() }
    }

    private fun buildErrorMessage(
        statusCode: Int,
        body: String,
    ): String {
        if (body.isBlank()) {
            return text(R.string.sync_server_error, statusCode)
        }

        return try {
            val detail = JSONObject(body).optString("detail")
            if (detail.isBlank()) {
                text(R.string.sync_server_error_detail, statusCode, body)
            } else {
                text(R.string.sync_server_error_detail, statusCode, detail)
            }
        } catch (_: Exception) {
            text(R.string.sync_server_error_detail, statusCode, body)
        }
    }

    private fun applyPullResponse(
        db: SQLiteDatabase,
        pullResponse: JSONObject,
        pushedEntities: List<QueuedEntity>,
    ) {
        val components = pullResponse.optJSONArray("components") ?: JSONArray()
        for (index in 0 until components.length()) {
            val component = components.getJSONObject(index)
            upsertRemoteComponent(db, component)
            removeQueuedIfSuperseded(
                db = db,
                entityType = "component",
                entityId = component.getString("id"),
                updatedAt = component.getString("updated_at"),
            )
        }

        val movements = pullResponse.optJSONArray("stock_movements") ?: JSONArray()
        for (index in 0 until movements.length()) {
            val movement = movements.getJSONObject(index)
            upsertRemoteMovement(db, movement)
            removeQueuedIfSuperseded(
                db = db,
                entityType = "stock_movement",
                entityId = movement.getString("id"),
                updatedAt = movement.getString("updated_at"),
            )
        }

        pushedEntities.forEach { entity ->
            db.delete(
                "sync_queue",
                "entity_type = ? AND entity_id = ?",
                arrayOf(entity.entityType, entity.entityId),
            )
        }
    }

    private fun upsertRemoteComponent(
        db: SQLiteDatabase,
        component: JSONObject,
    ) {
        val existing = getComponentById(db, component.getString("id"))
        if (existing != null && existing.updatedAt > component.getString("updated_at")) {
            return
        }

        val values = ContentValues().apply {
            put("id", component.getString("id"))
            put("sku", component.getString("sku"))
            put("name", component.getString("name"))
            put("category", component.getString("category"))
            put("package_name", component.getString("package_name"))
            put("location", component.getString("location"))
            put("description", component.optString("description"))
            put("quantity", component.getInt("quantity"))
            put("min_stock", component.getInt("min_stock"))
            put("updated_at", component.getString("updated_at"))
            put("deleted", if (component.getBoolean("deleted")) 1 else 0)
        }
        db.insertWithOnConflict(
            "components",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun upsertRemoteMovement(
        db: SQLiteDatabase,
        movement: JSONObject,
    ) {
        db.rawQuery(
            "SELECT updated_at FROM stock_movements WHERE id = ?",
            arrayOf(movement.getString("id")),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val existingUpdatedAt = cursor.getString(cursor.getColumnIndexOrThrow("updated_at"))
                if (existingUpdatedAt > movement.getString("updated_at")) {
                    return
                }
            }
        }

        val values = ContentValues().apply {
            put("id", movement.getString("id"))
            put("component_id", movement.getString("component_id"))
            put("movement_type", movement.getString("movement_type"))
            put("quantity", movement.getInt("quantity"))
            put("reason", movement.getString("reason"))
            put("note", movement.optString("note"))
            put("happened_at", movement.getString("happened_at"))
            put("updated_at", movement.getString("updated_at"))
            put("deleted", if (movement.getBoolean("deleted")) 1 else 0)
        }
        db.insertWithOnConflict(
            "stock_movements",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun removeQueuedIfSuperseded(
        db: SQLiteDatabase,
        entityType: String,
        entityId: String,
        updatedAt: String,
    ) {
        db.delete(
            "sync_queue",
            "entity_type = ? AND entity_id = ? AND entity_updated_at <= ?",
            arrayOf(entityType, entityId, updatedAt),
        )
    }

    private fun updateSyncStatus(
        lastSyncedAt: String?,
        message: String,
        preserveTimestamp: Boolean,
    ) {
        val editor = preferences.edit().putString(KEY_LAST_SYNC_MESSAGE, message)
        if (!preserveTimestamp && lastSyncedAt != null) {
            editor.putString(KEY_LAST_SYNCED_AT, lastSyncedAt)
        }
        editor.apply()
    }

    private fun readStoredLastSyncedAt(): String? {
        val value = preferences.getString(KEY_LAST_SYNCED_AT, null)
        return if (value.isNullOrBlank() || value == text(R.string.sync_never)) {
            null
        } else {
            value
        }
    }

    private fun calculateQuantityDelta(
        movementType: String,
        quantity: Int,
    ): Int = when (movementType.lowercase(Locale.US)) {
        "inbound" -> quantity
        "outbound" -> -quantity
        "adjustment" -> quantity
        else -> throw IllegalStateException(text(R.string.sync_unsupported_movement_type))
    }

    private fun normalizeMovementQuantity(
        movementType: String,
        quantity: Int,
    ): Int = when (movementType.lowercase(Locale.US)) {
        "adjustment" -> quantity
        else -> kotlin.math.abs(quantity)
    }

    private fun utcNow(): String = TIMESTAMP_FORMATTER.format(Instant.now())

    private fun randomId(): String = java.util.UUID.randomUUID().toString().replace("-", "")

    private fun text(
        @StringRes resId: Int,
        vararg args: Any,
    ): String = appContext.getString(resId, *args)

    private fun String.toImportSourceType() = when (lowercase(Locale.US)) {
        "jlc_text" -> com.componentvault.android.model.ComponentImportSourceType.JlcText
        "jlc_qr" -> com.componentvault.android.model.ComponentImportSourceType.JlcQr
        "supplier_ocr" -> com.componentvault.android.model.ComponentImportSourceType.SupplierOcr
        else -> com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel
    }

    private fun com.componentvault.android.model.ComponentImportSourceType.toStorageValue(): String =
        when (this) {
            com.componentvault.android.model.ComponentImportSourceType.JlcText -> "jlc_text"
            com.componentvault.android.model.ComponentImportSourceType.JlcQr -> "jlc_qr"
            com.componentvault.android.model.ComponentImportSourceType.SupplierOcr -> "supplier_ocr"
            com.componentvault.android.model.ComponentImportSourceType.WarehouseLabel -> "warehouse_label"
        }

    private fun String.blankToNull(): String? = trim().takeIf { it.isNotBlank() }

    private data class SyncPayload(
        val payload: JSONObject,
        val queuedEntities: List<QueuedEntity>,
    )

    private data class QueuedEntity(
        val entityType: String,
        val entityId: String,
        val entityUpdatedAt: String,
    )

    private companion object {
        const val PREFS_NAME = "component_vault_sync"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SERVER_BASE_URL = "server_base_url"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
        const val KEY_DEFAULT_IMPORT_LOCATION = "default_import_location"
        const val KEY_LAST_IMPORT_LOCATION = "last_import_location"
        const val KEY_DEFAULT_IMPORT_MIN_STOCK = "default_import_min_stock"
        const val KEY_REMEMBER_LAST_IMPORT_LOCATION = "remember_last_import_location"
        const val KEY_SYNC_AFTER_LOCAL_CHANGES = "sync_after_local_changes"
        const val KEY_ENABLE_LOCAL_AUTO_RECOGNITION = "enable_local_auto_recognition"
        const val KEY_PREFER_AGGRESSIVE_AUTO_RECOGNITION = "prefer_aggressive_auto_recognition"
        const val KEY_ENABLE_LOCAL_IMPORT_LEARNING = "enable_local_import_learning"
        const val KEY_ENABLE_SERVER_JLC_LOOKUP = "enable_server_jlc_lookup"
        const val KEY_AUTO_ENRICH_JLC_IMPORTS = "auto_enrich_jlc_imports"
        const val LOOKUP_CACHE_PREFIX = "lcsc_lookup_cache:"
        const val LOOKUP_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withLocale(Locale.US)
            .withZone(ZoneOffset.UTC)
    }
}
