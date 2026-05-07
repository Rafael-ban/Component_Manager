import 'dart:convert';

import 'package:http/http.dart' as http;

import '../data/local_store.dart';
import '../data/settings_store.dart';
import '../models.dart';

class SyncService {
  SyncService({
    required this.localStore,
    required this.settingsStore,
    http.Client? httpClient,
  }) : _httpClient = httpClient ?? http.Client();

  final LocalStore localStore;
  final SettingsStore settingsStore;
  final http.Client _httpClient;

  Future<String> testConnection() async {
    final configuration = await settingsStore.load();
    _assertConfigured(configuration);

    final response = await _httpClient.post(
      _buildUri(configuration, '/auth/ping'),
      headers: _headers(configuration, json: true),
    );
    _ensureSuccess(response);

    await settingsStore.updateSyncStatus(
      message: 'Connection successful',
      syncedAt: configuration.lastSyncedAt,
    );
    return 'Connection successful';
  }

  Future<SyncOutcome> syncNow() async {
    final configuration = await settingsStore.load();
    _assertConfigured(configuration);

    try {
      final pendingPacket = await localStore.buildPendingSyncPacket();

      var pushedComponents = 0;
      var pushedStockMovements = 0;
      if (pendingPacket.hasChanges) {
        final pushResponse = await _httpClient.post(
          _buildUri(configuration, '/sync/push'),
          headers: _headers(configuration, json: true),
          body: jsonEncode({
            'device_id': configuration.deviceId,
            'components': pendingPacket.components
                .map((item) => item.toSyncJson())
                .toList(),
            'stock_movements': pendingPacket.stockMovements
                .map((item) => item.toSyncJson())
                .toList(),
          }),
        );
        _ensureSuccess(pushResponse);

        final pushBody = jsonDecode(pushResponse.body) as Map<String, dynamic>;
        pushedComponents =
            (pushBody['accepted_components'] as num?)?.toInt() ??
            pendingPacket.components.length;
        pushedStockMovements =
            (pushBody['accepted_stock_movements'] as num?)?.toInt() ??
            pendingPacket.stockMovements.length;

        await localStore.clearQueueItems(pendingPacket.queueIds);
      }

      final queryParameters = <String, String>{};
      if (configuration.lastSyncedAt != null) {
        queryParameters['since'] =
            configuration.lastSyncedAt!.toUtc().toIso8601String();
      }

      final pullResponse = await _httpClient.get(
        _buildUri(
          configuration,
          '/sync/pull',
          queryParameters: queryParameters,
        ),
        headers: _headers(configuration),
      );
      _ensureSuccess(pullResponse);

      final pullBody = jsonDecode(pullResponse.body) as Map<String, dynamic>;
      final pulledComponents = ((pullBody['components'] as List<dynamic>?) ?? [])
          .cast<Map<String, dynamic>>()
          .map(ComponentRecord.fromMap)
          .toList();
      final pulledStockMovements =
          ((pullBody['stock_movements'] as List<dynamic>?) ?? [])
              .cast<Map<String, dynamic>>()
              .map(StockMovementRecord.fromMap)
              .toList();

      await localStore.applyRemoteSnapshot(
        components: pulledComponents,
        stockMovements: pulledStockMovements,
      );

      final outcome = SyncOutcome(
        pushedComponents: pushedComponents,
        pushedStockMovements: pushedStockMovements,
        pulledComponents: pulledComponents.length,
        pulledStockMovements: pulledStockMovements.length,
      );

      await settingsStore.updateSyncStatus(
        syncedAt: _parseServerTime(pullBody['server_time']) ?? DateTime.now().toUtc(),
        message: outcome.message,
      );
      return outcome;
    } catch (error) {
      await settingsStore.updateSyncStatus(
        message: 'Sync failed: $error',
      );
      rethrow;
    }
  }

  Uri _buildUri(
    SyncConfiguration configuration,
    String path, {
    Map<String, String>? queryParameters,
  }) {
    final baseUrl = configuration.serverBaseUrl.trim().replaceAll(RegExp(r'/$'), '');
    return Uri.parse('$baseUrl$path').replace(
      queryParameters: queryParameters == null || queryParameters.isEmpty
          ? null
          : queryParameters,
    );
  }

  Map<String, String> _headers(
    SyncConfiguration configuration, {
    bool json = false,
  }) {
    return {
      'Authorization': 'Bearer ${configuration.apiToken.trim()}',
      if (json) 'Content-Type': 'application/json',
    };
  }

  DateTime? _parseServerTime(Object? rawValue) {
    if (rawValue is String) {
      return DateTime.tryParse(rawValue)?.toUtc();
    }
    return null;
  }

  void _assertConfigured(SyncConfiguration configuration) {
    if (!configuration.isConfigured) {
      throw StateError('Configure server URL and API token first.');
    }
  }

  void _ensureSuccess(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    String detail = response.reasonPhrase ?? 'Unexpected error';
    try {
      final body = jsonDecode(response.body) as Map<String, dynamic>;
      final parsedDetail = body['detail'];
      if (parsedDetail is String && parsedDetail.isNotEmpty) {
        detail = parsedDetail;
      }
    } catch (_) {
      if (response.body.isNotEmpty) {
        detail = response.body;
      }
    }

    throw StateError('HTTP ${response.statusCode}: $detail');
  }
}

