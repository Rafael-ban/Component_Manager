import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../models.dart';

class SettingsStore {
  static const _deviceIdKey = 'device_id';
  static const _serverBaseUrlKey = 'server_base_url';
  static const _apiTokenKey = 'api_token';
  static const _autoSyncEnabledKey = 'auto_sync_enabled';
  static const _lastSyncedAtKey = 'last_synced_at';
  static const _lastSyncMessageKey = 'last_sync_message';

  final Uuid _uuid = const Uuid();

  Future<SyncConfiguration> load() async {
    final preferences = await SharedPreferences.getInstance();
    var deviceId = preferences.getString(_deviceIdKey);
    if (deviceId == null || deviceId.isEmpty) {
      deviceId = _uuid.v4();
      await preferences.setString(_deviceIdKey, deviceId);
    }

    final lastSyncedAtValue = preferences.getString(_lastSyncedAtKey);
    return SyncConfiguration(
      deviceId: deviceId,
      serverBaseUrl: preferences.getString(_serverBaseUrlKey) ?? '',
      apiToken: preferences.getString(_apiTokenKey) ?? '',
      autoSyncEnabled: preferences.getBool(_autoSyncEnabledKey) ?? false,
      lastSyncedAt: lastSyncedAtValue == null || lastSyncedAtValue.isEmpty
          ? null
          : DateTime.tryParse(lastSyncedAtValue)?.toUtc(),
      lastSyncMessage:
          preferences.getString(_lastSyncMessageKey) ?? 'Sync not configured',
    );
  }

  Future<void> save(SyncConfiguration configuration) async {
    final preferences = await SharedPreferences.getInstance();
    await preferences.setString(_deviceIdKey, configuration.deviceId);
    await preferences.setString(
      _serverBaseUrlKey,
      configuration.serverBaseUrl.trim(),
    );
    await preferences.setString(_apiTokenKey, configuration.apiToken.trim());
    await preferences.setBool(
      _autoSyncEnabledKey,
      configuration.autoSyncEnabled,
    );
    if (configuration.lastSyncedAt != null) {
      await preferences.setString(
        _lastSyncedAtKey,
        configuration.lastSyncedAt!.toUtc().toIso8601String(),
      );
    } else {
      await preferences.remove(_lastSyncedAtKey);
    }
    await preferences.setString(
      _lastSyncMessageKey,
      configuration.lastSyncMessage,
    );
  }

  Future<void> updateSyncStatus({
    DateTime? syncedAt,
    required String message,
  }) async {
    final current = await load();
    await save(
      current.copyWith(
        lastSyncedAt: syncedAt,
        lastSyncMessage: message,
      ),
    );
  }
}
