import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../models.dart';

class LocalStore {
  Database? _database;

  static void configureDatabaseFactory() {
    if (!kIsWeb &&
        (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }
  }

  Future<void> init() async {
    await database;
  }

  Future<Database> get database async {
    _database ??= await _open();
    return _database!;
  }

  Future<List<ComponentRecord>> listComponents() async {
    final db = await database;
    final rows = await db.query(
      'components',
      where: 'deleted = 0',
      orderBy: 'name COLLATE NOCASE ASC',
    );
    return rows.map(ComponentRecord.fromMap).toList();
  }

  Future<List<StockMovementRecord>> listMovements({int limit = 120}) async {
    final db = await database;
    final rows = await db.rawQuery(
      '''
      SELECT
        m.*,
        c.name AS component_name,
        c.sku AS component_sku
      FROM stock_movements m
      LEFT JOIN components c ON c.id = m.component_id
      WHERE m.deleted = 0
      ORDER BY m.happened_at DESC
      LIMIT ?
      ''',
      [limit],
    );
    return rows.map(StockMovementRecord.fromMap).toList();
  }

  Future<DashboardSnapshot> loadDashboardSnapshot() async {
    final db = await database;
    final aggregateRows = await db.rawQuery(
      '''
      SELECT
        COUNT(*) AS component_count,
        COALESCE(SUM(quantity), 0) AS total_units
      FROM components
      WHERE deleted = 0
      ''',
    );
    final lowStockRows = await db.rawQuery(
      '''
      SELECT COUNT(*) AS low_stock_count
      FROM components
      WHERE deleted = 0 AND quantity <= min_stock
      ''',
    );

    return DashboardSnapshot(
      componentCount: _readInt(aggregateRows.first['component_count']),
      totalUnits: _readInt(aggregateRows.first['total_units']),
      lowStockCount: _readInt(lowStockRows.first['low_stock_count']),
    );
  }

  Future<void> upsertComponent(ComponentRecord component) async {
    if (component.quantity < 0) {
      throw StateError('Quantity cannot be negative.');
    }
    if (component.minStock < 0) {
      throw StateError('Minimum stock cannot be negative.');
    }

    final db = await database;
    try {
      await db.transaction((txn) async {
        await txn.insert(
          'components',
          component.toDbMap(),
          conflictAlgorithm: ConflictAlgorithm.replace,
        );
        await _enqueueSync(
          txn,
          entityType: 'component',
          entityId: component.id,
          updatedAt: component.updatedAt,
        );
      });
    } on DatabaseException catch (error) {
      if (error.isUniqueConstraintError()) {
        throw StateError('An active component with this SKU already exists.');
      }
      rethrow;
    }
  }

  Future<void> softDeleteComponent(String componentId) async {
    final db = await database;
    await db.transaction((txn) async {
      final rows = await txn.query(
        'components',
        where: 'id = ?',
        whereArgs: [componentId],
        limit: 1,
      );
      if (rows.isEmpty) {
        throw StateError('Component not found.');
      }

      final current = ComponentRecord.fromMap(rows.first);
      final deletedRecord = current.copyWith(
        deleted: true,
        updatedAt: DateTime.now().toUtc(),
      );
      await txn.update(
        'components',
        deletedRecord.toDbMap(),
        where: 'id = ?',
        whereArgs: [componentId],
      );
      await _enqueueSync(
        txn,
        entityType: 'component',
        entityId: componentId,
        updatedAt: deletedRecord.updatedAt,
      );
    });
  }

  Future<void> recordMovement(StockMovementRecord movement) async {
    final db = await database;
    await db.transaction((txn) async {
      final componentRows = await txn.query(
        'components',
        where: 'id = ? AND deleted = 0',
        whereArgs: [movement.componentId],
        limit: 1,
      );
      if (componentRows.isEmpty) {
        throw StateError('Component not found for this movement.');
      }

      final current = ComponentRecord.fromMap(componentRows.first);
      final nextQuantity = _nextQuantity(
        currentQuantity: current.quantity,
        movement: movement,
      );
      if (nextQuantity < 0) {
        throw StateError('Movement would make stock negative.');
      }

      final updatedComponent = current.copyWith(
        quantity: nextQuantity,
        updatedAt: movement.updatedAt,
      );

      await txn.insert(
        'stock_movements',
        movement.toDbMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      await txn.insert(
        'components',
        updatedComponent.toDbMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      await _enqueueSync(
        txn,
        entityType: 'stock_movement',
        entityId: movement.id,
        updatedAt: movement.updatedAt,
      );
      await _enqueueSync(
        txn,
        entityType: 'component',
        entityId: current.id,
        updatedAt: updatedComponent.updatedAt,
      );
    });
  }

  Future<PendingSyncPacket> buildPendingSyncPacket() async {
    final db = await database;
    final queueRows = await db.query('sync_queue', orderBy: 'created_at ASC');
    if (queueRows.isEmpty) {
      return const PendingSyncPacket(
        queueIds: [],
        components: [],
        stockMovements: [],
      );
    }

    final queueIds = <int>[];
    final componentIds = <String>{};
    final movementIds = <String>{};

    for (final row in queueRows) {
      queueIds.add(row['id'] as int);
      final entityType = row['entity_type'] as String;
      final entityId = row['entity_id'] as String;
      if (entityType == 'component') {
        componentIds.add(entityId);
      } else if (entityType == 'stock_movement') {
        movementIds.add(entityId);
      }
    }

    final components = componentIds.isEmpty
        ? const <ComponentRecord>[]
        : await _loadComponentsByIds(componentIds.toList());
    final stockMovements = movementIds.isEmpty
        ? const <StockMovementRecord>[]
        : await _loadMovementsByIds(movementIds.toList());

    return PendingSyncPacket(
      queueIds: queueIds,
      components: components,
      stockMovements: stockMovements,
    );
  }

  Future<void> clearQueueItems(List<int> queueIds) async {
    if (queueIds.isEmpty) {
      return;
    }

    final db = await database;
    await db.delete(
      'sync_queue',
      where: 'id IN (${_placeholders(queueIds.length)})',
      whereArgs: queueIds,
    );
  }

  Future<void> applyRemoteSnapshot({
    required List<ComponentRecord> components,
    required List<StockMovementRecord> stockMovements,
  }) async {
    final db = await database;
    await db.transaction((txn) async {
      for (final component in components) {
        await _upsertRemoteComponent(txn, component);
        await _clearOlderQueueEntries(
          txn,
          entityType: 'component',
          entityId: component.id,
          updatedAt: component.updatedAt,
        );
      }

      for (final movement in stockMovements) {
        await _upsertRemoteMovement(txn, movement);
        await _clearOlderQueueEntries(
          txn,
          entityType: 'stock_movement',
          entityId: movement.id,
          updatedAt: movement.updatedAt,
        );
      }
    });
  }

  Future<List<ComponentRecord>> _loadComponentsByIds(List<String> ids) async {
    final db = await database;
    final rows = await db.query(
      'components',
      where: 'id IN (${_placeholders(ids.length)})',
      whereArgs: ids,
    );
    return rows.map(ComponentRecord.fromMap).toList();
  }

  Future<List<StockMovementRecord>> _loadMovementsByIds(List<String> ids) async {
    final db = await database;
    final rows = await db.query(
      'stock_movements',
      where: 'id IN (${_placeholders(ids.length)})',
      whereArgs: ids,
    );
    return rows.map(StockMovementRecord.fromMap).toList();
  }

  Future<void> _upsertRemoteComponent(
    DatabaseExecutor txn,
    ComponentRecord remote,
  ) async {
    final rows = await txn.query(
      'components',
      where: 'id = ?',
      whereArgs: [remote.id],
      limit: 1,
    );
    if (rows.isNotEmpty) {
      final local = ComponentRecord.fromMap(rows.first);
      if (remote.updatedAt.isBefore(local.updatedAt)) {
        return;
      }
    }

    await txn.insert(
      'components',
      remote.toDbMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> _upsertRemoteMovement(
    DatabaseExecutor txn,
    StockMovementRecord remote,
  ) async {
    final rows = await txn.query(
      'stock_movements',
      where: 'id = ?',
      whereArgs: [remote.id],
      limit: 1,
    );
    if (rows.isNotEmpty) {
      final local = StockMovementRecord.fromMap(rows.first);
      if (remote.updatedAt.isBefore(local.updatedAt)) {
        return;
      }
    }

    await txn.insert(
      'stock_movements',
      remote.toDbMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> _clearOlderQueueEntries(
    DatabaseExecutor txn, {
    required String entityType,
    required String entityId,
    required DateTime updatedAt,
  }) async {
    await txn.delete(
      'sync_queue',
      where:
          'entity_type = ? AND entity_id = ? AND entity_updated_at <= ?',
      whereArgs: [
        entityType,
        entityId,
        updatedAt.toUtc().toIso8601String(),
      ],
    );
  }

  Future<void> _enqueueSync(
    DatabaseExecutor txn, {
    required String entityType,
    required String entityId,
    required DateTime updatedAt,
  }) async {
    await txn.insert('sync_queue', {
      'entity_type': entityType,
      'entity_id': entityId,
      'entity_updated_at': updatedAt.toUtc().toIso8601String(),
      'created_at': DateTime.now().toUtc().toIso8601String(),
    });
  }

  int _nextQuantity({
    required int currentQuantity,
    required StockMovementRecord movement,
  }) {
    switch (movement.movementType) {
      case MovementType.inbound:
        return currentQuantity + movement.quantity.abs();
      case MovementType.outbound:
        return currentQuantity - movement.quantity.abs();
      case MovementType.adjustment:
        return currentQuantity + movement.quantity;
    }
  }

  Future<Database> _open() async {
    final directory = await getApplicationSupportDirectory();
    await directory.create(recursive: true);
    final databasePath = path.join(directory.path, 'component_vault.db');

    return openDatabase(
      databasePath,
      version: 1,
      onConfigure: (db) async {
        await db.execute('PRAGMA foreign_keys = ON');
      },
      onCreate: (db, version) async {
        await db.execute(
          '''
          CREATE TABLE components (
            id TEXT PRIMARY KEY,
            sku TEXT NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            package_name TEXT NOT NULL,
            location TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            quantity INTEGER NOT NULL CHECK (quantity >= 0),
            min_stock INTEGER NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
            updated_at TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0
          )
          ''',
        );
        await db.execute(
          '''
          CREATE TABLE stock_movements (
            id TEXT PRIMARY KEY,
            component_id TEXT NOT NULL,
            movement_type TEXT NOT NULL,
            quantity INTEGER NOT NULL,
            reason TEXT NOT NULL,
            note TEXT NOT NULL DEFAULT '',
            happened_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            deleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (component_id) REFERENCES components(id)
          )
          ''',
        );
        await db.execute(
          '''
          CREATE TABLE sync_queue (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_type TEXT NOT NULL,
            entity_id TEXT NOT NULL,
            entity_updated_at TEXT NOT NULL,
            created_at TEXT NOT NULL
          )
          ''',
        );
        await db.execute(
          'CREATE INDEX idx_components_updated_at ON components(updated_at)',
        );
        await db.execute(
          '''
          CREATE UNIQUE INDEX idx_components_sku_active
          ON components(sku)
          WHERE deleted = 0
          ''',
        );
        await db.execute(
          '''
          CREATE INDEX idx_stock_movements_updated_at
          ON stock_movements(updated_at)
          ''',
        );
      },
    );
  }

  int _readInt(Object? value) {
    if (value is int) {
      return value;
    }
    if (value is num) {
      return value.toInt();
    }
    return int.tryParse(value?.toString() ?? '0') ?? 0;
  }

  String _placeholders(int count) {
    return List.filled(count, '?').join(', ');
  }
}
