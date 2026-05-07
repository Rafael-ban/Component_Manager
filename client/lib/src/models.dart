enum MovementType {
  inbound,
  outbound,
  adjustment,
}

MovementType movementTypeFromValue(String value) {
  switch (value) {
    case 'inbound':
      return MovementType.inbound;
    case 'outbound':
      return MovementType.outbound;
    case 'adjustment':
      return MovementType.adjustment;
    default:
      throw ArgumentError('Unsupported movement type: $value');
  }
}

extension MovementTypeX on MovementType {
  String get storageValue {
    switch (this) {
      case MovementType.inbound:
        return 'inbound';
      case MovementType.outbound:
        return 'outbound';
      case MovementType.adjustment:
        return 'adjustment';
    }
  }

  String get label {
    switch (this) {
      case MovementType.inbound:
        return 'Inbound';
      case MovementType.outbound:
        return 'Outbound';
      case MovementType.adjustment:
        return 'Adjustment';
    }
  }
}

class ComponentRecord {
  const ComponentRecord({
    required this.id,
    required this.sku,
    required this.name,
    required this.category,
    required this.packageName,
    required this.location,
    required this.description,
    required this.quantity,
    required this.minStock,
    required this.updatedAt,
    required this.deleted,
  });

  final String id;
  final String sku;
  final String name;
  final String category;
  final String packageName;
  final String location;
  final String description;
  final int quantity;
  final int minStock;
  final DateTime updatedAt;
  final bool deleted;

  bool get isLowStock => !deleted && quantity <= minStock;

  ComponentRecord copyWith({
    String? id,
    String? sku,
    String? name,
    String? category,
    String? packageName,
    String? location,
    String? description,
    int? quantity,
    int? minStock,
    DateTime? updatedAt,
    bool? deleted,
  }) {
    return ComponentRecord(
      id: id ?? this.id,
      sku: sku ?? this.sku,
      name: name ?? this.name,
      category: category ?? this.category,
      packageName: packageName ?? this.packageName,
      location: location ?? this.location,
      description: description ?? this.description,
      quantity: quantity ?? this.quantity,
      minStock: minStock ?? this.minStock,
      updatedAt: updatedAt ?? this.updatedAt,
      deleted: deleted ?? this.deleted,
    );
  }

  factory ComponentRecord.fromMap(Map<String, Object?> map) {
    final deletedValue = map['deleted'];
    return ComponentRecord(
      id: map['id'] as String,
      sku: map['sku'] as String,
      name: map['name'] as String,
      category: map['category'] as String? ?? '',
      packageName: map['package_name'] as String? ?? '',
      location: map['location'] as String? ?? '',
      description: map['description'] as String? ?? '',
      quantity: (map['quantity'] as num?)?.toInt() ?? 0,
      minStock: (map['min_stock'] as num?)?.toInt() ?? 0,
      updatedAt: DateTime.parse(map['updated_at'] as String).toUtc(),
      deleted: deletedValue == true || deletedValue == 1,
    );
  }

  Map<String, Object?> toDbMap() {
    return {
      'id': id,
      'sku': sku,
      'name': name,
      'category': category,
      'package_name': packageName,
      'location': location,
      'description': description,
      'quantity': quantity,
      'min_stock': minStock,
      'updated_at': updatedAt.toUtc().toIso8601String(),
      'deleted': deleted ? 1 : 0,
    };
  }

  Map<String, Object?> toSyncJson() {
    return {
      'id': id,
      'sku': sku,
      'name': name,
      'category': category,
      'package_name': packageName,
      'location': location,
      'description': description,
      'quantity': quantity,
      'min_stock': minStock,
      'updated_at': updatedAt.toUtc().toIso8601String(),
      'deleted': deleted,
    };
  }
}

class StockMovementRecord {
  const StockMovementRecord({
    required this.id,
    required this.componentId,
    required this.movementType,
    required this.quantity,
    required this.reason,
    required this.note,
    required this.happenedAt,
    required this.updatedAt,
    required this.deleted,
    this.componentName,
    this.componentSku,
  });

  final String id;
  final String componentId;
  final MovementType movementType;
  final int quantity;
  final String reason;
  final String note;
  final DateTime happenedAt;
  final DateTime updatedAt;
  final bool deleted;
  final String? componentName;
  final String? componentSku;

  StockMovementRecord copyWith({
    String? id,
    String? componentId,
    MovementType? movementType,
    int? quantity,
    String? reason,
    String? note,
    DateTime? happenedAt,
    DateTime? updatedAt,
    bool? deleted,
    String? componentName,
    String? componentSku,
  }) {
    return StockMovementRecord(
      id: id ?? this.id,
      componentId: componentId ?? this.componentId,
      movementType: movementType ?? this.movementType,
      quantity: quantity ?? this.quantity,
      reason: reason ?? this.reason,
      note: note ?? this.note,
      happenedAt: happenedAt ?? this.happenedAt,
      updatedAt: updatedAt ?? this.updatedAt,
      deleted: deleted ?? this.deleted,
      componentName: componentName ?? this.componentName,
      componentSku: componentSku ?? this.componentSku,
    );
  }

  factory StockMovementRecord.fromMap(Map<String, Object?> map) {
    final deletedValue = map['deleted'];
    return StockMovementRecord(
      id: map['id'] as String,
      componentId: map['component_id'] as String,
      movementType: movementTypeFromValue(map['movement_type'] as String),
      quantity: (map['quantity'] as num?)?.toInt() ?? 0,
      reason: map['reason'] as String? ?? '',
      note: map['note'] as String? ?? '',
      happenedAt: DateTime.parse(map['happened_at'] as String).toUtc(),
      updatedAt: DateTime.parse(map['updated_at'] as String).toUtc(),
      deleted: deletedValue == true || deletedValue == 1,
      componentName: map['component_name'] as String?,
      componentSku: map['component_sku'] as String?,
    );
  }

  Map<String, Object?> toDbMap() {
    return {
      'id': id,
      'component_id': componentId,
      'movement_type': movementType.storageValue,
      'quantity': quantity,
      'reason': reason,
      'note': note,
      'happened_at': happenedAt.toUtc().toIso8601String(),
      'updated_at': updatedAt.toUtc().toIso8601String(),
      'deleted': deleted ? 1 : 0,
    };
  }

  Map<String, Object?> toSyncJson() {
    return {
      'id': id,
      'component_id': componentId,
      'movement_type': movementType.storageValue,
      'quantity': quantity,
      'reason': reason,
      'note': note,
      'happened_at': happenedAt.toUtc().toIso8601String(),
      'updated_at': updatedAt.toUtc().toIso8601String(),
      'deleted': deleted,
    };
  }
}

class SyncConfiguration {
  const SyncConfiguration({
    required this.deviceId,
    required this.serverBaseUrl,
    required this.apiToken,
    required this.autoSyncEnabled,
    required this.lastSyncMessage,
    this.lastSyncedAt,
  });

  final String deviceId;
  final String serverBaseUrl;
  final String apiToken;
  final bool autoSyncEnabled;
  final DateTime? lastSyncedAt;
  final String lastSyncMessage;

  bool get isConfigured =>
      serverBaseUrl.trim().isNotEmpty && apiToken.trim().isNotEmpty;

  SyncConfiguration copyWith({
    String? deviceId,
    String? serverBaseUrl,
    String? apiToken,
    bool? autoSyncEnabled,
    DateTime? lastSyncedAt,
    bool clearLastSyncedAt = false,
    String? lastSyncMessage,
  }) {
    return SyncConfiguration(
      deviceId: deviceId ?? this.deviceId,
      serverBaseUrl: serverBaseUrl ?? this.serverBaseUrl,
      apiToken: apiToken ?? this.apiToken,
      autoSyncEnabled: autoSyncEnabled ?? this.autoSyncEnabled,
      lastSyncedAt: clearLastSyncedAt
          ? null
          : lastSyncedAt ?? this.lastSyncedAt,
      lastSyncMessage: lastSyncMessage ?? this.lastSyncMessage,
    );
  }

  factory SyncConfiguration.empty() {
    return const SyncConfiguration(
      deviceId: '',
      serverBaseUrl: '',
      apiToken: '',
      autoSyncEnabled: false,
      lastSyncMessage: 'Sync not configured',
    );
  }
}

class DashboardSnapshot {
  const DashboardSnapshot({
    required this.componentCount,
    required this.totalUnits,
    required this.lowStockCount,
  });

  final int componentCount;
  final int totalUnits;
  final int lowStockCount;
}

class PendingSyncPacket {
  const PendingSyncPacket({
    required this.queueIds,
    required this.components,
    required this.stockMovements,
  });

  final List<int> queueIds;
  final List<ComponentRecord> components;
  final List<StockMovementRecord> stockMovements;

  bool get hasChanges => queueIds.isNotEmpty;
}

class SyncOutcome {
  const SyncOutcome({
    required this.pushedComponents,
    required this.pushedStockMovements,
    required this.pulledComponents,
    required this.pulledStockMovements,
  });

  final int pushedComponents;
  final int pushedStockMovements;
  final int pulledComponents;
  final int pulledStockMovements;

  String get message {
    return 'Sync complete. '
        'Push C:$pushedComponents M:$pushedStockMovements, '
        'Pull C:$pulledComponents M:$pulledStockMovements.';
  }
}

