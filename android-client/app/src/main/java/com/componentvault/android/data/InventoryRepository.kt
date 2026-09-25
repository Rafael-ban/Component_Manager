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
import com.componentvault.android.model.AppLanguage
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
import com.componentvault.android.model.OcrEngineMode
import com.componentvault.android.model.MovementScanMatchStatus
import com.componentvault.android.model.MovementScanResolutionUiState
import com.componentvault.android.model.StockMovementRecord
import com.componentvault.android.model.StorageLocationRecord
import com.componentvault.android.model.StorageLocationSaveIntent
import com.componentvault.android.model.ComponentAllocationRecord
import com.componentvault.android.model.SyncConfiguration
import com.componentvault.android.model.isJlcSource
import com.componentvault.android.model.parseImportDescription
import com.componentvault.android.model.withRecognitionMetadata
import com.componentvault.android.model.withLearningMapping
import com.componentvault.android.model.withOfficialMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    internal data class WorkbookPreview(val workbook:InventoryWorkbook,val newComponentCount:Int,val skippedComponentCount:Int,val newLocationCount:Int,val warnings:List<String>)
    private val appContext = context.applicationContext
    private val databaseHelper = InventoryDatabaseHelper(appContext)
    private val inventoryImageStore = InventoryImageStore(appContext)
    private val localPartRecognitionEngine by lazy { LocalPartRecognitionEngine(appContext) }
    private val publicCatalogLookup = LcscCombinedLookup()
    internal fun retryDomesticCatalogNow() = publicCatalogLookup.retryDomesticNow()
    internal fun searchDomesticCatalog(keyword: String) = publicCatalogLookup.searchDomestic(keyword)
    private val preferences: SharedPreferences = appContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    private val syncMutex = Mutex()
    private val syncConfigurationLock = Any()

    init {
        ensureDefaultSettings()
        cleanupLookupCache()
        if (!preferences.getBoolean(KEY_INVENTORY_PROTOCOL_MIGRATED, false)) {
            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    db.rawQuery("SELECT id, updated_at FROM components", null).use { cursor ->
                        while (cursor.moveToNext()) enqueueEntity(db, "component", cursor.getString(0), cursor.getString(1))
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            preferences.edit()
                .remove(KEY_SYNC_CURSOR)
                .putBoolean(KEY_INVENTORY_PROTOCOL_MIGRATED, true)
                .commit()
        }
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
                    m.deleted,
                    m.location_id,
                    m.destination_location_id
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

    suspend fun resolveComponentByScannedLabel(
        rawValue: String,
    ): MovementScanResolutionUiState = withContext(Dispatchers.IO) {
        val candidate = ComponentLabelCodec.parseScannedPayload(rawValue)
            ?: return@withContext MovementScanResolutionUiState(
                matchStatus = MovementScanMatchStatus.InvalidLabel,
                rawValue = rawValue.trim(),
            )

        databaseHelper.readableDatabase.use { db ->
            val matches = findActiveComponentsBySku(
                db = db,
                sku = candidate.sku,
            )

            when {
                matches.isEmpty() -> MovementScanResolutionUiState(
                    matchStatus = MovementScanMatchStatus.NotFound,
                    rawValue = rawValue.trim(),
                    parsedSku = candidate.sku,
                    parsedName = candidate.name,
                    parsedPackageName = candidate.packageName,
                    parsedLocation = extractWarehouseLocation(candidate),
                )

                matches.size == 1 -> MovementScanResolutionUiState(
                    matchStatus = MovementScanMatchStatus.Matched,
                    rawValue = rawValue.trim(),
                    parsedSku = candidate.sku,
                    parsedName = candidate.name,
                    parsedPackageName = candidate.packageName,
                    parsedLocation = extractWarehouseLocation(candidate),
                    matchedComponent = matches.single(),
                )

                else -> MovementScanResolutionUiState(
                    matchStatus = MovementScanMatchStatus.Ambiguous,
                    rawValue = rawValue.trim(),
                    parsedSku = candidate.sku,
                    parsedName = candidate.name,
                    parsedPackageName = candidate.packageName,
                    parsedLocation = extractWarehouseLocation(candidate),
                )
            }
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
            externalServerBaseUrl = preferences.getString(KEY_EXTERNAL_SERVER_BASE_URL, "") ?: "",
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
            enableServerJlcLookup = false,
            enablePublicJlcLookup = preferences.getBoolean("enable_public_jlc_lookup", true),
            ocrEngineMode = OcrEngineMode.fromStorageValue(
                preferences.getString(KEY_OCR_ENGINE_MODE, OcrEngineMode.Auto.storageValue),
            ),
            appLanguage = AppLanguage.fromStorageValue(
                preferences.getString(KEY_APP_LANGUAGE, AppLanguage.ZhCn.storageValue),
            ),
        )
    }

    fun saveSyncConfiguration(
        serverBaseUrl: String,
        apiToken: String,
        autoSyncEnabled: Boolean,
        externalServerBaseUrl: String = "",
    ): OperationResult {
        ensureDefaultSettings()
        val normalizedServerUrl = serverBaseUrl.trim().trimEnd('/')
        synchronized(syncConfigurationLock) {
            preferences.edit()
                .putString(KEY_SERVER_BASE_URL, normalizedServerUrl)
                .putString(KEY_EXTERNAL_SERVER_BASE_URL, externalServerBaseUrl.trim().trimEnd('/'))
                .putString(KEY_API_TOKEN, apiToken.trim())
                .putBoolean(KEY_AUTO_SYNC_ENABLED, autoSyncEnabled)
                .commit()
        }

        return OperationResult(
            isSuccess = true,
            message = text(R.string.sync_settings_saved_local),
        )
    }

    fun saveAppPreferences(
        preferencesState: AppPreferences,
    ): OperationResult {
        ensureDefaultSettings()
        val languageChanged = loadAppPreferences().appLanguage != preferencesState.appLanguage
        if (languageChanged) clearLookupCache()
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
            .putBoolean("enable_public_jlc_lookup", preferencesState.enablePublicJlcLookup)
            .putBoolean(KEY_AUTO_ENRICH_JLC_IMPORTS, preferencesState.enableServerJlcLookup)
            .putString(KEY_OCR_ENGINE_MODE, preferencesState.ocrEngineMode.storageValue)
            .putString(KEY_APP_LANGUAGE, preferencesState.appLanguage.storageValue)
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

        val publicLookupResult = if (appPreferences.enablePublicJlcLookup &&
            LcscPublicCatalog.normalizeSku(enrichedCandidate.sku) != null
        ) {
            lookupPublicPartMetadata(enrichedCandidate.sku)
        } else null

        val officialLookupResult = if (publicLookupResult?.outcome == ComponentOfficialLookupOutcome.Success) {
            publicLookupResult.also { result ->
                result.metadata?.let { enrichedCandidate = enrichedCandidate.withOfficialMetadata(it) }
            }
        } else {
            publicLookupResult
        }

        ComponentImportResolution(
            candidate = enrichedCandidate,
            learningMatch = learningMatch,
            officialLookupResult = officialLookupResult,
        )
    }

    private suspend fun lookupPublicPartMetadata(sku: String): ComponentOfficialLookupResult =
        withContext(Dispatchers.IO) {
            val language = loadAppPreferences().appLanguage
            val preferredSource = LcscCatalogRoutePolicy.preferredSource(language)
            val cacheScope = preferredSource.wireName
            readCachedLookup(sku, "", cacheScope)?.takeIf {
                it.sku.equals(sku, true)
            }?.let {
                return@withContext ComponentOfficialLookupResult(
                    outcome = ComponentOfficialLookupOutcome.Success,
                    metadata = it.copy(
                        category = it.categoryPath?.takeIf(String::isNotBlank)
                            ?.let(OfficialCategoryNormalizer::normalize) ?: it.category,
                    ),
                    fromCache = true,
                    message = text(
                        R.string.catalog_lookup_cache_success,
                        text(
                            if (it.source == LcscCatalogSource.Domestic.wireName) R.string.catalog_source_domestic
                            else R.string.catalog_source_international,
                        ),
                    ),
                )
            }
            try {
                val route = publicCatalogLookup.lookupWithRoute(sku, preferredSource)
                val metadata = route.metadata
                if (metadata == null) {
                    ComponentOfficialLookupResult(
                        outcome = if (route.attempts.all { it.failure == LcscCatalogFailureKind.NoMatch }) {
                            ComponentOfficialLookupOutcome.NoMatch
                        } else {
                            ComponentOfficialLookupOutcome.Failed
                        },
                        message = catalogLookupMessage(route),
                    )
                } else {
                    if (LcscCatalogRoutePolicy.shouldPersist(route)) cacheLookup(metadata, cacheScope)
                    ComponentOfficialLookupResult(
                        outcome = ComponentOfficialLookupOutcome.Success,
                        metadata = metadata,
                        message = catalogLookupMessage(route),
                    )
                }
            } catch (error: IOException) {
                ComponentOfficialLookupResult(
                    outcome = ComponentOfficialLookupOutcome.Failed,
                    message = text(R.string.importer_public_lookup_unavailable),
                )
            }
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

        readCachedLookup(normalizedSku, normalizedMpn, SERVER_LOOKUP_CACHE_SCOPE)?.let { cached ->
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
                cacheLookup(metadata, SERVER_LOOKUP_CACHE_SCOPE)
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
            var componentId: String? = null

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    componentId = upsertComponent(db, draft)
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
                entityId = componentId,
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
            var componentId: String? = null

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    componentId = upsertComponent(db, draft)
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
                entityId = componentId,
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
                    applyMovementDrafts(db, listOf(draft))
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

    suspend fun loadStorageLocations(): List<StorageLocationRecord> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT id, name, updated_at, deleted FROM storage_locations WHERE deleted = 0 ORDER BY name, id",
                null,
            ).use { cursor -> buildList {
                while (cursor.moveToNext()) add(StorageLocationRecord(
                    id = cursor.getString(0), name = cursor.getString(1),
                    updatedAt = cursor.getString(2), deleted = cursor.getInt(3) == 1,
                ))
            } }
        }
    }

    internal suspend fun findExistingImportTarget(sku: String): ExistingImportTarget? = withContext(Dispatchers.IO) {
        val normalized = LcscPublicCatalog.normalizeSku(sku) ?: return@withContext null
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT id,sku,quantity,updated_at,location FROM components WHERE UPPER(sku)=? AND deleted=0 LIMIT 2",
                arrayOf(normalized),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val target = ExistingImportTarget(
                    cursor.getString(0), cursor.getString(1), cursor.getInt(2), cursor.getString(3), cursor.getString(4),
                )
                check(!cursor.moveToNext()) { "存在多个大小写不同但料号相同的活跃元器件，请先合并冲突记录。" }
                target
            }
        }
    }

    private fun catalogLookupMessage(result: LcscCatalogLookupResult): String {
        fun sourceName(source: LcscCatalogSource): String = text(
            if (source == LcscCatalogSource.Domestic) R.string.catalog_source_domestic
            else R.string.catalog_source_international,
        )
        fun failureName(failure: LcscCatalogFailureKind): String = text(
            when (failure) {
                LcscCatalogFailureKind.Blocked -> R.string.catalog_failure_blocked
                LcscCatalogFailureKind.RateLimited -> R.string.catalog_failure_rate_limited
                LcscCatalogFailureKind.CoolingDown -> R.string.catalog_failure_cooling_down
                LcscCatalogFailureKind.Unreachable -> R.string.catalog_failure_unreachable
                LcscCatalogFailureKind.NoMatch -> R.string.catalog_failure_no_match
                LcscCatalogFailureKind.InvalidResponse -> R.string.catalog_failure_invalid_response
            },
        )
        val resolved = result.resolvedSource
        if (resolved != null && !result.usedFallback) {
            return appContext.getString(R.string.catalog_lookup_source_success, sourceName(resolved))
        }
        if (resolved != null) {
            val primaryFailure = result.attempts.firstOrNull()?.failure ?: LcscCatalogFailureKind.NoMatch
            return appContext.getString(
                R.string.catalog_lookup_fallback_success,
                sourceName(result.preferredSource),
                failureName(primaryFailure),
                sourceName(resolved),
            )
        }
        val failures = LcscCatalogRoutePolicy.order(result.preferredSource).mapIndexed { index, source ->
            val failure = result.attempts.getOrNull(index)?.failure ?: LcscCatalogFailureKind.NoMatch
            "${sourceName(source)}：${failureName(failure)}"
        }
        return appContext.getString(R.string.catalog_lookup_all_failed, failures[0], failures[1])
    }

    internal suspend fun appendImportedStock(
        target: ExistingImportTarget,
        quantity: Int,
        locationId: String,
    ): OperationResult = withContext(Dispatchers.IO) {
        runCatching {
            require(quantity > 0) { "入库数量必须为正整数。" }
            val location = locationId.trim()
            require(location.isNotBlank()) { "请选择入库库位。" }
            var newTotal = 0
            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    val current = db.rawQuery(
                        "SELECT quantity,updated_at FROM components WHERE id=? AND deleted=0",
                        arrayOf(target.componentId),
                    ).use { cursor ->
                        check(cursor.moveToFirst()) { "元器件已不存在，请重新核对。" }
                        cursor.getInt(0) to cursor.getString(1)
                    }
                    check(current.second == target.updatedAt) { "库存已发生变化，请重新核对后再确认。" }
                    check(db.rawQuery("SELECT 1 FROM storage_locations WHERE id=? AND deleted=0", arrayOf(location)).use { it.moveToFirst() }) {
                        "库位不存在或已删除。"
                    }
                    newTotal = Math.addExact(current.first, quantity)
                    val newAllocation = Math.addExact(allocationQuantity(db, target.componentId, location), quantity)
                    val now = utcNow()
                    db.update("components", ContentValues().apply {
                        put("quantity", newTotal); put("location", location); put("updated_at", now)
                    }, "id=?", arrayOf(target.componentId))
                    setAllocationQuantity(db, target.componentId, location, newAllocation)
                    verifyAllocationTotal(db, target.componentId, newTotal)
                    val movementId = "mov-" + randomId()
                    db.insertOrThrow("stock_movements", null, ContentValues().apply {
                        put("id", movementId); put("component_id", target.componentId); put("movement_type", "inbound")
                        put("quantity", quantity); put("reason", "JLC single import inbound"); put("note", "")
                        put("happened_at", now); put("updated_at", now); put("deleted", 0); put("location_id", location)
                    })
                    enqueueEntity(db, "component", target.componentId, now)
                    enqueueEntity(db, "stock_movement", movementId, now)
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            OperationResult(true, "已追加 $quantity 个，当前库存 $newTotal。", target.componentId)
        }.getOrElse { OperationResult(false, it.message ?: "追加库存失败。") }
    }

    internal suspend fun loadBatchJlcReceipts(sessionId:String):Set<String> = withContext(Dispatchers.IO){
        databaseHelper.readableDatabase.use{db->db.rawQuery("SELECT row_id FROM batch_jlc_receipts WHERE session_id = ?",arrayOf(sessionId)).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}}
    }

    internal suspend fun commitBatchJlc(sessionId:String,rows:List<BatchJlcRow>):OperationResult=withContext(Dispatchers.IO){
        runCatching{
            require(rows.isNotEmpty()){ "没有可提交的包装。" }
            var committedCount = 0
            var skippedCount = 0
            databaseHelper.writableDatabase.use{db->db.beginTransaction();try{
                val already=db.rawQuery("SELECT row_id FROM batch_jlc_receipts WHERE session_id = ?",arrayOf(sessionId)).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                skippedCount = rows.count { it.selected && it.status == BatchJlcStatus.Ready && it.id in already }
                val parsed=BatchJlcCommitPlanner.plan(rows,already);val now=utcNow()
                parsed.forEach{item->val row=item.row;val sku=item.sku;val quantity=item.quantity
                    val component=db.rawQuery("SELECT id,quantity FROM components WHERE UPPER(sku)=? AND deleted=0 LIMIT 2",arrayOf(sku)).use{c->if(!c.moveToFirst())null else {(c.getString(0) to c.getInt(1)).also{check(!c.moveToNext()){ "存在多个大小写不同但料号相同的活跃元器件，请先合并冲突记录。" }}}}
                    val componentId=component?.first?:"cmp-"+randomId();val oldTotal=component?.second?:0
                    val activeLocation=db.rawQuery("SELECT 1 FROM storage_locations WHERE id=? AND deleted=0",arrayOf(row.location.trim())).use{it.moveToFirst()};check(activeLocation){"库位 ${row.location} 不存在或已删除。"}
                    if(component==null){require(row.name.isNotBlank()&&row.category.isNotBlank()&&row.packageName.isNotBlank()){"新料号 $sku 缺少名称、分类或封装。"};db.insertOrThrow("components",null,ContentValues().apply{put("id",componentId);put("sku",sku);put("name",row.name.trim());put("category",row.category.trim());put("package_name",row.packageName.trim());put("location",row.location.trim());put("description",row.description.trim());put("quantity",0);put("min_stock",0);put("updated_at",now);putNull("base_updated_at")});setAllocationQuantity(db,componentId,row.location.trim(),0)}
                    val oldAllocation=allocationQuantity(db,componentId,row.location.trim());val newTotal=Math.addExact(oldTotal,quantity);val newAllocation=Math.addExact(oldAllocation,quantity)
                    db.update("components",ContentValues().apply{put("quantity",newTotal);put("location",row.location.trim());put("updated_at",now)},"id=?",arrayOf(componentId));setAllocationQuantity(db,componentId,row.location.trim(),newAllocation);verifyAllocationTotal(db,componentId,newTotal)
                    val movementId="mov-"+randomId();db.insertOrThrow("stock_movements",null,ContentValues().apply{put("id",movementId);put("component_id",componentId);put("movement_type","inbound");put("quantity",quantity);put("reason","JLC batch inbound");put("note","");put("happened_at",now);put("updated_at",now);put("deleted",0);put("location_id",row.location.trim())})
                    enqueueEntity(db,"component",componentId,now);enqueueEntity(db,"stock_movement",movementId,now);db.insertOrThrow("batch_jlc_receipts",null,ContentValues().apply{put("row_id",row.id);put("session_id",sessionId);put("component_id",componentId);put("movement_id",movementId);put("committed_at",now)})
                };committedCount = parsed.size;db.setTransactionSuccessful()
            }finally{db.endTransaction()}}
            OperationResult(true,"已批量入库 $committedCount 个包装；跳过已提交 $skippedCount 个。")
        }.getOrElse{OperationResult(false,it.message?:"批量入库失败。")}
    }

    internal suspend fun exportInventoryWorkbook():ByteArray=withContext(Dispatchers.IO){
        databaseHelper.readableDatabase.use{db->
            db.beginTransaction()
            try {
                val snapshot=readWorkbookSnapshot(db)
                val bytes=InventoryWorkbookWriter.write(snapshot.copy(images=snapshot.components.mapNotNull{c->inventoryImageStore.resolve(c.description)?.let{c.id to it}}.toMap()))
                InventoryWorkbookCodec.parse(bytes)
                bytes
            } finally { db.endTransaction() }
        }
    }

    internal suspend fun previewInventoryWorkbook(bytes:ByteArray):WorkbookPreview=withContext(Dispatchers.IO){
        val workbook=InventoryWorkbookCodec.parse(bytes)
        databaseHelper.readableDatabase.use{db->
            db.beginTransaction()
            try {
                val ids=db.rawQuery("SELECT id FROM components",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val skus=db.rawQuery("SELECT UPPER(sku) FROM components",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val locations=db.rawQuery("SELECT id FROM storage_locations",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val movementIds=db.rawQuery("SELECT id FROM stock_movements",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val conflictingMovementComponents=workbook.movements.filter{it.id in movementIds}.map{it.componentId}.toSet()
                val fresh=workbook.components.filter{it.id !in ids && it.sku.uppercase() !in skus && it.id !in conflictingMovementComponents}
                val warnings=buildList{addAll(workbook.warnings);if(conflictingMovementComponents.isNotEmpty())add("部分流水 ID 已存在；对应组件将按新记录合并规则跳过。")}
                WorkbookPreview(workbook,fresh.size,workbook.components.size-fresh.size,workbook.locations.count{it.id !in locations},warnings)
            } finally { db.endTransaction() }
        }
    }

    internal suspend fun restoreInventoryWorkbook(preview:WorkbookPreview):OperationResult=withContext(Dispatchers.IO){
        val createdImages=mutableListOf<String>()
        runCatching{
            databaseHelper.writableDatabase.use{db->db.beginTransaction();try{
                check(!db.rawQuery("SELECT 1 FROM inventory_backup_imports WHERE fingerprint = ?",arrayOf(preview.workbook.fingerprint)).use{it.moveToFirst()}){"该备份已经导入。"}
                val ids=db.rawQuery("SELECT id FROM components",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val skus=db.rawQuery("SELECT UPPER(sku) FROM components",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val movementIds=db.rawQuery("SELECT id FROM stock_movements",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                val conflictingMovementComponents=preview.workbook.movements.filter{it.id in movementIds}.map{it.componentId}.toSet()
                val selected=preview.workbook.components.filter{it.id !in ids&&it.sku.uppercase() !in skus&&it.id !in conflictingMovementComponents}.map{c->preview.workbook.images[c.id]?.let{image->val marker=WorkbookImageCodec.marker(image);if(!inventoryImageStore.exists(marker))createdImages+=marker;inventoryImageStore.persist(image);c.copy(description=c.description.lineSequence().filterNot{it.startsWith("本地图片：")}.joinToString("\n").trim().let{base->listOf(base,marker).filter{it.isNotBlank()}.joinToString("\n")})}?:c}
                val selectedIds=selected.map{it.id}.toSet();val now=utcNow()
                val deletedLocations=db.rawQuery("SELECT id FROM storage_locations WHERE deleted = 1",null).use{c->buildSet{while(c.moveToNext())add(c.getString(0))}}
                check(preview.workbook.allocations.none{it.componentId in selectedIds&&it.quantity>0&&it.locationId in deletedLocations}){"导入的正库存不能分配到本地已删除库位。"}
                preview.workbook.locations.forEach{l->db.insertWithOnConflict("storage_locations",null,ContentValues().apply{put("id",l.id);put("name",l.name);put("updated_at",l.updatedAt);put("deleted",if(l.deleted)1 else 0)},SQLiteDatabase.CONFLICT_IGNORE)}
                selected.forEach{c->db.insertOrThrow("components",null,ContentValues().apply{put("id",c.id);put("sku",c.sku);put("name",c.name);put("category",c.category);put("package_name",c.packageName);put("location",c.location);put("description",c.description);put("quantity",c.quantity);put("min_stock",c.minStock);put("updated_at",c.updatedAt);put("deleted",if(c.deleted)1 else 0);put("base_updated_at",c.baseUpdatedAt)})}
                preview.workbook.allocations.filter{it.componentId in selectedIds}.forEach{a->setAllocationQuantity(db,a.componentId,a.locationId,a.quantity)}
                if(preview.workbook.source==WorkbookSource.ComponentVault){
                    preview.workbook.movements.filter{it.componentId in selectedIds}.forEach{m->db.insertOrThrow("stock_movements",null,ContentValues().apply{put("id",m.id);put("component_id",m.componentId);put("movement_type",m.type);put("quantity",m.quantity);put("reason",m.reason);put("note",m.note);put("happened_at",m.happenedAt);put("updated_at",m.updatedAt);put("deleted",if(m.deleted)1 else 0);put("location_id",m.locationId);put("destination_location_id",m.destinationLocationId)});enqueueEntity(db,"stock_movement",m.id,m.updatedAt)}
                }else selected.filter{it.quantity>0}.forEach{c->preview.workbook.allocations.filter{it.componentId==c.id&&it.quantity>0}.forEach{a->val id="mov-"+randomId();db.insertOrThrow("stock_movements",null,ContentValues().apply{put("id",id);put("component_id",c.id);put("movement_type","inbound");put("quantity",a.quantity);put("reason","LCSC schema1 initial inventory");put("note","Imported source fingerprint "+preview.workbook.fingerprint);put("happened_at",now);put("updated_at",now);put("deleted",0);put("location_id",a.locationId)});enqueueEntity(db,"stock_movement",id,now)}}
                selected.forEach{c->verifyAllocationTotal(db,c.id,c.quantity);enqueueEntity(db,"component",c.id,c.updatedAt)}
                db.insertOrThrow("inventory_backup_imports",null,ContentValues().apply{put("fingerprint",preview.workbook.fingerprint);put("source",preview.workbook.source.name);put("imported_at",now)})
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}}
            OperationResult(true,"已新增导入 "+preview.newComponentCount+" 个元器件；跳过 "+preview.skippedComponentCount+" 个现有记录。")
        }.getOrElse{createdImages.forEach(inventoryImageStore::delete);OperationResult(false,it.message?:"库存恢复失败。")}
    }

    private fun readWorkbookSnapshot(db:SQLiteDatabase):InventoryWorkbook {
        val components=db.rawQuery("SELECT id,sku,name,category,package_name,location,COALESCE(description,''),quantity,min_stock,updated_at,deleted,base_updated_at FROM components ORDER BY id",null).use{c->buildList{while(c.moveToNext())add(WorkbookComponent(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getInt(7),c.getInt(8),c.getString(9),c.getInt(10)==1,c.getString(11)))}}
        val locations=db.rawQuery("SELECT id,name,updated_at,deleted FROM storage_locations ORDER BY id",null).use{c->buildList{while(c.moveToNext())add(WorkbookLocation(c.getString(0),c.getString(1),c.getString(2),c.getInt(3)==1))}}
        val allocations=db.rawQuery("SELECT component_id,location_id,quantity FROM component_allocations ORDER BY component_id,location_id",null).use{c->buildList{while(c.moveToNext())add(WorkbookAllocation(c.getString(0),c.getString(1),c.getInt(2)))}}
        val movements=db.rawQuery("SELECT id,component_id,movement_type,quantity,reason,COALESCE(note,''),happened_at,updated_at,deleted,location_id,destination_location_id FROM stock_movements ORDER BY id",null).use{c->buildList{while(c.moveToNext())add(WorkbookMovement(c.getString(0),c.getString(1),c.getString(2),c.getInt(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),c.getInt(8)==1,c.getString(9),c.getString(10)))}}
        return InventoryWorkbook(WorkbookSource.ComponentVault,components,locations,allocations,movements,"",emptyList())
    }

    suspend fun loadAllocations(componentId: String? = null): List<ComponentAllocationRecord> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """SELECT a.component_id, a.location_id, COALESCE(l.name, a.location_id), a.quantity
                   FROM component_allocations a LEFT JOIN storage_locations l ON l.id = a.location_id
                   WHERE (? = '' OR a.component_id = ?) ORDER BY a.quantity DESC, a.location_id""",
                arrayOf(componentId.orEmpty(), componentId.orEmpty()),
            ).use { cursor -> buildList {
                while (cursor.moveToNext()) add(ComponentAllocationRecord(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3),
                ))
            } }
        }
    }

    suspend fun saveStorageLocation(
        id: String,
        name: String,
        intent: StorageLocationSaveIntent,
    ): OperationResult = withContext(Dispatchers.IO) {
        runCatching {
            val code = id.trim()
            val displayName = name.trim()
            require(code.isNotEmpty() && code.length <= 120 && displayName.isNotEmpty() && displayName.length <= 200)
            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    val existingDeleted = db.rawQuery(
                        "SELECT deleted FROM storage_locations WHERE id = ?",
                        arrayOf(code),
                    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) != 0 else null }
                    val now = utcNow()
                    when (intent) {
                        StorageLocationSaveIntent.Create -> {
                            check(existingDeleted == null) { "库位编号已存在。" }
                            val inserted = db.insertOrThrow("storage_locations", null, ContentValues().apply {
                                put("id", code)
                                put("name", displayName)
                                put("updated_at", now)
                                put("deleted", 0)
                            })
                            check(inserted != -1L) { "库位新增失败。" }
                        }
                        StorageLocationSaveIntent.Edit -> {
                            check(existingDeleted == false) { "库位不存在或已删除。" }
                            val updated = db.update("storage_locations", ContentValues().apply {
                                put("name", displayName)
                                put("updated_at", now)
                            }, "id = ? AND deleted = 0", arrayOf(code))
                            check(updated == 1) { "库位不存在或已删除。" }
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            OperationResult(
                true,
                if (intent == StorageLocationSaveIntent.Create) "库位已新增。" else "库位已更新。",
                code,
            )
        }.getOrElse { OperationResult(false, it.message ?: "库位保存失败。") }
    }

    suspend fun deleteStorageLocation(id: String): OperationResult = withContext(Dispatchers.IO) {
        runCatching {
            databaseHelper.writableDatabase.use { db ->
                val occupied = db.rawQuery(
                    """SELECT 1 FROM component_allocations a JOIN components c ON c.id=a.component_id
                       WHERE a.location_id = ? AND a.quantity > 0 AND c.deleted = 0 LIMIT 1""",
                    arrayOf(id),
                ).use { it.moveToFirst() }
                check(!occupied) { "该库位仍有库存，不能删除。" }
                val count = db.update("storage_locations", ContentValues().apply {
                    put("deleted", 1); put("updated_at", utcNow())
                }, "id = ? AND deleted = 0", arrayOf(id))
                check(count == 1) { "库位不存在。" }
            }
            OperationResult(true, "空库位已删除。", id)
        }.getOrElse { OperationResult(false, it.message ?: "库位删除失败。") }
    }

    suspend fun loadIssuedQuantities(): Map<String, Long> = withContext(Dispatchers.IO) {
        databaseHelper.readableDatabase.use { db ->
            db.rawQuery(
                """
                SELECT component_id, COALESCE(SUM(ABS(CAST(quantity AS INTEGER))), 0) AS issued_quantity
                FROM stock_movements
                WHERE deleted = 0
                  AND LOWER(TRIM(movement_type)) = 'outbound'
                GROUP BY component_id
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildMap {
                    val componentIdIndex = cursor.getColumnIndexOrThrow("component_id")
                    val issuedQuantityIndex = cursor.getColumnIndexOrThrow("issued_quantity")
                    while (cursor.moveToNext()) {
                        put(cursor.getString(componentIdIndex), cursor.getLong(issuedQuantityIndex))
                    }
                }
            }
        }
    }

    suspend fun recordMovementsBatch(drafts: List<MovementEntryDraft>): OperationResult = withContext(Dispatchers.IO) {
        try {
            if (drafts.isEmpty()) {
                throw IllegalStateException(text(R.string.sync_movement_batch_empty))
            }
            drafts.forEach(::validateMovementDraft)

            databaseHelper.writableDatabase.use { db ->
                db.beginTransaction()
                try {
                    applyMovementDrafts(db, drafts)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            OperationResult(
                isSuccess = true,
                message = text(R.string.sync_movement_batch_recorded_local, drafts.size),
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_movement_record_failed),
            )
        }
    }

    suspend fun testConnection(
        serverBaseUrl: String,
        apiToken: String,
        externalServerBaseUrl: String = "",
    ): OperationResult = withContext(Dispatchers.IO) {
        val settings = loadSyncConfiguration().copy(
            serverBaseUrl = serverBaseUrl.trim().trimEnd('/'),
            externalServerBaseUrl = externalServerBaseUrl.trim().trimEnd('/'),
            apiToken = apiToken.trim(),
        )
        if (settings.serverBaseUrl.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_server_url_first))
        }
        if (settings.apiToken.isBlank()) {
            return@withContext OperationResult(false, text(R.string.sync_enter_api_token_first))
        }

        return@withContext try {
            val endpoint = resolveSyncEndpoint(settings)
            OperationResult(
                isSuccess = true,
                message = text(R.string.sync_connection_ok, endpoint.probe.optString("server_time")) +
                    if (endpoint.usesExternalAddress) " " + text(R.string.sync_via_external) else "",
            )
        } catch (exception: Exception) {
            OperationResult(
                isSuccess = false,
                message = exception.message ?: text(R.string.sync_connection_failed),
            )
        }
    }

    suspend fun runSync(): OperationResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = loadSyncConfiguration()
            if (settings.serverBaseUrl.isBlank()) {
                return@withContext OperationResult(
                    false,
                    text(R.string.sync_enter_server_url_before_sync),
                )
            }
            if (settings.apiToken.isBlank()) {
                return@withContext OperationResult(
                    false,
                    text(R.string.sync_enter_api_token_before_sync),
                )
            }

            try {
                val endpoint = resolveSyncEndpoint(settings)
                val capability = endpoint.probe
                check(capability.optInt("inventory_protocol", 0) == 1) {
                    "The server does not support inventory_protocol=1. Local changes were kept for retry."
                }
                val identity = callJson(endpoint.configuration, "GET", "/auth/me", null)
                val serverId = identity.optString("server_id").trim()
                val accountId = identity.optString("account_id").trim()
                check(serverId.isNotBlank() && accountId.isNotBlank()) {
                    text(R.string.sync_identity_unavailable)
                }
                synchronized(syncConfigurationLock) {
                    checkSyncConfigurationUnchanged(settings)
                    val boundServer = preferences.getString(KEY_BOUND_SERVER_ID, "").orEmpty()
                    val boundAccount = preferences.getString(KEY_BOUND_ACCOUNT_ID, "").orEmpty()
                    check(boundServer.isBlank() && boundAccount.isBlank() ||
                        boundServer == serverId && boundAccount == accountId) {
                        text(R.string.sync_account_mismatch)
                    }
                    if (boundServer.isBlank() && boundAccount.isBlank()) {
                        val priorSync = readStoredSyncCursor() != null ||
                            preferences.getString(KEY_LAST_SYNCED_AT, "").orEmpty()
                                .matches(Regex("^\\d{4}-\\d{2}-\\d{2}T.*")) ||
                            databaseHelper.readableDatabase.use { db ->
                                db.rawQuery(
                                    "SELECT 1 FROM components WHERE base_updated_at IS NOT NULL LIMIT 1",
                                    null,
                                ).use { it.moveToFirst() }
                            }
                        check(!priorSync || identity.optString("role") == "admin") {
                            text(R.string.sync_legacy_admin_only)
                        }
                        check(preferences.edit()
                            .putString(KEY_BOUND_SERVER_ID, serverId)
                            .putString(KEY_BOUND_ACCOUNT_ID, accountId)
                            .commit()) { text(R.string.sync_identity_unavailable) }
                    }
                }
                val pushPayload = buildPushPayload(settings.deviceId)
                val pushResponse = callJson(
                    settings = endpoint.configuration,
                    method = "POST",
                    path = "/sync/push",
                    body = pushPayload.payload,
                    accountId = accountId,
                )
                val cursor = readStoredSyncCursor()
                val pullResponse = callJson(
                    settings = endpoint.configuration,
                    method = "GET",
                    path = SyncProtocol.pullPath(cursor),
                    body = null,
                    accountId = accountId,
                )
                val rawCursor = pullResponse.opt("sync_cursor")
                val cursorDecision = SyncProtocol.cursorFromResponse(
                    hasCursor = pullResponse.has("sync_cursor"),
                    cursor = when (rawCursor) {
                        is Int -> rawCursor.toLong()
                        is Long -> rawCursor
                        else -> null
                    },
                )

                synchronized(syncConfigurationLock) {
                    checkSyncConfigurationUnchanged(settings)
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
                    saveSyncCursor(cursorDecision)
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
                ) + if (endpoint.usesExternalAddress) " " + text(R.string.sync_via_external) else ""

                synchronized(syncConfigurationLock) {
                    checkSyncConfigurationUnchanged(settings)
                    updateSyncStatus(
                        lastSyncedAt = serverTime,
                        message = successMessage,
                        preserveTimestamp = false,
                    )
                }

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
    }

    suspend fun previewBomRelease(
        parsed: com.componentvault.android.data.bom.BomParseResult,
        selections: Map<String, String> = emptyMap(),
        searchQueries: Map<String, String> = emptyMap(),
    ): com.componentvault.android.data.bom.BomReleasePreview = withContext(Dispatchers.IO) {
        val components = loadComponents()
        val inventory = components.map { component ->
            val parsedDescription = parseImportDescription(component.description)
            com.componentvault.android.data.bom.InventoryMatchCandidate(
                inventoryId = component.id,
                sku = component.sku,
                model = parsedDescription.model,
                packageName = component.packageName,
                displayName = component.name,
                active = !component.deleted,
            )
        }
        val matches = com.componentvault.android.data.bom.BomInventoryMatcher.match(
            parsed.requirements,
            inventory.filter { it.active },
        )
        val componentsById = components.associateBy { it.id }
        val rawLines = matches.map { match ->
            val requirementKey = match.requirement.identity.canonicalKey
            val selectedId = selections[requirementKey]
                ?: match.selectedInventoryId
            val component = selectedId?.let(componentsById::get)
                ?.takeUnless { it.deleted }
            val searchedCandidates = searchQueries[requirementKey]
                ?.let { query ->
                    com.componentvault.android.data.bom.BomInventoryMatcher.search(inventory, query)
                }.orEmpty()
            val selectedCandidate = component?.let {
                inventory.firstOrNull { candidate -> candidate.inventoryId == it.id }
            }
            com.componentvault.android.data.bom.BomReleaseLine(
                requirement = match.requirement,
                componentId = component?.id,
                componentSku = component?.sku,
                availableQuantity = component?.quantity ?: 0,
                expectedUpdatedAt = component?.updatedAt,
                candidates = (match.candidates + searchedCandidates + listOfNotNull(selectedCandidate))
                    .distinctBy { it.inventoryId },
            )
        }
        val aggregated = com.componentvault.android.data.bom.BomReleaseAggregator.aggregate(rawLines)
        val allocations = loadAllocations().groupBy { it.componentId }
        com.componentvault.android.data.bom.BomReleasePreview(
            parsed = parsed,
            lines = aggregated.map { line ->
                val componentId = line.componentId ?: return@map line
                line.copy(allocationPlan = runCatching {
                    com.componentvault.android.data.bom.BomAllocationPlanner.plan(
                        line.requirement.requiredQuantity,
                        allocations[componentId].orEmpty().map { it.locationId to it.quantity },
                    )
                }.getOrDefault(emptyList()))
            },
            matchingLines = rawLines,
        )
    }

    suspend fun commitBomRelease(
        preview: com.componentvault.android.data.bom.BomReleasePreview,
        releaseId: String,
        batchId: String,
    ): com.componentvault.android.data.bom.BomReleaseResult = withContext(Dispatchers.IO) {
        if (releaseId.isBlank() || batchId.isBlank() || !preview.canCommit) {
            return@withContext com.componentvault.android.data.bom.BomReleaseResult(
                com.componentvault.android.data.bom.BomReleaseOutcome.REJECTED,
                "BOM 预览不完整，请先解决未匹配项和缺料问题。",
            )
        }
        databaseHelper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                val alreadyApplied = db.rawQuery(
                    "SELECT 1 FROM bom_releases WHERE release_id = ?",
                    arrayOf(releaseId),
                ).use { it.moveToFirst() }
                if (alreadyApplied) {
                    return@withContext com.componentvault.android.data.bom.BomReleaseResult(
                        com.componentvault.android.data.bom.BomReleaseOutcome.ALREADY_APPLIED,
                        "本批次已出库，没有重复扣减库存。",
                    )
                }
                check(preview.lines.mapNotNull { it.componentId }.distinct().size == preview.lines.size) {
                    "BOM 预览包含重复元件，请重新生成预览。"
                }

                val currentRows = preview.lines.map { line ->
                    val componentId = requireNotNull(line.componentId)
                    db.rawQuery(
                        "SELECT sku, quantity, updated_at FROM components " +
                            "WHERE id = ? AND deleted = 0",
                        arrayOf(componentId),
                    ).use { cursor ->
                        check(cursor.moveToFirst()) { "选中的元器件已不存在，请刷新预览。" }
                        val currentUpdatedAt = cursor.getString(2)
                        check(
                            line.expectedUpdatedAt != null &&
                                SyncProtocol.timestampsEqual(currentUpdatedAt, line.expectedUpdatedAt),
                        ) {
                            "预览后库存已变化，请重新生成 BOM 预览。"
                        }
                        val available = cursor.getInt(1)
                        check(available >= line.requirement.requiredQuantity) {
                            "${cursor.getString(0)} 库存不足，整批尚未出库。"
                        }
                        Triple(componentId, available, cursor.getString(0))
                    }
                }

                val now = utcNow()
                preview.lines.zip(currentRows).forEach { (line, current) ->
                    val required = line.requirement.requiredQuantity
                    val currentPlan = allocationPlan(db, current.first, required)
                    check(currentPlan == line.allocationPlan.map { it.locationId to it.quantity }) {
                        "预览后库位分配已变化，请重新生成 BOM 预览。"
                    }
                    val consumed = consumeAllocations(db, current.first, required)
                    val values = ContentValues().apply {
                        put("quantity", current.second - required)
                        put("updated_at", now)
                    }
                    db.update("components", values, "id = ?", arrayOf(current.first))
                    enqueueEntity(db, "component", current.first, now)
                    consumed.forEach { (locationId, consumedQuantity) ->
                        val movementId = "mov-${randomId()}"
                        db.insertOrThrow(
                            "stock_movements", null, ContentValues().apply {
                                put("id", movementId)
                                put("component_id", current.first)
                                put("movement_type", "outbound")
                                put("quantity", consumedQuantity)
                                put("reason", "BOM: ${preview.parsed.projectName}")
                                put("note", "Batch $batchId; release $releaseId")
                                put("happened_at", now)
                                put("updated_at", now)
                                put("deleted", 0)
                                put("location_id", locationId)
                            },
                        )
                        enqueueEntity(db, "stock_movement", movementId, now)
                    }
                    verifyAllocationTotal(db, current.first, current.second - required)
                }
                db.insertOrThrow(
                    "bom_releases",
                    null,
                    ContentValues().apply {
                        put("release_id", releaseId)
                        put("batch_id", batchId)
                        put("project_name", preview.parsed.projectName)
                        put("production_runs", preview.parsed.productionSets)
                        put("source_fingerprint", preview.parsed.fileSha256)
                        put("source_sheet", preview.parsed.selectedSheet.name)
                        put("created_at", now)
                    },
                )
                db.setTransactionSuccessful()
                com.componentvault.android.data.bom.BomReleaseResult(
                    com.componentvault.android.data.bom.BomReleaseOutcome.APPLIED,
                    "BOM 批量出库已完成。",
                )
            } catch (error: Exception) {
                com.componentvault.android.data.bom.BomReleaseResult(
                    com.componentvault.android.data.bom.BomReleaseOutcome.REJECTED,
                    error.message ?: "BOM 出库失败，整批已回滚。",
                )
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun previewComponentHub(
        bytes: ByteArray,
        duplicatePolicy: com.componentvault.android.data.bom.ComponentHubDuplicatePolicy,
    ): com.componentvault.android.data.bom.ComponentHubParseResult = withContext(Dispatchers.IO) {
        val existingSkus = loadComponents().asSequence()
            .filterNot { it.deleted }
            .map { it.sku }
            .toSet()
        com.componentvault.android.data.bom.ComponentHubParser.parse(
            bytes = bytes,
            duplicatePolicy = duplicatePolicy,
            existingSkus = existingSkus,
        )
    }

    suspend fun importComponentHub(
        preview: com.componentvault.android.data.bom.ComponentHubParseResult,
    ): com.componentvault.android.data.bom.ComponentHubImportResult = withContext(Dispatchers.IO) {
        if (!preview.canConfirm) {
            return@withContext com.componentvault.android.data.bom.ComponentHubImportResult(
                com.componentvault.android.data.bom.ComponentHubImportOutcome.REJECTED,
                "迁移预览仍有冲突或无效记录，请先处理。",
            )
        }
        databaseHelper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                val alreadyImported = db.rawQuery(
                    "SELECT 1 FROM component_hub_imports WHERE source_fingerprint = ?",
                    arrayOf(preview.fileSha256),
                ).use { it.moveToFirst() }
                if (alreadyImported) {
                    return@withContext com.componentvault.android.data.bom.ComponentHubImportResult(
                        com.componentvault.android.data.bom.ComponentHubImportOutcome.ALREADY_APPLIED,
                        "此 Component Hub 文件已经导入，未重复创建库存。",
                    )
                }
                val existing = db.rawQuery(
                    "SELECT sku FROM components WHERE deleted = 0",
                    null,
                ).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.getString(0).trim().uppercase()) }
                }
                check(preview.components.none { it.sku.trim().uppercase() in existing }) {
                    "预览后库存已变化，请重新核对重复 SKU。"
                }

                val now = utcNow()
                preview.components.forEach { source ->
                    val componentId = "cmp-${randomId()}"
                    db.insertOrThrow(
                        "components",
                        null,
                        ContentValues().apply {
                            put("id", componentId)
                            put("sku", source.sku.trim())
                            put("name", source.name.trim())
                            put("category", source.category.trim())
                            put("package_name", source.packageName.trim())
                            put("location", source.location.trim())
                            put("description", source.notes.joinToString("\n"))
                            put("quantity", source.quantity)
                            put("min_stock", source.minStock)
                            put("updated_at", now)
                            put("deleted", 0)
                        },
                    )
                    ensureStorageLocation(db, source.location.trim(), now)
                    setAllocationQuantity(db, componentId, source.location.trim(), source.quantity)
                    verifyAllocationTotal(db, componentId, source.quantity)
                    enqueueEntity(db, "component", componentId, now)
                    if (source.quantity > 0) {
                        val movementId = "mov-${randomId()}"
                        db.insertOrThrow(
                            "stock_movements",
                            null,
                            ContentValues().apply {
                                put("id", movementId)
                                put("component_id", componentId)
                                put("movement_type", "inbound")
                                put("quantity", source.quantity)
                                put("reason", "Component Hub import")
                                put("note", "Source ${preview.fileSha256}")
                                put("happened_at", now)
                                put("updated_at", now)
                                put("deleted", 0)
                                put("location_id", source.location.trim())
                            },
                        )
                        enqueueEntity(db, "stock_movement", movementId, now)
                    }
                }
                db.insertOrThrow(
                    "component_hub_imports",
                    null,
                    ContentValues().apply {
                        put("source_fingerprint", preview.fileSha256)
                        put("imported_count", preview.components.size)
                        put("skipped_count", preview.skippedDuplicateCount)
                        put("created_at", now)
                    },
                )
                db.setTransactionSuccessful()
                com.componentvault.android.data.bom.ComponentHubImportResult(
                    com.componentvault.android.data.bom.ComponentHubImportOutcome.APPLIED,
                    "Component Hub 迁移已完成。",
                    preview.components.size,
                    preview.skippedDuplicateCount,
                )
            } catch (error: Exception) {
                com.componentvault.android.data.bom.ComponentHubImportResult(
                    com.componentvault.android.data.bom.ComponentHubImportOutcome.REJECTED,
                    error.message ?: "Component Hub 迁移失败，整批已回滚。",
                )
            } finally {
                db.endTransaction()
            }
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
                locationId = cursor.getString(cursor.getColumnIndexOrThrow("location_id")),
                destinationLocationId = cursor.getString(cursor.getColumnIndexOrThrow("destination_location_id")),
            )
        }
        return items
    }

    private fun findActiveComponentsBySku(
        db: SQLiteDatabase,
        sku: String,
    ): List<ComponentRecord> {
        return db.rawQuery(
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
            WHERE deleted = 0 AND sku = ?
            ORDER BY updated_at DESC, name COLLATE NOCASE ASC
            """.trimIndent(),
            arrayOf(sku.trim()),
        ).use(::readComponents)
    }

    private fun extractWarehouseLocation(candidate: ComponentImportCandidate): String {
        return candidate.notes.firstOrNull {
            it.startsWith("Warehouse location: ") || it.startsWith("仓位：")
        }
            ?.removePrefix("Warehouse location: ")
            ?.removePrefix("仓位：")
            ?.trim()
            .orEmpty()
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
        if (!preferences.contains(KEY_OCR_ENGINE_MODE)) {
            editor.putString(KEY_OCR_ENGINE_MODE, OcrEngineMode.Auto.storageValue)
            changed = true
        }
        if (!preferences.contains(KEY_APP_LANGUAGE)) {
            editor.putString(KEY_APP_LANGUAGE, AppLanguage.ZhCn.storageValue)
            changed = true
        }
        if (changed) {
            editor.apply()
        }
    }

    internal fun readCachedLookup(
        sku: String,
        mpn: String,
        cacheScope: String? = null,
    ): ComponentOfficialMetadata? {
        val now = System.currentTimeMillis()
        val cacheKeys = buildList {
            if (sku.isNotBlank()) {
                add("${LOOKUP_CACHE_PREFIX}${cacheScope?.let { "$it:" }.orEmpty()}sku:${sku.lowercase(Locale.US)}")
            }
            if (mpn.isNotBlank()) {
                add("${LOOKUP_CACHE_PREFIX}${cacheScope?.let { "$it:" }.orEmpty()}mpn:${mpn.lowercase(Locale.US)}")
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
                description = payload.optString("description").blankToNull(),
                packageName = payload.optString("package_name").blankToNull(),
                category = payload.optString("category").blankToNull(),
                model = payload.optString("model").blankToNull(),
                brand = payload.optString("brand").blankToNull(),
                vendor = payload.optString("vendor").blankToNull(),
                modelFamily = payload.optString("model_family").blankToNull(),
                categoryPath = payload.optString("category_path").blankToNull(),
                officialUrl = payload.optString("official_url").blankToNull(),
                imageUrl = payload.optString("image_url").blankToNull(),
                matchedBy = payload.optString("matched_by").blankToNull(),
                confidence = payload.optString("confidence").blankToNull(),
                ruleVersion = payload.optString("rule_version").blankToNull(),
                parameters = payload.optJSONObject("parameters")?.let { json ->
                    buildMap {
                        json.keys().forEach { key ->
                            json.optString(key).blankToNull()?.let { value -> put(key, value) }
                        }
                    }
                }.orEmpty(),
                datasheetUrl = payload.optString("datasheet_url").blankToNull(),
            )
        }

        return null
    }

    internal fun cacheLookup(metadata: ComponentOfficialMetadata, cacheScope: String? = null) {
        val payload = JSONObject().apply {
            put("source", metadata.source)
            put("sku", metadata.sku)
            put("name", metadata.name)
            put("description", metadata.description)
            put("package_name", metadata.packageName)
            put("category", metadata.category)
            put("model", metadata.model)
            put("brand", metadata.brand)
            put("vendor", metadata.vendor)
            put("model_family", metadata.modelFamily)
            put("category_path", metadata.categoryPath)
            put("official_url", metadata.officialUrl)
            put("image_url", metadata.imageUrl)
            put("matched_by", metadata.matchedBy)
            put("confidence", metadata.confidence)
            put("rule_version", metadata.ruleVersion)
            put("parameters", JSONObject(metadata.parameters))
            put("datasheet_url", metadata.datasheetUrl)
            put("fetched_at", System.currentTimeMillis())
        }.toString()

        val editor = preferences.edit()
        metadata.sku?.takeIf { it.isNotBlank() }?.let { value ->
            editor.putString("${LOOKUP_CACHE_PREFIX}${cacheScope?.let { "$it:" }.orEmpty()}sku:${value.lowercase(Locale.US)}", payload)
        }
        metadata.model?.takeIf { it.isNotBlank() }?.let { value ->
            editor.putString("${LOOKUP_CACHE_PREFIX}${cacheScope?.let { "$it:" }.orEmpty()}mpn:${value.lowercase(Locale.US)}", payload)
        }
        editor.apply()
    }

    internal fun clearLookupCache() {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(LOOKUP_CACHE_PREFIX) }.forEach(editor::remove)
        editor.apply()
    }

    private fun cleanupLookupCache() {
        val now = System.currentTimeMillis()
        val scopedKey = Regex(
            "^${Regex.escape(LOOKUP_CACHE_PREFIX)}(?:lcsc_domestic_web|lcsc_public_web|$SERVER_LOOKUP_CACHE_SCOPE):(sku|mpn):.+$",
        )
        val editor = preferences.edit()
        var changed = false
        preferences.all.forEach { (key, value) ->
            if (!key.startsWith(LOOKUP_CACHE_PREFIX)) return@forEach
            val payload = (value as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val fetchedAt = payload?.optLong("fetched_at", 0L) ?: 0L
            if (!scopedKey.matches(key) || fetchedAt <= 0L || now - fetchedAt !in 0..LOOKUP_CACHE_MAX_AGE_MS) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
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
    ): String {
        ensureUniqueActiveSku(db, draft.sku.trim(), draft.id)
        val updatedAt = utcNow()
        val componentId = draft.id ?: "cmp-${randomId()}"
        val oldTotal = draft.id?.let { getComponentById(db, it)?.quantity } ?: 0
        val baseUpdatedAt = draft.id?.let { id ->
            db.rawQuery("SELECT base_updated_at FROM components WHERE id = ?", arrayOf(id)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
        ensureStorageLocation(db, draft.location.trim(), updatedAt)

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
            put("base_updated_at", baseUpdatedAt)
        }
        db.insertWithOnConflict(
            "components",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        val locationId = draft.location.trim()
        val existingAtLocation = allocationQuantity(db, componentId, locationId)
        val adjustedAtLocation = Math.addExact(existingAtLocation, draft.quantity - oldTotal)
        check(adjustedAtLocation >= 0) { "The edited total would make the selected location negative." }
        db.insertWithOnConflict(
            "component_allocations",
            null,
            ContentValues().apply {
                put("component_id", componentId)
                put("location_id", locationId)
                put("quantity", adjustedAtLocation)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        verifyAllocationTotal(db, componentId, draft.quantity)
        enqueueEntity(db, "component", componentId, updatedAt)
        return componentId
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
        val resolvedModel = parsedDescription.model?.trim()?.blankToNull()
            ?: sourceCandidate.model?.trim()?.blankToNull()
        val resolvedBrand = parsedDescription.brand?.trim()?.blankToNull()
            ?: sourceCandidate.brand?.trim()?.blankToNull()
        val resolvedName = draft.name.trim().blankToNull()?.takeUnless {
            it.isLikelyModelLike(
                sku = normalizedSourceSku.orEmpty(),
                model = resolvedModel,
            )
        }

        val values = ContentValues().apply {
            put("id", mappingId)
            put("source_type", sourceCandidate.sourceType.toStorageValue())
            put("source_sku", normalizedSourceSku)
            put("source_mpn", normalizedSourceMpn)
            put("resolved_name", resolvedName)
            put("resolved_category", draft.category.trim().blankToNull())
            put("resolved_package_name", draft.packageName.trim().blankToNull())
            put("resolved_model", resolvedModel)
            put("resolved_brand", resolvedBrand)
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
        if (draft.movementType == "transfer") {
            if (draft.quantity <= 0 || draft.locationId.isNullOrBlank() ||
                draft.destinationLocationId.isNullOrBlank() || draft.locationId == draft.destinationLocationId
            ) throw IllegalStateException("Choose two different locations and a positive transfer quantity.")
        } else if (draft.movementType == "adjustment") {
            if (draft.quantity == 0) {
                throw IllegalStateException(text(R.string.sync_adjustment_non_zero))
            }
        } else if (draft.quantity <= 0) {
            throw IllegalStateException(text(R.string.sync_movement_quantity_positive))
        }
    }

    private fun applyMovementDrafts(
        db: SQLiteDatabase,
        drafts: List<MovementEntryDraft>,
    ) {
        drafts.forEach { draft ->
            val component = getComponentById(db, draft.componentId)
                ?: throw IllegalStateException(text(R.string.sync_choose_active_component_first))

            val sourceLocation = draft.locationId?.trim()?.takeIf(String::isNotEmpty) ?: component.location
            val destinationLocation = draft.destinationLocationId?.trim()?.takeIf(String::isNotEmpty)
            val delta = calculateQuantityDelta(draft.movementType, draft.quantity)
            val newQuantity = component.quantity + delta
            if (newQuantity < 0) {
                throw IllegalStateException(text(R.string.sync_negative_stock_error))
            }

            val updatedAt = utcNow()
            ensureStorageLocation(db, sourceLocation, updatedAt)
            destinationLocation?.let { ensureStorageLocation(db, it, updatedAt) }
            if (draft.movementType == "transfer") {
                val sourceQuantity = allocationQuantity(db, draft.componentId, sourceLocation)
                val destinationQuantity = allocationQuantity(db, draft.componentId, requireNotNull(destinationLocation))
                val transferred = StockAllocationMath.transfer(sourceQuantity, destinationQuantity, draft.quantity)
                setAllocationQuantity(db, draft.componentId, sourceLocation, transferred.first)
                setAllocationQuantity(db, draft.componentId, destinationLocation, transferred.second)
            } else {
                val currentAllocation = allocationQuantity(db, draft.componentId, sourceLocation)
                val allocationDelta = calculateQuantityDelta(draft.movementType, draft.quantity)
                val newAllocation = StockAllocationMath.applyDelta(currentAllocation, allocationDelta)
                setAllocationQuantity(db, draft.componentId, sourceLocation, newAllocation)
            }
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
                put("location_id", sourceLocation)
                put("destination_location_id", destinationLocation)
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
            verifyAllocationTotal(db, draft.componentId, newQuantity)

            enqueueEntity(db, "component", draft.componentId, updatedAt)
            enqueueEntity(db, "stock_movement", movementId, updatedAt)
        }
    }

    private fun ensureStorageLocation(db: SQLiteDatabase, id: String, updatedAt: String) {
        require(id.isNotBlank() && id.length <= 120) { "Invalid storage location code." }
        val deleted=db.rawQuery("SELECT deleted FROM storage_locations WHERE id = ?",arrayOf(id)).use{it.moveToFirst()&&it.getInt(0)==1}
        check(!deleted) { "库位已删除，请改用其他编号的库位。" }
        db.insertWithOnConflict(
            "storage_locations",
            null,
            ContentValues().apply {
                put("id", id)
                put("name", id)
                put("updated_at", updatedAt)
                put("deleted", 0)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    private fun allocationQuantity(db: SQLiteDatabase, componentId: String, locationId: String): Int =
        db.rawQuery(
            "SELECT quantity FROM component_allocations WHERE component_id = ? AND location_id = ?",
            arrayOf(componentId, locationId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

    private fun setAllocationQuantity(db: SQLiteDatabase, componentId: String, locationId: String, quantity: Int) {
        db.insertWithOnConflict(
            "component_allocations",
            null,
            ContentValues().apply {
                put("component_id", componentId)
                put("location_id", locationId)
                put("quantity", quantity)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun verifyAllocationTotal(db: SQLiteDatabase, componentId: String, expected: Int) {
        val total = db.rawQuery(
            "SELECT COALESCE(SUM(quantity), 0) FROM component_allocations WHERE component_id = ?",
            arrayOf(componentId),
        ).use { cursor -> cursor.moveToFirst(); cursor.getLong(0) }
        check(total == expected.toLong()) { "Allocation total does not match component quantity." }
    }

    private fun consumeAllocations(
        db: SQLiteDatabase,
        componentId: String,
        requested: Int,
    ): List<Pair<String, Int>> {
        var remaining = requested
        val consumed = mutableListOf<Pair<String, Int>>()
        db.rawQuery(
            "SELECT location_id, quantity FROM component_allocations WHERE component_id = ? AND quantity > 0 " +
                "ORDER BY quantity DESC, location_id ASC",
            arrayOf(componentId),
        ).use { cursor ->
            while (cursor.moveToNext() && remaining > 0) {
                val locationId = cursor.getString(0)
                val available = cursor.getInt(1)
                val take = minOf(available, remaining)
                setAllocationQuantity(db, componentId, locationId, available - take)
                consumed += locationId to take
                remaining -= take
            }
        }
        check(remaining == 0) { "Allocated stock is insufficient; refresh the BOM preview." }
        return consumed
    }

    private fun allocationPlan(db: SQLiteDatabase, componentId: String, requested: Int): List<Pair<String, Int>> {
        val available = db.rawQuery(
            "SELECT location_id, quantity FROM component_allocations WHERE component_id = ? AND quantity > 0",
            arrayOf(componentId),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) } }
        return com.componentvault.android.data.bom.BomAllocationPlanner.plan(requested, available)
            .map { it.locationId to it.quantity }
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
                put("inventory_protocol", 1)
                put("components", JSONArray())
                put("stock_movements", JSONArray())
                put("storage_locations", getStorageLocationsJson(db))
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
                deleted,
                base_updated_at
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
                put("base_updated_at", cursor.getString(cursor.getColumnIndexOrThrow("base_updated_at")))
                put("allocations", JSONArray().apply {
                    db.rawQuery(
                        "SELECT location_id, quantity FROM component_allocations WHERE component_id = ? ORDER BY location_id",
                        arrayOf(componentId),
                    ).use { allocationCursor ->
                        while (allocationCursor.moveToNext()) put(JSONObject().apply {
                            put("location_id", allocationCursor.getString(0))
                            put("quantity", allocationCursor.getInt(1))
                        })
                    }
                })
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
                deleted,
                location_id,
                destination_location_id
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
                put("location_id", cursor.getString(cursor.getColumnIndexOrThrow("location_id")))
                put("destination_location_id", cursor.getString(cursor.getColumnIndexOrThrow("destination_location_id")))
            }
        }
    }

    private fun resolveSyncEndpoint(settings: SyncConfiguration): ResolvedSyncEndpoint<JSONObject> =
        SyncEndpointResolver.resolve(settings) { configuration, timeout ->
            callJson(configuration, "POST", "/auth/ping", null, timeout)
        }

    private fun callJson(
        settings: SyncConfiguration,
        method: String,
        path: String,
        body: JSONObject?,
        timeoutMillis: Int = 60_000,
        accountId: String? = null,
    ): JSONObject {
        val connection = URL("${settings.serverBaseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMillis
        connection.readTimeout = timeoutMillis
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer ${settings.apiToken}")
        if (accountId != null) connection.setRequestProperty("X-Component-Vault-Account-Id", accountId)
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(body.toString())
                }
            }
            val responseCode = connection.responseCode
            val responseText = readResponseText(connection, responseCode)
            if (responseCode !in 200..299) {
                if (path == "/auth/me" && responseCode == 404) {
                    throw IllegalStateException(text(R.string.sync_identity_unavailable))
                }
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
            val parsedBody = JSONObject(body)
            val validationErrors = parsedBody.optJSONArray("detail")
            if (statusCode == 422 && validationErrors != null && validationErrors.length() > 0) {
                val first = validationErrors.optJSONObject(0)
                val location = first?.optJSONArray("loc")
                val path = location?.let { fields ->
                    (0 until fields.length()).joinToString(".") { fields.optString(it) }
                }.orEmpty()
                return text(
                    R.string.sync_validation_failed,
                    path,
                    first?.optString("msg").orEmpty(),
                    validationErrors.length(),
                )
            }
            val detail = parsedBody.optString("detail")
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
        check(pullResponse.optInt("inventory_protocol", 0) == 1) {
            "The sync response is missing inventory_protocol=1. Local changes were kept."
        }
        val locations = pullResponse.optJSONArray("storage_locations") ?: JSONArray()
        for (index in 0 until locations.length()) {
            upsertRemoteStorageLocation(db, locations.getJSONObject(index))
        }
        val components = pullResponse.optJSONArray("components") ?: JSONArray()
        for (index in 0 until components.length()) {
            val component = components.getJSONObject(index)
            val id = component.getString("id")
            val pushed = pushedEntities.firstOrNull { it.entityType == "component" && it.entityId == id }
            val queued = db.rawQuery(
                "SELECT entity_updated_at FROM sync_queue WHERE entity_type = 'component' AND entity_id = ?",
                arrayOf(id),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            if (SyncProtocol.canApplyInventorySnapshot(queued, pushed?.entityUpdatedAt)) {
                upsertRemoteComponent(db, component)
            } else if (pushed != null) {
                // A second local edit survives the pull, based on the version actually uploaded.
                db.execSQL("UPDATE components SET base_updated_at = ? WHERE id = ?", arrayOf(pushed.entityUpdatedAt, id))
            }
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
            if (entity.entityType == "component" && (0 until components.length()).none {
                components.getJSONObject(it).getString("id") == entity.entityId
            }) {
                db.execSQL("UPDATE components SET base_updated_at = ? WHERE id = ?", arrayOf(entity.entityUpdatedAt, entity.entityId))
            }
            removeQueuedIfSuperseded(
                db = db,
                entityType = entity.entityType,
                entityId = entity.entityId,
                updatedAt = entity.entityUpdatedAt,
            )
        }
    }

    private fun upsertRemoteComponent(
        db: SQLiteDatabase,
        component: JSONObject,
    ) {
        val values = ContentValues().apply {
            put("id", component.getString("id"))
            put("sku", component.getString("sku"))
            put("name", component.getString("name"))
            put("category", component.getString("category"))
            put("package_name", component.getString("package_name"))
            put("location", component.getString("location"))
            put("description", if (component.isNull("description")) null as String? else component.optString("description"))
            put("quantity", component.getInt("quantity"))
            put("min_stock", component.getInt("min_stock"))
            put("updated_at", component.getString("updated_at"))
            put("deleted", if (component.getBoolean("deleted")) 1 else 0)
            put("base_updated_at", component.getString("updated_at"))
        }
        db.insertWithOnConflict(
            "components",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        val allocations = component.optJSONArray("allocations") ?: JSONArray().put(
            JSONObject().put("location_id", component.getString("location").trim().ifBlank { "待整理" })
                .put("quantity", component.getInt("quantity")),
        )
        run {
            db.delete("component_allocations", "component_id = ?", arrayOf(component.getString("id")))
            for (index in 0 until allocations.length()) {
                val allocation = allocations.getJSONObject(index)
                if (component.optJSONArray("allocations") == null) {
                    ensureStorageLocation(db, allocation.getString("location_id"), component.getString("updated_at"))
                }
                setAllocationQuantity(
                    db,
                    component.getString("id"),
                    allocation.getString("location_id"),
                    allocation.getInt("quantity"),
                )
            }
            verifyAllocationTotal(db, component.getString("id"), component.getInt("quantity"))
        }
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
                if (
                    !SyncProtocol.isRemoteAtLeastAsNew(
                        localUpdatedAt = existingUpdatedAt,
                        remoteUpdatedAt = movement.getString("updated_at"),
                    )
                ) {
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
            put("location_id", movement.optString("location_id").takeIf(String::isNotBlank))
            put("destination_location_id", movement.optString("destination_location_id").takeIf(String::isNotBlank))
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
        db.rawQuery(
            """
            SELECT entity_updated_at
            FROM sync_queue
            WHERE entity_type = ? AND entity_id = ?
            """.trimIndent(),
            arrayOf(entityType, entityId),
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return
            }
            val queuedUpdatedAt = cursor.getString(
                cursor.getColumnIndexOrThrow("entity_updated_at"),
            )
            if (!SyncProtocol.timestampsEqual(queuedUpdatedAt, updatedAt)) {
                return
            }
            db.delete(
                "sync_queue",
                "entity_type = ? AND entity_id = ? AND entity_updated_at = ?",
                arrayOf(entityType, entityId, queuedUpdatedAt),
            )
        }
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

    private fun calculateQuantityDelta(
        movementType: String,
        quantity: Int,
    ): Int = when (movementType.lowercase(Locale.US)) {
        "inbound" -> quantity
        "outbound" -> -quantity
        "adjustment" -> quantity
        "transfer" -> 0
        else -> throw IllegalStateException(text(R.string.sync_unsupported_movement_type))
    }

    private fun normalizeMovementQuantity(
        movementType: String,
        quantity: Int,
    ): Int = when (movementType.lowercase(Locale.US)) {
        "adjustment" -> quantity
        else -> kotlin.math.abs(quantity)
    }

    private fun utcNow(): String {
        val millis = lastMutationMillis.updateAndGet { previous ->
            maxOf(System.currentTimeMillis(), previous + 1L)
        }
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(millis))
    }

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

    private fun String.isLikelyModelLike(
        sku: String,
        model: String?,
    ): Boolean {
        val normalized = trim()
        if (normalized.isBlank()) {
            return false
        }
        if (normalized.equals(sku.trim(), ignoreCase = true)) {
            return true
        }
        if (!model.isNullOrBlank() && normalized.equals(model.trim(), ignoreCase = true)) {
            return true
        }
        if (normalized.any { it.code in 0x4E00..0x9FFF } || normalized.any(Char::isWhitespace)) {
            return false
        }
        val alphaNumericCount = normalized.count(Char::isLetterOrDigit)
        val hasSeparator = normalized.any { it == '-' || it == '_' || it == '/' || it == '.' }
        return alphaNumericCount >= 5 && (hasSeparator || normalized.any(Char::isDigit))
    }

    private fun upsertRemoteStorageLocation(db: SQLiteDatabase, location: JSONObject) {
        val id = location.getString("id")
        val remoteUpdatedAt = location.getString("updated_at")
        val existingUpdatedAt = db.rawQuery(
            "SELECT updated_at FROM storage_locations WHERE id = ?",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (existingUpdatedAt != null && !SyncProtocol.isRemoteAtLeastAsNew(existingUpdatedAt, remoteUpdatedAt)) return
        db.insertWithOnConflict(
            "storage_locations",
            null,
            ContentValues().apply {
                put("id", id)
                put("name", location.getString("name"))
                put("updated_at", remoteUpdatedAt)
                put("deleted", if (location.getBoolean("deleted")) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun getStorageLocationsJson(db: SQLiteDatabase): JSONArray = JSONArray().apply {
        db.rawQuery("SELECT id, name, updated_at, deleted FROM storage_locations ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) put(JSONObject().apply {
                put("id", cursor.getString(0))
                put("name", cursor.getString(1))
                put("updated_at", cursor.getString(2))
                put("deleted", cursor.getInt(3) == 1)
            })
        }
    }

    private fun readStoredSyncCursor(): Long? = if (preferences.contains(KEY_SYNC_CURSOR)) {
        preferences.getLong(KEY_SYNC_CURSOR, 0L).coerceAtLeast(0L)
    } else {
        null
    }

    private fun saveSyncCursor(decision: CursorDecision) {
        val editor = preferences.edit()
        when (decision) {
            is CursorDecision.Store -> editor.putLong(KEY_SYNC_CURSOR, decision.value)
            CursorDecision.Clear -> editor.remove(KEY_SYNC_CURSOR)
        }
        check(editor.commit()) { "Unable to persist sync cursor" }
    }

    private fun checkSyncConfigurationUnchanged(settings: SyncConfiguration) {
        val currentServerUrl = preferences.getString(KEY_SERVER_BASE_URL, "").orEmpty()
        val currentApiToken = preferences.getString(KEY_API_TOKEN, "").orEmpty()
        val currentExternalUrl = preferences.getString(KEY_EXTERNAL_SERVER_BASE_URL, "").orEmpty()
        check(
            currentServerUrl == settings.serverBaseUrl &&
                currentExternalUrl == settings.externalServerBaseUrl &&
                currentApiToken == settings.apiToken,
        ) {
            "Sync configuration changed while synchronization was running. Please retry."
        }
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
        val lastMutationMillis = java.util.concurrent.atomic.AtomicLong(0L)
        const val PREFS_NAME = "component_vault_sync"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SERVER_BASE_URL = "server_base_url"
        const val KEY_EXTERNAL_SERVER_BASE_URL = "external_server_base_url"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        const val KEY_LAST_SYNCED_AT = "last_synced_at"
        const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
        const val KEY_SYNC_CURSOR = "sync_cursor"
        const val KEY_BOUND_SERVER_ID = "bound_server_id"
        const val KEY_BOUND_ACCOUNT_ID = "bound_account_id"
        const val KEY_INVENTORY_PROTOCOL_MIGRATED = "inventory_protocol_migrated_v1"
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
        const val KEY_OCR_ENGINE_MODE = "ocr_engine_mode"
        const val KEY_APP_LANGUAGE = "app_language"
        const val LOOKUP_CACHE_PREFIX = "lcsc_lookup_cache:"
        const val SERVER_LOOKUP_CACHE_SCOPE = "server"
        const val LOOKUP_CACHE_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

        val TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withLocale(Locale.US)
            .withZone(ZoneOffset.UTC)
    }
}
