import 'dart:async';

import 'package:flutter/foundation.dart';

import 'data/local_store.dart';
import 'data/settings_store.dart';
import 'models.dart';
import 'services/sync_service.dart';

class AppController extends ChangeNotifier {
  AppController({
    required this.localStore,
    required this.settingsStore,
    required this.syncService,
  });

  final LocalStore localStore;
  final SettingsStore settingsStore;
  final SyncService syncService;

  List<ComponentRecord> components = const [];
  List<StockMovementRecord> stockMovements = const [];
  DashboardSnapshot dashboard = const DashboardSnapshot(
    componentCount: 0,
    totalUnits: 0,
    lowStockCount: 0,
  );
  SyncConfiguration syncConfiguration = SyncConfiguration.empty();

  bool isReady = false;
  bool isRefreshing = false;
  bool isSyncing = false;

  Timer? _autoSyncTicker;
  Timer? _localChangeSyncDebounce;

  Future<void> initialize() async {
    await localStore.init();
    await _reload();
    _restartAutoSyncTicker();
    isReady = true;
    notifyListeners();

    if (syncConfiguration.autoSyncEnabled && syncConfiguration.isConfigured) {
      unawaited(syncNow(silent: true));
    }
  }

  Future<void> refresh() async {
    if (isRefreshing) {
      return;
    }

    isRefreshing = true;
    notifyListeners();
    try {
      await _reload();
    } finally {
      isRefreshing = false;
      notifyListeners();
    }
  }

  Future<void> saveComponent(ComponentRecord component) async {
    await localStore.upsertComponent(component);
    await _reload();
    notifyListeners();
    _scheduleDeferredSync();
  }

  Future<void> removeComponent(String componentId) async {
    await localStore.softDeleteComponent(componentId);
    await _reload();
    notifyListeners();
    _scheduleDeferredSync();
  }

  Future<void> saveStockMovement(StockMovementRecord movement) async {
    await localStore.recordMovement(movement);
    await _reload();
    notifyListeners();
    _scheduleDeferredSync();
  }

  Future<void> saveSyncConfiguration(SyncConfiguration configuration) async {
    await settingsStore.save(configuration);
    await _reload();
    _restartAutoSyncTicker();
    notifyListeners();

    if (syncConfiguration.autoSyncEnabled && syncConfiguration.isConfigured) {
      unawaited(syncNow(silent: true));
    }
  }

  Future<String> testConnection() async {
    final message = await syncService.testConnection();
    await _reload();
    notifyListeners();
    return message;
  }

  Future<void> syncNow({bool silent = false}) async {
    if (isSyncing) {
      return;
    }
    if (!syncConfiguration.isConfigured) {
      if (silent) {
        return;
      }
      throw StateError('Configure server URL and API token first.');
    }

    isSyncing = true;
    notifyListeners();
    try {
      await syncService.syncNow();
      await _reload();
    } finally {
      isSyncing = false;
      notifyListeners();
    }
  }

  Future<void> _reload() async {
    components = await localStore.listComponents();
    stockMovements = await localStore.listMovements();
    dashboard = await localStore.loadDashboardSnapshot();
    syncConfiguration = await settingsStore.load();
  }

  void _restartAutoSyncTicker() {
    _autoSyncTicker?.cancel();
    if (!syncConfiguration.autoSyncEnabled || !syncConfiguration.isConfigured) {
      return;
    }

    _autoSyncTicker = Timer.periodic(
      const Duration(minutes: 1),
      (_) => unawaited(syncNow(silent: true)),
    );
  }

  void _scheduleDeferredSync() {
    if (!syncConfiguration.autoSyncEnabled || !syncConfiguration.isConfigured) {
      return;
    }

    _localChangeSyncDebounce?.cancel();
    _localChangeSyncDebounce = Timer(
      const Duration(seconds: 2),
      () => unawaited(syncNow(silent: true)),
    );
  }

  @override
  void dispose() {
    _autoSyncTicker?.cancel();
    _localChangeSyncDebounce?.cancel();
    super.dispose();
  }
}

