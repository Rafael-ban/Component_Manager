package com.componentvault.android.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.componentvault.android.R
import com.componentvault.android.data.ComponentLabelPayloadMode
import com.componentvault.android.data.ComponentLabelTemplate
import com.componentvault.android.data.ComponentTextLabelTemplate
import com.componentvault.android.model.AppLanguage
import com.componentvault.android.model.ComponentImportFieldOrigin
import com.componentvault.android.model.InventorySortOption
import com.componentvault.android.model.OcrEngineMode
import java.util.Locale

@Immutable
internal data class ComponentVaultStrings(
    val shell: ShellStrings,
    val common: CommonStrings,
    val inventory: InventoryStrings,
    val movements: MovementsStrings,
    val overview: OverviewStrings,
    val settings: SettingsStrings,
    val importer: ImportStrings,
    val forms: FormsStrings,
)

@Immutable
internal data class ShellStrings(
    val appTitle: String,
    val inventoryDestination: String,
    val movementsDestination: String,
    val overviewDestination: String,
    val settingsDestination: String,
) {
    fun destinationLabel(destination: InventoryDestination): String = when (destination) {
        InventoryDestination.Inventory -> inventoryDestination
        InventoryDestination.Movements -> movementsDestination
        InventoryDestination.Overview -> overviewDestination
        InventoryDestination.Settings -> settingsDestination
    }
}

@Immutable
internal data class CommonStrings(
    val actionAdd: String,
    val actionBack: String,
    val actionCancel: String,
    val actionDelete: String,
    val actionEdit: String,
    val actionGenerateLabel: String,
    val actionHideToken: String,
    val actionImport: String,
    val actionManageSync: String,
    val actionRecordMovement: String,
    val actionSave: String,
    val actionSaveAndGenerateLabel: String,
    val actionSaveSettings: String,
    val actionShowToken: String,
    val actionSyncNow: String,
    val actionTestConnection: String,
    val actionViewInventory: String,
    val actionViewMovements: String,
    val emptyAllComponentsHealthy: String,
    val emptyNoComponentSelected: String,
    val emptyNoComponentsMatchFilter: String,
    val emptyNoMovements: String,
    val labelNoDescription: String,
    val labelNoNote: String,
    val labelNoReasonRecorded: String,
    val labelNotConfigured: String,
    val labelUpdated: String,
    val statusLowStock: String,
    val statusHealthy: String,
    val fieldApiToken: String,
    val fieldCategory: String,
    val fieldComponent: String,
    val fieldDescription: String,
    val fieldLocation: String,
    val fieldMinimumStock: String,
    val fieldName: String,
    val fieldNote: String,
    val fieldPackage: String,
    val fieldQuantity: String,
    val fieldReason: String,
    val fieldServerUrl: String,
    val fieldSku: String,
)

@Immutable
internal data class InventoryStrings(
    val filtersTitle: String,
    val filtersSubtitle: String,
    val searchLabel: String,
    val searchPlaceholder: String,
    val filterAll: String,
    val filterLowStock: String,
    val filterCategoryLabel: String,
    val filterCategoryAll: String,
    val filterLocationLabel: String,
    val filterLocationAll: String,
    val filterSortLabel: String,
    val sortUpdatedNewest: String,
    val sortName: String,
    val sortQuantityLowToHigh: String,
    val sortQuantityHighToLow: String,
    val resultsSummaryPattern: String,
    val detailBasicTitle: String,
    val detailStockTitle: String,
    val detailLocationTitle: String,
    val detailNotesTitle: String,
    val detailRecentMovementsTitle: String,
    val detailRecentMovementsEmpty: String,
    val componentSubtitlePattern: String,
    val componentMinStockPattern: String,
    val selectedComponentTitle: String,
    val selectedComponentEmptySubtitle: String,
    val selectedComponentLowStockStatus: String,
    val selectedComponentHealthyStatus: String,
    val metricQuantity: String,
    val metricMinStock: String,
) {
    fun resultsSummary(itemCount: Int, categoryCount: Int, locationCount: Int): String =
        formatPattern(resultsSummaryPattern, itemCount, categoryCount, locationCount)

    fun componentSubtitle(sku: String, category: String, packageName: String): String =
        formatPattern(componentSubtitlePattern, sku, category, packageName)

    fun componentMinStock(minStock: Int): String =
        formatPattern(componentMinStockPattern, minStock)

    fun sortLabel(sort: InventorySortOption): String = when (sort) {
        InventorySortOption.UpdatedNewest -> sortUpdatedNewest
        InventorySortOption.Name -> sortName
        InventorySortOption.QuantityLowToHigh -> sortQuantityLowToHigh
        InventorySortOption.QuantityHighToLow -> sortQuantityHighToLow
    }
}

@Immutable
internal data class MovementsStrings(
    val recordTitle: String,
    val recordSubtitle: String,
    val resultsSummaryPattern: String,
    val detailTitle: String,
    val detailEmptySupporting: String,
    val detailEmpty: String,
    val detailHappenedAt: String,
    val typeInbound: String,
    val typeOutbound: String,
    val typeAdjustment: String,
    val positiveQuantityPattern: String,
    val updatedAtPattern: String,
) {
    fun resultsSummary(itemCount: Int, componentCount: Int): String =
        formatPattern(resultsSummaryPattern, itemCount, componentCount)

    fun movementTypeLabel(movementType: String): String = when (movementType.lowercase()) {
        "inbound" -> typeInbound
        "outbound" -> typeOutbound
        else -> typeAdjustment
    }

    fun movementQuantity(quantity: Int): String = if (quantity > 0) {
        formatPattern(positiveQuantityPattern, quantity)
    } else {
        quantity.toString()
    }

    fun updatedAt(value: String): String = formatPattern(updatedAtPattern, value)
}

@Immutable
internal data class OverviewStrings(
    val syncTitle: String,
    val syncSubtitle: String,
    val lowStockSupporting: String,
    val recentActivitySupporting: String,
    val inventoryHint: String,
    val metricComponents: String,
    val metricUnits: String,
    val metricLowStock: String,
    val metricMovements: String,
    val lowStockSectionTitle: String,
    val recentActivitySectionTitle: String,
)

@Immutable
internal data class SettingsStrings(
    val summaryTitle: String,
    val summarySubtitle: String,
    val syncOnLaunch: String,
    val deviceId: String,
    val endpoint: String,
    val lastResult: String,
    val lastSynced: String,
    val connectionTitle: String,
    val connectionSubtitle: String,
    val authTitle: String,
    val authSubtitle: String,
    val syncBehaviorTitle: String,
    val syncBehaviorSubtitle: String,
    val importPreferencesTitle: String,
    val importPreferencesSubtitle: String,
    val languageTitle: String,
    val languageSubtitle: String,
    val languageLabel: String,
    val languageChinese: String,
    val languageEnglish: String,
    val aboutTitle: String,
    val aboutSubtitle: String,
    val serverUrlPlaceholder: String,
    val syncOnLaunchDescription: String,
    val syncAfterWrites: String,
    val syncAfterWritesDescription: String,
    val rememberLastImportLocation: String,
    val rememberLastImportLocationDescription: String,
    val localAutoRecognition: String,
    val localAutoRecognitionDescription: String,
    val aggressiveRecognition: String,
    val aggressiveRecognitionDescription: String,
    val localImportLearning: String,
    val localImportLearningDescription: String,
    val serverJlcLookup: String,
    val serverJlcLookupDescription: String,
    val ocrEngineLabel: String,
    val ocrEngineAuto: String,
    val ocrEngineAutoDescription: String,
    val ocrEngineMlKit: String,
    val ocrEngineMlKitDescription: String,
    val ocrEnginePaddle: String,
    val ocrEnginePaddleDescription: String,
    val learnedMappingsCount: String,
    val noLearnedMappings: String,
    val clearLearnedMappingsAction: String,
    val clearLearnedMappingsTitle: String,
    val clearLearnedMappingsMessage: String,
    val appVersion: String,
    val localStorage: String,
    val localStorageValue: String,
    val aboutBody: String,
) {
    fun appLanguageLabel(language: AppLanguage): String = when (language) {
        AppLanguage.ZhCn -> languageChinese
        AppLanguage.English -> languageEnglish
    }

    fun ocrEngineLabel(mode: OcrEngineMode): String = when (mode) {
        OcrEngineMode.Auto -> ocrEngineAuto
        OcrEngineMode.MlKit -> ocrEngineMlKit
        OcrEngineMode.PaddleExperimental -> ocrEnginePaddle
    }
}

@Immutable
internal data class ImportStrings(
    val title: String,
    val subtitle: String,
    val supportedFormatHint: String,
    val enrichmentTitle: String,
    val enrichmentSubtitle: String,
    val rawInputLabel: String,
    val rawInputPlaceholder: String,
    val actionParseText: String,
    val actionParseSupplierText: String,
    val actionScanQr: String,
    val actionScanSupplierText: String,
    val actionCaptureText: String,
    val actionOpenFullEditor: String,
    val recognizedTitle: String,
    val recognizedSubtitle: String,
    val sourceLabel: String,
    val referenceNameLabel: String,
    val modelLabel: String,
    val brandLabel: String,
    val vendorLabel: String,
    val modelFamilyLabel: String,
    val recognitionConfidenceLabel: String,
    val matchedByLabel: String,
    val importDetailTitle: String,
    val quantityHint: String,
    val importPreviewTitle: String,
    val parseFirstError: String,
    val scanFailedPattern: String,
    val scannerPermissionTitle: String,
    val scannerPermissionDescription: String,
    val scannerPermissionDeniedTitle: String,
    val scannerPermissionDeniedDescription: String,
    val scannerStarting: String,
    val scannerScanningHint: String,
    val scannerFailedTitle: String,
    val scannerFailedDescription: String,
    val supplierScannerStarting: String,
    val supplierScannerHint: String,
    val supplierScannerCaptureHint: String,
    val supplierScannerRecognizing: String,
    val supplierScannerFailedTitle: String,
    val supplierScanFailedDescription: String,
    val supplierScanNoTextDescription: String,
    val supplierScannerFrameUnavailable: String,
    val supplierScannerEnginePattern: String,
    val actionGrantCameraAccess: String,
    val actionRetryScan: String,
    val actionReturnToImport: String,
    val actionUseReferenceName: String,
    val parseSupplierTextError: String,
    val nameConfirmationHint: String,
    val localRulesOnly: String,
    val learningMatchSku: String,
    val learningMatchMpn: String,
    val serverLookupDisabled: String,
    val lookupOnlyFillsMissing: String,
    val lookupLoading: String,
    val lookupSuccess: String,
    val lookupCacheSuccess: String,
    val lookupNoMatch: String,
    val lookupNotConfigured: String,
    val lookupFailedPattern: String,
    val fieldOriginPattern: String,
    val fieldOriginParsed: String,
    val fieldOriginRule: String,
    val fieldOriginLearned: String,
    val fieldOriginServer: String,
    val fieldOriginUser: String,
    val labelPreviewTitle: String,
    val labelPreviewSubtitle: String,
    val labelPayloadTitle: String,
    val labelPayloadSubtitle: String,
    val labelFormatTitle: String,
    val labelFormatSubtitle: String,
    val labelSize10x40Qr: String,
    val labelSize30x40Qr: String,
    val labelSizeTextOnly: String,
    val labelFormatQrSummaryPattern: String,
    val labelFormatTextSummaryPattern: String,
    val labelPayloadModeTitle: String,
    val labelPayloadModeNone: String,
    val labelPayloadModeCompact: String,
    val labelPayloadModeStandard: String,
    val labelPayloadModeJlc: String,
    val labelCompanionToggle: String,
    val labelCompanionHint: String,
    val textLabelTemplateTitle: String,
    val textLabelTemplateSubtitle: String,
    val textLabelTemplateNameSku: String,
    val textLabelTemplateNamePackageSku: String,
    val textLabelTemplateNameModel: String,
    val labelTextContentTitle: String,
    val labelFooterText: String,
    val exportSectionTitle: String,
    val labelCopiesLabel: String,
    val actionExportPng: String,
    val actionExportPdf: String,
    val exportPngSuccess: String,
    val exportPdfSuccess: String,
    val exportGenericError: String,
    val exportFailedPattern: String,
) {
    fun scanFailed(detail: String): String = formatPattern(scanFailedPattern, detail)
    fun supplierScannerEngine(engineLabel: String): String = formatPattern(supplierScannerEnginePattern, engineLabel)
    fun lookupFailed(detail: String): String = formatPattern(lookupFailedPattern, detail)
    fun exportFailed(detail: String): String = formatPattern(exportFailedPattern, detail)
    fun labelTemplateLabel(template: ComponentLabelTemplate): String = when (template.id) {
        ComponentLabelTemplate.Qr10x40.id -> labelSize10x40Qr
        ComponentLabelTemplate.Qr30x40.id -> labelSize30x40Qr
        ComponentLabelTemplate.TextOnly.id -> labelSizeTextOnly
        else -> labelSize30x40Qr
    }

    fun labelFormatSummary(template: ComponentLabelTemplate): String = if (template.isQrLabel) {
        formatPattern(
            labelFormatQrSummaryPattern,
            labelTemplateLabel(template),
            requireNotNull(template.displayWidthMm),
            template.displayHeightMm,
            requireNotNull(template.qrSizeMm),
        )
    } else {
        formatPattern(
            labelFormatTextSummaryPattern,
            labelTemplateLabel(template),
        )
    }

    fun payloadModeLabel(mode: ComponentLabelPayloadMode): String = when (mode) {
        ComponentLabelPayloadMode.None -> labelPayloadModeNone
        ComponentLabelPayloadMode.CompactOffline -> labelPayloadModeCompact
        ComponentLabelPayloadMode.StandardWarehouse -> labelPayloadModeStandard
        ComponentLabelPayloadMode.JlcCompatible -> labelPayloadModeJlc
    }

    fun textLabelTemplateLabel(template: ComponentTextLabelTemplate): String = when (template) {
        ComponentTextLabelTemplate.NameSku -> textLabelTemplateNameSku
        ComponentTextLabelTemplate.NamePackageSku -> textLabelTemplateNamePackageSku
        ComponentTextLabelTemplate.NameModel -> textLabelTemplateNameModel
    }

    fun fieldOrigin(origin: ComponentImportFieldOrigin): String = formatPattern(
        fieldOriginPattern,
        when (origin) {
            ComponentImportFieldOrigin.Parsed -> fieldOriginParsed
            ComponentImportFieldOrigin.Rule -> fieldOriginRule
            ComponentImportFieldOrigin.Learned -> fieldOriginLearned
            ComponentImportFieldOrigin.Server -> fieldOriginServer
            ComponentImportFieldOrigin.User -> fieldOriginUser
        },
    )
}

@Immutable
internal data class FormsStrings(
    val addComponentTitle: String,
    val editComponentTitle: String,
    val invalidQuantityMinStock: String,
    val recordStockMovementTitle: String,
    val componentBasicTitle: String,
    val componentBasicSubtitle: String,
    val componentStockTitle: String,
    val componentStorageTitle: String,
    val movementScopeTitle: String,
    val movementScopeSubtitle: String,
    val movementEntryTitle: String,
    val deleteConfirmTitle: String,
    val deleteConfirmMessage: String,
    val movementEditorInstruction: String,
    val componentRequiredFields: String,
    val componentNonNegative: String,
    val chooseComponentTypeReason: String,
    val adjustmentNonZero: String,
    val movementQuantityPositive: String,
)

internal val LocalComponentVaultStrings = staticCompositionLocalOf<ComponentVaultStrings> {
    error("ComponentVaultStrings not provided")
}

@Composable
internal fun ProvideComponentVaultStrings(
    strings: ComponentVaultStrings,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalComponentVaultStrings provides strings,
        content = content,
    )
}

@Composable
@ReadOnlyComposable
internal fun vaultStrings(): ComponentVaultStrings = LocalComponentVaultStrings.current

@Composable
internal fun runtimeComponentVaultStrings(): ComponentVaultStrings {
    return ComponentVaultStrings(
        shell = ShellStrings(
            appTitle = stringResource(R.string.top_bar_title),
            inventoryDestination = stringResource(R.string.destination_inventory),
            movementsDestination = stringResource(R.string.destination_movements),
            overviewDestination = stringResource(R.string.destination_overview),
            settingsDestination = stringResource(R.string.destination_settings),
        ),
        common = CommonStrings(
            actionAdd = stringResource(R.string.action_add),
            actionBack = stringResource(R.string.action_back),
            actionCancel = stringResource(R.string.action_cancel),
            actionDelete = stringResource(R.string.action_delete),
            actionEdit = stringResource(R.string.action_edit),
            actionGenerateLabel = stringResource(R.string.action_generate_label),
            actionHideToken = stringResource(R.string.action_hide_token),
            actionImport = stringResource(R.string.action_import_jlc),
            actionManageSync = stringResource(R.string.action_manage_sync),
            actionRecordMovement = stringResource(R.string.action_record_movement),
            actionSave = stringResource(R.string.action_save),
            actionSaveAndGenerateLabel = stringResource(R.string.action_save_and_generate_label),
            actionSaveSettings = stringResource(R.string.action_save_settings),
            actionShowToken = stringResource(R.string.action_show_token),
            actionSyncNow = stringResource(R.string.action_sync_now),
            actionTestConnection = stringResource(R.string.action_test_connection),
            actionViewInventory = stringResource(R.string.action_view_inventory),
            actionViewMovements = stringResource(R.string.action_view_movements),
            emptyAllComponentsHealthy = stringResource(R.string.empty_all_components_healthy),
            emptyNoComponentSelected = stringResource(R.string.empty_no_component_selected),
            emptyNoComponentsMatchFilter = stringResource(R.string.empty_no_components_match_filter),
            emptyNoMovements = stringResource(R.string.empty_no_movements),
            labelNoDescription = stringResource(R.string.label_no_description),
            labelNoNote = stringResource(R.string.label_no_note),
            labelNoReasonRecorded = stringResource(R.string.label_no_reason_recorded),
            labelNotConfigured = stringResource(R.string.label_not_configured),
            labelUpdated = stringResource(R.string.label_updated),
            statusLowStock = stringResource(R.string.status_low_stock),
            statusHealthy = stringResource(R.string.status_healthy),
            fieldApiToken = stringResource(R.string.field_api_token),
            fieldCategory = stringResource(R.string.field_category),
            fieldComponent = stringResource(R.string.field_component),
            fieldDescription = stringResource(R.string.field_description),
            fieldLocation = stringResource(R.string.field_location),
            fieldMinimumStock = stringResource(R.string.field_minimum_stock),
            fieldName = stringResource(R.string.field_name),
            fieldNote = stringResource(R.string.field_note),
            fieldPackage = stringResource(R.string.field_package),
            fieldQuantity = stringResource(R.string.field_quantity),
            fieldReason = stringResource(R.string.field_reason),
            fieldServerUrl = stringResource(R.string.field_server_url),
            fieldSku = stringResource(R.string.field_sku),
        ),
        inventory = InventoryStrings(
            filtersTitle = stringResource(R.string.inventory_filters_title),
            filtersSubtitle = stringResource(R.string.inventory_filters_subtitle),
            searchLabel = stringResource(R.string.search_components_label),
            searchPlaceholder = stringResource(R.string.search_components_placeholder),
            filterAll = stringResource(R.string.filter_all),
            filterLowStock = stringResource(R.string.filter_low_stock),
            filterCategoryLabel = stringResource(R.string.filter_category_label),
            filterCategoryAll = stringResource(R.string.filter_category_all),
            filterLocationLabel = stringResource(R.string.filter_location_label),
            filterLocationAll = stringResource(R.string.filter_location_all),
            filterSortLabel = stringResource(R.string.filter_sort_label),
            sortUpdatedNewest = stringResource(R.string.sort_updated_newest),
            sortName = stringResource(R.string.sort_name),
            sortQuantityLowToHigh = stringResource(R.string.sort_quantity_low_to_high),
            sortQuantityHighToLow = stringResource(R.string.sort_quantity_high_to_low),
            resultsSummaryPattern = stringResource(R.string.inventory_results_summary),
            detailBasicTitle = stringResource(R.string.inventory_detail_basic_title),
            detailStockTitle = stringResource(R.string.inventory_detail_stock_title),
            detailLocationTitle = stringResource(R.string.inventory_detail_location_title),
            detailNotesTitle = stringResource(R.string.inventory_detail_notes_title),
            detailRecentMovementsTitle = stringResource(R.string.inventory_detail_recent_movements_title),
            detailRecentMovementsEmpty = stringResource(R.string.inventory_detail_recent_movements_empty),
            componentSubtitlePattern = stringResource(R.string.component_subtitle_format),
            componentMinStockPattern = stringResource(R.string.component_min_stock_format),
            selectedComponentTitle = stringResource(R.string.selected_component_title),
            selectedComponentEmptySubtitle = stringResource(R.string.selected_component_empty_subtitle),
            selectedComponentLowStockStatus = stringResource(R.string.selected_component_low_stock_status),
            selectedComponentHealthyStatus = stringResource(R.string.selected_component_healthy_status),
            metricQuantity = stringResource(R.string.metric_quantity),
            metricMinStock = stringResource(R.string.metric_min_stock),
        ),
        movements = MovementsStrings(
            recordTitle = stringResource(R.string.movements_record_title),
            recordSubtitle = stringResource(R.string.movements_record_subtitle),
            resultsSummaryPattern = stringResource(R.string.movements_results_summary),
            detailTitle = stringResource(R.string.movements_detail_title),
            detailEmptySupporting = stringResource(R.string.movements_detail_empty_supporting),
            detailEmpty = stringResource(R.string.movements_detail_empty),
            detailHappenedAt = stringResource(R.string.movements_detail_happened_at),
            typeInbound = stringResource(R.string.movement_type_inbound),
            typeOutbound = stringResource(R.string.movement_type_outbound),
            typeAdjustment = stringResource(R.string.movement_type_adjustment),
            positiveQuantityPattern = stringResource(R.string.movement_positive_quantity_format),
            updatedAtPattern = stringResource(R.string.updated_at_format),
        ),
        overview = OverviewStrings(
            syncTitle = stringResource(R.string.overview_sync_title),
            syncSubtitle = stringResource(R.string.overview_sync_subtitle),
            lowStockSupporting = stringResource(R.string.overview_low_stock_supporting),
            recentActivitySupporting = stringResource(R.string.overview_recent_activity_supporting),
            inventoryHint = stringResource(R.string.overview_inventory_hint),
            metricComponents = stringResource(R.string.metric_components),
            metricUnits = stringResource(R.string.metric_units),
            metricLowStock = stringResource(R.string.metric_low_stock),
            metricMovements = stringResource(R.string.metric_movements),
            lowStockSectionTitle = stringResource(R.string.section_low_stock_watchlist_title),
            recentActivitySectionTitle = stringResource(R.string.section_recent_activity_title),
        ),
        settings = SettingsStrings(
            summaryTitle = stringResource(R.string.sync_summary_title),
            summarySubtitle = stringResource(R.string.sync_summary_subtitle),
            syncOnLaunch = stringResource(R.string.setting_auto_sync),
            deviceId = stringResource(R.string.setting_device_id),
            endpoint = stringResource(R.string.setting_endpoint),
            lastResult = stringResource(R.string.setting_last_result),
            lastSynced = stringResource(R.string.setting_last_synced),
            connectionTitle = stringResource(R.string.settings_connection_title),
            connectionSubtitle = stringResource(R.string.settings_connection_subtitle),
            authTitle = stringResource(R.string.settings_auth_title),
            authSubtitle = stringResource(R.string.settings_auth_subtitle),
            syncBehaviorTitle = stringResource(R.string.settings_sync_behavior_title),
            syncBehaviorSubtitle = stringResource(R.string.settings_sync_behavior_subtitle),
            importPreferencesTitle = stringResource(R.string.settings_import_preferences_title),
            importPreferencesSubtitle = stringResource(R.string.settings_import_preferences_subtitle),
            languageTitle = stringResource(R.string.settings_language_title),
            languageSubtitle = stringResource(R.string.settings_language_subtitle),
            languageLabel = stringResource(R.string.settings_language_label),
            languageChinese = stringResource(R.string.settings_language_chinese),
            languageEnglish = stringResource(R.string.settings_language_english),
            aboutTitle = stringResource(R.string.settings_about_title),
            aboutSubtitle = stringResource(R.string.settings_about_subtitle),
            serverUrlPlaceholder = stringResource(R.string.server_url_placeholder),
            syncOnLaunchDescription = stringResource(R.string.settings_sync_on_launch_description),
            syncAfterWrites = stringResource(R.string.settings_sync_after_writes),
            syncAfterWritesDescription = stringResource(R.string.settings_sync_after_writes_description),
            rememberLastImportLocation = stringResource(R.string.settings_remember_last_import_location),
            rememberLastImportLocationDescription = stringResource(R.string.settings_remember_last_import_location_description),
            localAutoRecognition = stringResource(R.string.settings_local_auto_recognition),
            localAutoRecognitionDescription = stringResource(R.string.settings_local_auto_recognition_description),
            aggressiveRecognition = stringResource(R.string.settings_aggressive_recognition),
            aggressiveRecognitionDescription = stringResource(R.string.settings_aggressive_recognition_description),
            localImportLearning = stringResource(R.string.settings_local_import_learning),
            localImportLearningDescription = stringResource(R.string.settings_local_import_learning_description),
            serverJlcLookup = stringResource(R.string.settings_server_jlc_lookup),
            serverJlcLookupDescription = stringResource(R.string.settings_server_jlc_lookup_description),
            ocrEngineLabel = stringResource(R.string.settings_ocr_engine_label),
            ocrEngineAuto = stringResource(R.string.settings_ocr_engine_auto),
            ocrEngineAutoDescription = stringResource(R.string.settings_ocr_engine_auto_description),
            ocrEngineMlKit = stringResource(R.string.settings_ocr_engine_mlkit),
            ocrEngineMlKitDescription = stringResource(R.string.settings_ocr_engine_mlkit_description),
            ocrEnginePaddle = stringResource(R.string.settings_ocr_engine_paddle),
            ocrEnginePaddleDescription = stringResource(R.string.settings_ocr_engine_paddle_description),
            learnedMappingsCount = stringResource(R.string.settings_learned_mappings_count),
            noLearnedMappings = stringResource(R.string.settings_no_learned_mappings),
            clearLearnedMappingsAction = stringResource(R.string.settings_clear_learned_mappings_action),
            clearLearnedMappingsTitle = stringResource(R.string.settings_clear_learned_mappings_title),
            clearLearnedMappingsMessage = stringResource(R.string.settings_clear_learned_mappings_message),
            appVersion = stringResource(R.string.settings_app_version),
            localStorage = stringResource(R.string.settings_local_storage),
            localStorageValue = stringResource(R.string.settings_local_storage_value),
            aboutBody = stringResource(R.string.settings_about_body),
        ),
        importer = ImportStrings(
            title = stringResource(R.string.importer_title),
            subtitle = stringResource(R.string.importer_subtitle),
            supportedFormatHint = stringResource(R.string.importer_supported_format_hint),
            enrichmentTitle = stringResource(R.string.importer_enrichment_title),
            enrichmentSubtitle = stringResource(R.string.importer_enrichment_subtitle),
            rawInputLabel = stringResource(R.string.importer_raw_input_label),
            rawInputPlaceholder = stringResource(R.string.importer_raw_input_placeholder),
            actionParseText = stringResource(R.string.importer_action_parse_text),
            actionParseSupplierText = stringResource(R.string.importer_action_parse_supplier_text),
            actionScanQr = stringResource(R.string.importer_action_scan_qr),
            actionScanSupplierText = stringResource(R.string.importer_action_scan_supplier_text),
            actionCaptureText = stringResource(R.string.importer_action_capture_text),
            actionOpenFullEditor = stringResource(R.string.importer_action_open_full_editor),
            recognizedTitle = stringResource(R.string.importer_recognized_title),
            recognizedSubtitle = stringResource(R.string.importer_recognized_subtitle),
            sourceLabel = stringResource(R.string.importer_source_label),
            referenceNameLabel = stringResource(R.string.importer_reference_name_label),
            modelLabel = stringResource(R.string.importer_model_label),
            brandLabel = stringResource(R.string.importer_brand_label),
            vendorLabel = stringResource(R.string.importer_vendor_label),
            modelFamilyLabel = stringResource(R.string.importer_model_family_label),
            recognitionConfidenceLabel = stringResource(R.string.importer_recognition_confidence_label),
            matchedByLabel = stringResource(R.string.importer_matched_by_label),
            importDetailTitle = stringResource(R.string.importer_import_detail_title),
            quantityHint = stringResource(R.string.importer_quantity_hint),
            importPreviewTitle = stringResource(R.string.importer_import_preview_title),
            parseFirstError = stringResource(R.string.importer_parse_first_error),
            scanFailedPattern = stringResource(R.string.importer_scan_failed_pattern),
            scannerPermissionTitle = stringResource(R.string.importer_scanner_permission_title),
            scannerPermissionDescription = stringResource(R.string.importer_scanner_permission_description),
            scannerPermissionDeniedTitle = stringResource(R.string.importer_scanner_permission_denied_title),
            scannerPermissionDeniedDescription = stringResource(R.string.importer_scanner_permission_denied_description),
            scannerStarting = stringResource(R.string.importer_scanner_starting),
            scannerScanningHint = stringResource(R.string.importer_scanner_scanning_hint),
            scannerFailedTitle = stringResource(R.string.importer_scanner_failed_title),
            scannerFailedDescription = stringResource(R.string.importer_scanner_failed_description),
            supplierScannerStarting = stringResource(R.string.importer_supplier_scanner_starting),
            supplierScannerHint = stringResource(R.string.importer_supplier_scanner_hint),
            supplierScannerCaptureHint = stringResource(R.string.importer_supplier_scanner_capture_hint),
            supplierScannerRecognizing = stringResource(R.string.importer_supplier_scanner_recognizing),
            supplierScannerFailedTitle = stringResource(R.string.importer_supplier_scanner_failed_title),
            supplierScanFailedDescription = stringResource(R.string.importer_supplier_scan_failed_description),
            supplierScanNoTextDescription = stringResource(R.string.importer_supplier_scan_no_text_description),
            supplierScannerFrameUnavailable = stringResource(R.string.importer_supplier_scanner_frame_unavailable),
            supplierScannerEnginePattern = stringResource(R.string.importer_supplier_scanner_engine_pattern),
            actionGrantCameraAccess = stringResource(R.string.importer_action_grant_camera_access),
            actionRetryScan = stringResource(R.string.importer_action_retry_scan),
            actionReturnToImport = stringResource(R.string.importer_action_return_to_import),
            actionUseReferenceName = stringResource(R.string.importer_action_use_reference_name),
            parseSupplierTextError = stringResource(R.string.importer_parse_supplier_text_error),
            nameConfirmationHint = stringResource(R.string.importer_name_confirmation_hint),
            localRulesOnly = stringResource(R.string.importer_local_rules_only),
            learningMatchSku = stringResource(R.string.importer_learning_match_sku),
            learningMatchMpn = stringResource(R.string.importer_learning_match_mpn),
            serverLookupDisabled = stringResource(R.string.importer_server_lookup_disabled),
            lookupOnlyFillsMissing = stringResource(R.string.importer_lookup_only_fills_missing),
            lookupLoading = stringResource(R.string.importer_lookup_loading),
            lookupSuccess = stringResource(R.string.importer_lookup_success),
            lookupCacheSuccess = stringResource(R.string.importer_lookup_cache_success),
            lookupNoMatch = stringResource(R.string.importer_lookup_no_match),
            lookupNotConfigured = stringResource(R.string.importer_lookup_not_configured),
            lookupFailedPattern = stringResource(R.string.importer_lookup_failed_pattern),
            fieldOriginPattern = stringResource(R.string.importer_field_origin_pattern),
            fieldOriginParsed = stringResource(R.string.importer_field_origin_parsed),
            fieldOriginRule = stringResource(R.string.importer_field_origin_rule),
            fieldOriginLearned = stringResource(R.string.importer_field_origin_learned),
            fieldOriginServer = stringResource(R.string.importer_field_origin_server),
            fieldOriginUser = stringResource(R.string.importer_field_origin_user),
            labelPreviewTitle = stringResource(R.string.importer_label_preview_title),
            labelPreviewSubtitle = stringResource(R.string.importer_label_preview_subtitle),
            labelPayloadTitle = stringResource(R.string.importer_label_payload_title),
            labelPayloadSubtitle = stringResource(R.string.importer_label_payload_subtitle),
            labelFormatTitle = stringResource(R.string.importer_label_format_title),
            labelFormatSubtitle = stringResource(R.string.importer_label_format_subtitle),
            labelSize10x40Qr = stringResource(R.string.importer_label_size_10x40_qr),
            labelSize30x40Qr = stringResource(R.string.importer_label_size_30x40_qr),
            labelSizeTextOnly = stringResource(R.string.importer_label_size_text_only),
            labelFormatQrSummaryPattern = stringResource(R.string.importer_label_format_qr_summary_pattern),
            labelFormatTextSummaryPattern = stringResource(R.string.importer_label_format_text_summary_pattern),
            labelPayloadModeTitle = stringResource(R.string.importer_label_payload_mode_title),
            labelPayloadModeNone = stringResource(R.string.importer_label_payload_mode_none),
            labelPayloadModeCompact = stringResource(R.string.importer_label_payload_mode_compact),
            labelPayloadModeStandard = stringResource(R.string.importer_label_payload_mode_standard),
            labelPayloadModeJlc = stringResource(R.string.importer_label_payload_mode_jlc),
            labelCompanionToggle = stringResource(R.string.importer_label_companion_toggle),
            labelCompanionHint = stringResource(R.string.importer_label_companion_hint),
            textLabelTemplateTitle = stringResource(R.string.importer_text_label_template_title),
            textLabelTemplateSubtitle = stringResource(R.string.importer_text_label_template_subtitle),
            textLabelTemplateNameSku = stringResource(R.string.importer_text_label_template_name_sku),
            textLabelTemplateNamePackageSku = stringResource(R.string.importer_text_label_template_name_package_sku),
            textLabelTemplateNameModel = stringResource(R.string.importer_text_label_template_name_model),
            labelTextContentTitle = stringResource(R.string.importer_label_text_content_title),
            labelFooterText = stringResource(R.string.importer_label_footer_text),
            exportSectionTitle = stringResource(R.string.importer_export_section_title),
            labelCopiesLabel = stringResource(R.string.importer_label_copies_label),
            actionExportPng = stringResource(R.string.importer_action_export_png),
            actionExportPdf = stringResource(R.string.importer_action_export_pdf),
            exportPngSuccess = stringResource(R.string.importer_export_png_success),
            exportPdfSuccess = stringResource(R.string.importer_export_pdf_success),
            exportGenericError = stringResource(R.string.importer_export_generic_error),
            exportFailedPattern = stringResource(R.string.importer_export_failed_pattern),
        ),
        forms = FormsStrings(
            addComponentTitle = stringResource(R.string.dialog_add_component_title),
            editComponentTitle = stringResource(R.string.dialog_edit_component_title),
            invalidQuantityMinStock = stringResource(R.string.dialog_invalid_quantity_min_stock),
            recordStockMovementTitle = stringResource(R.string.dialog_record_stock_movement_title),
            componentBasicTitle = stringResource(R.string.component_form_basic_title),
            componentBasicSubtitle = stringResource(R.string.component_form_basic_subtitle),
            componentStockTitle = stringResource(R.string.component_form_stock_title),
            componentStorageTitle = stringResource(R.string.component_form_storage_title),
            movementScopeTitle = stringResource(R.string.movement_form_scope_title),
            movementScopeSubtitle = stringResource(R.string.movement_form_scope_subtitle),
            movementEntryTitle = stringResource(R.string.movement_form_entry_title),
            deleteConfirmTitle = stringResource(R.string.delete_component_confirm_title),
            deleteConfirmMessage = stringResource(R.string.delete_component_confirm_message),
            movementEditorInstruction = stringResource(R.string.movement_editor_instruction),
            componentRequiredFields = stringResource(R.string.sync_component_required_fields),
            componentNonNegative = stringResource(R.string.sync_component_non_negative),
            chooseComponentTypeReason = stringResource(R.string.sync_choose_component_type_reason),
            adjustmentNonZero = stringResource(R.string.sync_adjustment_non_zero),
            movementQuantityPositive = stringResource(R.string.sync_movement_quantity_positive),
        ),
    )
}

private fun formatPattern(
    pattern: String,
    vararg args: Any,
): String = String.format(Locale.getDefault(), pattern, *args)
