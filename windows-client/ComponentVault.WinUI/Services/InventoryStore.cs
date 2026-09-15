using System.Globalization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services.Bom;
using ComponentVault.WinUI.Services.Migration;
using Microsoft.Data.Sqlite;

namespace ComponentVault.WinUI.Services;

public sealed class InventoryStore
{
    private readonly string _databasePath;

    public InventoryStore()
        : this(
            Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "ComponentVault",
                "component-vault.db"
            )
        )
    {
    }

    internal InventoryStore(string databasePath)
    {
        var root = Path.GetDirectoryName(databasePath);
        if (!string.IsNullOrWhiteSpace(root))
        {
            Directory.CreateDirectory(root);
        }
        _databasePath = databasePath;
    }

    public void Initialize()
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        ExecuteSchema(connection);
        EnsureDefaultSettings(connection);

        transaction.Commit();
    }

    public DashboardSnapshot GetDashboardSnapshot()
    {
        using var connection = OpenConnection();

        var command = connection.CreateCommand();
        command.CommandText =
            """
            SELECT
                COUNT(*) AS component_count,
                COALESCE(SUM(quantity), 0) AS total_units,
                COALESCE(SUM(CASE WHEN quantity <= min_stock THEN 1 ELSE 0 END), 0) AS low_stock_count
            FROM components
            WHERE deleted = 0
            """;

        using var reader = command.ExecuteReader();
        reader.Read();

        return new DashboardSnapshot
        {
            ComponentCount = reader.GetInt32(0),
            TotalUnits = reader.GetInt32(1),
            LowStockCount = reader.GetInt32(2),
            MovementCount = GetMovementCount(connection),
        };
    }

    public IReadOnlyList<ComponentRecord> GetComponents()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText =
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
            """;

        using var reader = command.ExecuteReader();
        return ReadComponents(reader);
    }

    public IReadOnlyList<ComponentRecord> GetLowStockComponents()
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText =
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
            """;

        using var reader = command.ExecuteReader();
        return ReadComponents(reader);
    }

    public IReadOnlyList<ComponentRecord> GetActiveComponentsForSelection()
    {
        return GetComponents();
    }

    public IReadOnlyList<StockMovementRecord> GetMovements(int limit = 200)
    {
        using var connection = OpenConnection();
        using var command = connection.CreateCommand();
        command.CommandText =
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
            LIMIT $limit
            """;
        command.Parameters.AddWithValue("$limit", limit);

        using var reader = command.ExecuteReader();
        return ReadMovements(reader);
    }

    public SyncConfiguration GetSyncConfiguration()
    {
        using var connection = OpenConnection();
        EnsureDefaultSettings(connection);
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            SELECT
                device_id,
                server_base_url,
                api_token,
                auto_sync_enabled,
                COALESCE(last_synced_at, '') AS last_synced_at,
                COALESCE(last_sync_message, '尚未同步。') AS last_sync_message
            FROM sync_settings
            WHERE id = 1
            """;

        using var reader = command.ExecuteReader();
        reader.Read();

        return new SyncConfiguration
        {
            DeviceId = reader.GetString(0),
            ServerBaseUrl = reader.GetString(1),
            ApiToken = reader.GetString(2),
            AutoSyncEnabled = reader.GetInt32(3) == 1,
            LastSyncedAt = string.IsNullOrWhiteSpace(reader.GetString(4))
                ? "从未同步"
                : reader.GetString(4),
            LastSyncMessage = reader.GetString(5),
        };
    }

    public OperationResult SaveSyncConfiguration(
        string serverBaseUrl,
        string apiToken,
        bool autoSyncEnabled
    )
    {
        using var connection = OpenConnection();
        EnsureDefaultSettings(connection);

        var normalizedUrl = NormalizeServerBaseUrl(serverBaseUrl);
        var normalizedToken = apiToken.Trim();

        using var command = connection.CreateCommand();
        command.CommandText =
            """
            UPDATE sync_settings
            SET
                server_base_url = $server_base_url,
                api_token = $api_token,
                auto_sync_enabled = $auto_sync_enabled,
                last_sync_cursor = CASE
                    WHEN server_base_url = $server_base_url THEN last_sync_cursor
                    ELSE NULL
                END
            WHERE id = 1
            """;
        command.Parameters.AddWithValue("$server_base_url", normalizedUrl);
        command.Parameters.AddWithValue("$api_token", normalizedToken);
        command.Parameters.AddWithValue("$auto_sync_enabled", autoSyncEnabled ? 1 : 0);
        command.ExecuteNonQuery();

        return OperationResult.Success("同步设置已保存到本机。");
    }

    public ComponentRecord SaveComponent(ComponentDraft draft)
    {
        ValidateComponentDraft(draft);

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        EnsureUniqueActiveSku(connection, draft.Sku.Trim(), draft.Id);

        var componentId = draft.Id ?? $"cmp-{Guid.NewGuid():N}";
        var updatedAt = UtcNow();

        using var command = connection.CreateCommand();
        command.CommandText =
            """
            INSERT INTO components (
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
            ) VALUES (
                $id,
                $sku,
                $name,
                $category,
                $package_name,
                $location,
                $description,
                $quantity,
                $min_stock,
                $updated_at,
                0
            )
            ON CONFLICT(id) DO UPDATE SET
                sku = excluded.sku,
                name = excluded.name,
                category = excluded.category,
                package_name = excluded.package_name,
                location = excluded.location,
                description = excluded.description,
                quantity = excluded.quantity,
                min_stock = excluded.min_stock,
                updated_at = excluded.updated_at,
                deleted = 0
            """;
        command.Parameters.AddWithValue("$id", componentId);
        command.Parameters.AddWithValue("$sku", draft.Sku.Trim());
        command.Parameters.AddWithValue("$name", draft.Name.Trim());
        command.Parameters.AddWithValue("$category", draft.Category.Trim());
        command.Parameters.AddWithValue("$package_name", draft.PackageName.Trim());
        command.Parameters.AddWithValue("$location", draft.Location.Trim());
        command.Parameters.AddWithValue("$description", draft.Description.Trim());
        command.Parameters.AddWithValue("$quantity", draft.Quantity);
        command.Parameters.AddWithValue("$min_stock", draft.MinStock);
        command.Parameters.AddWithValue("$updated_at", updatedAt);
        command.ExecuteNonQuery();

        EnqueueEntity(connection, "component", componentId, updatedAt);

        transaction.Commit();
        return GetComponentById(componentId);
    }

    public OperationResult SoftDeleteComponent(string componentId)
    {
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        if (!ComponentExists(connection, componentId))
        {
            return OperationResult.Failure("请先选择一个要删除的元器件。");
        }

        var updatedAt = UtcNow();
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            UPDATE components
            SET deleted = 1, updated_at = $updated_at
            WHERE id = $id
            """;
        command.Parameters.AddWithValue("$id", componentId);
        command.Parameters.AddWithValue("$updated_at", updatedAt);
        command.ExecuteNonQuery();

        EnqueueEntity(connection, "component", componentId, updatedAt);
        transaction.Commit();
        return OperationResult.Success("元器件已在本机软删除。");
    }

    public OperationResult RecordMovement(MovementEntryDraft draft)
    {
        ValidateMovementDraft(draft);

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        var component = GetComponentById(connection, draft.ComponentId);
        if (component is null || component.Deleted)
        {
            return OperationResult.Failure("请先选择一个可用元器件。");
        }

        var quantityDelta = CalculateQuantityDelta(draft.MovementType, draft.Quantity);
        var newQuantity = component.Quantity + quantityDelta;
        if (newQuantity < 0)
        {
            return OperationResult.Failure("这次变动会让库存变成负数。");
        }

        var happenedAt = UtcNow();
        var movementId = $"mov-{Guid.NewGuid():N}";
        using var insertMovement = connection.CreateCommand();
        insertMovement.CommandText =
            """
            INSERT INTO stock_movements (
                id,
                component_id,
                movement_type,
                quantity,
                reason,
                note,
                happened_at,
                updated_at,
                deleted
            ) VALUES (
                $id,
                $component_id,
                $movement_type,
                $quantity,
                $reason,
                $note,
                $happened_at,
                $updated_at,
                0
            )
            """;
        insertMovement.Parameters.AddWithValue("$id", movementId);
        insertMovement.Parameters.AddWithValue("$component_id", draft.ComponentId);
        insertMovement.Parameters.AddWithValue(
            "$movement_type",
            draft.MovementType.Trim().ToLowerInvariant()
        );
        insertMovement.Parameters.AddWithValue(
            "$quantity",
            NormalizeMovementQuantity(draft.MovementType, draft.Quantity)
        );
        insertMovement.Parameters.AddWithValue("$reason", draft.Reason.Trim());
        insertMovement.Parameters.AddWithValue("$note", draft.Note.Trim());
        insertMovement.Parameters.AddWithValue("$happened_at", happenedAt);
        insertMovement.Parameters.AddWithValue("$updated_at", happenedAt);
        insertMovement.ExecuteNonQuery();

        using var updateComponent = connection.CreateCommand();
        updateComponent.CommandText =
            """
            UPDATE components
            SET quantity = $quantity, updated_at = $updated_at
            WHERE id = $id
            """;
        updateComponent.Parameters.AddWithValue("$quantity", newQuantity);
        updateComponent.Parameters.AddWithValue("$updated_at", happenedAt);
        updateComponent.Parameters.AddWithValue("$id", draft.ComponentId);
        updateComponent.ExecuteNonQuery();

        EnqueueEntity(connection, "component", draft.ComponentId, happenedAt);
        EnqueueEntity(connection, "stock_movement", movementId, happenedAt);

        transaction.Commit();
        return OperationResult.Success("库存变动已记录到本机。");
    }

    public BomConfirmResult ConfirmBomConsumption(BomConfirmRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.ReleaseId) || string.IsNullOrWhiteSpace(request.BatchId))
        {
            return BomConfirmResult.Failure("发行 ID 与批次 ID 不能为空。");
        }
        if (string.IsNullOrWhiteSpace(request.ProjectName) || request.BatchQuantity <= 0 || request.Lines.Count == 0)
        {
            return BomConfirmResult.Failure("BOM 确认参数无效。");
        }
        if (request.Lines.Select(line => line.ComponentId).Distinct(StringComparer.Ordinal).Count() != request.Lines.Count)
        {
            return BomConfirmResult.Failure("BOM 确认行包含重复库存项。");
        }

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();
        if (BomReleaseExists(connection, request.ReleaseId))
        {
            transaction.Rollback();
            return BomConfirmResult.Duplicate("该发行已经扣减，无需重复处理。");
        }
        if (BomBatchExists(connection, request.BatchId))
        {
            transaction.Rollback();
            return BomConfirmResult.Failure("批次 ID 已被其他发行使用。");
        }

        var current = new Dictionary<string, ComponentRecord>(StringComparer.Ordinal);
        foreach (var line in request.Lines)
        {
            var component = GetComponentById(connection, line.ComponentId);
            if (component is null || component.Deleted)
            {
                transaction.Rollback();
                return BomConfirmResult.Failure($"{line.ComponentSku} 已不存在或已删除，请重新预览。");
            }
            if (!TryCompareInstants(component.UpdatedAt, line.ExpectedUpdatedAt, out var comparison) || comparison != 0)
            {
                transaction.Rollback();
                return BomConfirmResult.Failure($"{line.ComponentSku} 在预览后发生变化，请重新预览。");
            }
            if (line.RequiredQuantity <= 0 || component.Quantity < line.RequiredQuantity)
            {
                transaction.Rollback();
                return BomConfirmResult.Failure($"{line.ComponentSku} 库存不足，请重新预览。");
            }
            current[line.ComponentId] = component;
        }

        var happenedAt = UtcNow();
        foreach (var line in request.Lines)
        {
            var movementId = $"mov-{Guid.NewGuid():N}";
            using var update = connection.CreateCommand();
            update.CommandText = "UPDATE components SET quantity = $quantity, updated_at = $updated_at WHERE id = $id";
            update.Parameters.AddWithValue("$quantity", current[line.ComponentId].Quantity - line.RequiredQuantity);
            update.Parameters.AddWithValue("$updated_at", happenedAt);
            update.Parameters.AddWithValue("$id", line.ComponentId);
            update.ExecuteNonQuery();

            using var movement = connection.CreateCommand();
            movement.CommandText =
                """
                INSERT INTO stock_movements (
                    id, component_id, movement_type, quantity, reason, note,
                    happened_at, updated_at, deleted
                ) VALUES (
                    $id, $component_id, 'outbound', $quantity, 'BOM production', $note,
                    $happened_at, $updated_at, 0
                )
                """;
            movement.Parameters.AddWithValue("$id", movementId);
            movement.Parameters.AddWithValue("$component_id", line.ComponentId);
            movement.Parameters.AddWithValue("$quantity", line.RequiredQuantity);
            movement.Parameters.AddWithValue("$note", $"项目：{request.ProjectName}；批次：{request.BatchId}");
            movement.Parameters.AddWithValue("$happened_at", happenedAt);
            movement.Parameters.AddWithValue("$updated_at", happenedAt);
            movement.ExecuteNonQuery();

            EnqueueEntity(connection, "component", line.ComponentId, happenedAt);
            EnqueueEntity(connection, "stock_movement", movementId, happenedAt);
        }

        using (var batch = connection.CreateCommand())
        {
            batch.CommandText =
                """
                INSERT INTO bom_consumption_batches (
                    batch_id, release_id, project_name, batch_quantity, source_fingerprint, created_at
                ) VALUES ($batch_id, $release_id, $project_name, $batch_quantity, $source_fingerprint, $created_at)
                """;
            batch.Parameters.AddWithValue("$batch_id", request.BatchId.Trim());
            batch.Parameters.AddWithValue("$release_id", request.ReleaseId.Trim());
            batch.Parameters.AddWithValue("$project_name", request.ProjectName.Trim());
            batch.Parameters.AddWithValue("$batch_quantity", request.BatchQuantity);
            batch.Parameters.AddWithValue("$source_fingerprint", request.SourceFingerprint.Trim());
            batch.Parameters.AddWithValue("$created_at", happenedAt);
            batch.ExecuteNonQuery();
        }
        transaction.Commit();
        return BomConfirmResult.Success("BOM 批次已原子扣减并写入出库记录。");
    }

    public OperationResult ImportComponentHub(ComponentHubImportRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Fingerprint) || request.Items.Count == 0)
            return OperationResult.Failure("迁移预览为空或缺少文件指纹。");
        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();
        using (var duplicateFile = connection.CreateCommand())
        {
            duplicateFile.CommandText = "SELECT COUNT(*) FROM component_hub_imports WHERE fingerprint = $fingerprint";
            duplicateFile.Parameters.AddWithValue("$fingerprint", request.Fingerprint);
            if (Convert.ToInt32(duplicateFile.ExecuteScalar(), CultureInfo.InvariantCulture) > 0)
            {
                transaction.Rollback();
                return OperationResult.Failure("该 component-hub 文件已导入，未重复写入。");
            }
        }

        var imported = 0;
        foreach (var item in request.Items)
        {
            if (item.Quantity < 0 || item.MinStock < 0 || string.IsNullOrWhiteSpace(item.Sku))
            {
                transaction.Rollback();
                return OperationResult.Failure($"第 {item.Index} 条迁移数据无效。");
            }
            using var exists = connection.CreateCommand();
            exists.CommandText = "SELECT COUNT(*) FROM components WHERE sku = $sku AND deleted = 0";
            exists.Parameters.AddWithValue("$sku", item.Sku);
            if (Convert.ToInt32(exists.ExecuteScalar(), CultureInfo.InvariantCulture) > 0)
            {
                if (request.DuplicatePolicy == DuplicateSkuPolicy.Skip) continue;
                transaction.Rollback();
                return OperationResult.Failure($"SKU {item.Sku} 已存在，迁移已全部回滚。");
            }

            var componentId = $"cmp-{Guid.NewGuid():N}";
            var updatedAt = UtcNow();
            using var component = connection.CreateCommand();
            component.CommandText =
                """
                INSERT INTO components (
                    id, sku, name, category, package_name, location, description,
                    quantity, min_stock, updated_at, deleted
                ) VALUES (
                    $id, $sku, $name, $category, $package_name, $location, $description,
                    $quantity, $min_stock, $updated_at, 0
                )
                """;
            component.Parameters.AddWithValue("$id", componentId);
            component.Parameters.AddWithValue("$sku", item.Sku);
            component.Parameters.AddWithValue("$name", item.Name);
            component.Parameters.AddWithValue("$category", item.Category);
            component.Parameters.AddWithValue("$package_name", item.PackageName);
            component.Parameters.AddWithValue("$location", item.Location);
            component.Parameters.AddWithValue("$description", item.Description);
            component.Parameters.AddWithValue("$quantity", item.Quantity);
            component.Parameters.AddWithValue("$min_stock", item.MinStock);
            component.Parameters.AddWithValue("$updated_at", updatedAt);
            component.ExecuteNonQuery();
            EnqueueEntity(connection, "component", componentId, updatedAt);

            if (item.Quantity > 0)
            {
                var movementId = $"mov-{Guid.NewGuid():N}";
                using var movement = connection.CreateCommand();
                movement.CommandText =
                    """
                    INSERT INTO stock_movements (
                        id, component_id, movement_type, quantity, reason, note,
                        happened_at, updated_at, deleted
                    ) VALUES (
                        $id, $component_id, 'inbound', $quantity, 'component-hub migration',
                        'component-hub JSON 导入初始库存', $updated_at, $updated_at, 0
                    )
                    """;
                movement.Parameters.AddWithValue("$id", movementId);
                movement.Parameters.AddWithValue("$component_id", componentId);
                movement.Parameters.AddWithValue("$quantity", item.Quantity);
                movement.Parameters.AddWithValue("$updated_at", updatedAt);
                movement.ExecuteNonQuery();
                EnqueueEntity(connection, "stock_movement", movementId, updatedAt);
            }
            imported++;
        }

        using var marker = connection.CreateCommand();
        marker.CommandText = "INSERT INTO component_hub_imports (fingerprint, imported_count, created_at) VALUES ($fingerprint, $count, $created_at)";
        marker.Parameters.AddWithValue("$fingerprint", request.Fingerprint);
        marker.Parameters.AddWithValue("$count", imported);
        marker.Parameters.AddWithValue("$created_at", UtcNow());
        marker.ExecuteNonQuery();
        transaction.Commit();
        return OperationResult.Success($"component-hub 迁移完成，新增 {imported} 个元器件。");
    }

    public SyncEnvelope CreateSyncEnvelope()
    {
        using var connection = OpenConnection();
        var settings = GetSyncConfiguration();
        var queuedEntities = GetQueuedEntities(connection);

        var components = new List<SyncComponentDto>();
        var stockMovements = new List<SyncStockMovementDto>();

        foreach (var entity in queuedEntities)
        {
            if (entity.EntityType == "component")
            {
                var component = GetComponentDtoById(connection, entity.EntityId);
                if (component is not null)
                {
                    components.Add(component);
                }
            }
            else if (entity.EntityType == "stock_movement")
            {
                var movement = GetMovementDtoById(connection, entity.EntityId);
                if (movement is not null)
                {
                    stockMovements.Add(movement);
                }
            }
        }

        return new SyncEnvelope
        {
            Settings = settings,
            Cursor = GetStoredSyncCursor(connection),
            QueuedEntities = queuedEntities,
            PushRequest = new SyncPushRequest
            {
                DeviceId = settings.DeviceId,
                Components = components,
                StockMovements = stockMovements,
            },
        };
    }

    public bool ApplySyncResult(
        SyncRunResult result,
        IReadOnlyList<SyncEntityReference> pushedEntities,
        string expectedServerBaseUrl
    )
    {
        if (result.PullResponse is null)
        {
            throw new InvalidOperationException("同步成功结果必须包含拉取数据。");
        }

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

        if (!StoredServerMatches(connection, expectedServerBaseUrl))
        {
            transaction.Rollback();
            return false;
        }

        foreach (var component in result.PullResponse.Components)
        {
            UpsertRemoteComponent(connection, component);
            RemoveQueuedIfSuperseded(connection, "component", component.Id, component.UpdatedAt);
        }

        foreach (var movement in result.PullResponse.StockMovements)
        {
            UpsertRemoteMovement(connection, movement);
            RemoveQueuedIfSuperseded(
                connection,
                "stock_movement",
                movement.Id,
                movement.UpdatedAt
            );
        }

        foreach (var pushedEntity in pushedEntities)
        {
            RemoveQueuedEntityIfSnapshotMatches(connection, pushedEntity);
        }

        UpdateStoredSyncCursor(connection, result.PullResponse.SyncCursor);

        UpdateStoredSyncStatus(
            connection,
            result.PullResponse.ServerTime,
            $"同步完成。上传 元器件:{result.AcceptedComponents} 变动:{result.AcceptedStockMovements}；下载 元器件:{result.PullResponse.Components.Count} 变动:{result.PullResponse.StockMovements.Count}。",
            preserveTimestamp: false
        );

        transaction.Commit();
        return true;
    }

    public void UpdateSyncStatus(string message)
    {
        using var connection = OpenConnection();
        UpdateStoredSyncStatus(connection, null, message, preserveTimestamp: true);
    }

    private SqliteConnection OpenConnection()
    {
        var connection = new SqliteConnection($"Data Source={_databasePath}");
        connection.Open();

        using var pragma = connection.CreateCommand();
        pragma.CommandText = "PRAGMA foreign_keys = ON";
        pragma.ExecuteNonQuery();

        return connection;
    }

    private static void ExecuteSchema(SqliteConnection connection)
    {
        var statements = new[]
        {
            """
            CREATE TABLE IF NOT EXISTS components (
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
            """,
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_components_sku_active
            ON components(sku)
            WHERE deleted = 0
            """,
            """
            CREATE TABLE IF NOT EXISTS stock_movements (
                id TEXT PRIMARY KEY,
                component_id TEXT NOT NULL,
                movement_type TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                reason TEXT NOT NULL,
                note TEXT,
                happened_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (component_id) REFERENCES components(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sync_queue (
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                entity_updated_at TEXT NOT NULL,
                created_at TEXT NOT NULL,
                PRIMARY KEY (entity_type, entity_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sync_settings (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                device_id TEXT NOT NULL,
                server_base_url TEXT NOT NULL DEFAULT '',
                api_token TEXT NOT NULL DEFAULT '',
                auto_sync_enabled INTEGER NOT NULL DEFAULT 0,
                last_sync_cursor INTEGER,
                last_synced_at TEXT,
                last_sync_message TEXT NOT NULL DEFAULT '尚未同步。'
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bom_consumption_batches (
                batch_id TEXT PRIMARY KEY,
                release_id TEXT NOT NULL UNIQUE,
                project_name TEXT NOT NULL,
                batch_quantity INTEGER NOT NULL CHECK (batch_quantity > 0),
                source_fingerprint TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS component_hub_imports (
                fingerprint TEXT PRIMARY KEY,
                imported_count INTEGER NOT NULL CHECK (imported_count >= 0),
                created_at TEXT NOT NULL
            )
            """,
        };

        foreach (var statement in statements)
        {
            using var command = connection.CreateCommand();
            command.CommandText = statement;
            command.ExecuteNonQuery();
        }

        EnsureColumnExists(connection, "sync_settings", "last_sync_cursor", "INTEGER");
    }

    private static void EnsureColumnExists(
        SqliteConnection connection,
        string tableName,
        string columnName,
        string columnType
    )
    {
        using var inspect = connection.CreateCommand();
        inspect.CommandText = $"PRAGMA table_info({tableName})";
        using var reader = inspect.ExecuteReader();
        while (reader.Read())
        {
            if (string.Equals(reader.GetString(1), columnName, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }

        reader.Close();
        using var alter = connection.CreateCommand();
        alter.CommandText = $"ALTER TABLE {tableName} ADD COLUMN {columnName} {columnType}";
        alter.ExecuteNonQuery();
    }

    private static void EnsureDefaultSettings(SqliteConnection connection)
    {
        using var insert = connection.CreateCommand();
        insert.CommandText =
            """
            INSERT INTO sync_settings (
                id,
                device_id,
                server_base_url,
                api_token,
                auto_sync_enabled,
                last_synced_at,
                last_sync_message
            )
            VALUES (
                1,
                $device_id,
                '',
                '',
                0,
                NULL,
                '尚未同步。'
            )
            ON CONFLICT(id) DO NOTHING
            """;
        insert.Parameters.AddWithValue(
            "$device_id",
            $"windows-{Environment.MachineName.ToLowerInvariant()}"
        );
        insert.ExecuteNonQuery();
    }

    private static int GetMovementCount(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            "SELECT COUNT(*) FROM stock_movements WHERE deleted = 0";
        return Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture);
    }

    private static bool BomReleaseExists(SqliteConnection connection, string releaseId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM bom_consumption_batches WHERE release_id = $release_id";
        command.Parameters.AddWithValue("$release_id", releaseId.Trim());
        return Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture) > 0;
    }

    private static bool BomBatchExists(SqliteConnection connection, string batchId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM bom_consumption_batches WHERE batch_id = $batch_id";
        command.Parameters.AddWithValue("$batch_id", batchId.Trim());
        return Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture) > 0;
    }

    private static IReadOnlyList<ComponentRecord> ReadComponents(SqliteDataReader reader)
    {
        var components = new List<ComponentRecord>();
        while (reader.Read())
        {
            components.Add(
                new ComponentRecord
                {
                    Id = reader.GetString(0),
                    Sku = reader.GetString(1),
                    Name = reader.GetString(2),
                    Category = reader.GetString(3),
                    PackageName = reader.GetString(4),
                    Location = reader.GetString(5),
                    Description = reader.GetString(6),
                    Quantity = reader.GetInt32(7),
                    MinStock = reader.GetInt32(8),
                    UpdatedAt = reader.GetString(9),
                    Deleted = reader.GetInt32(10) == 1,
                }
            );
        }

        return components;
    }

    private static IReadOnlyList<StockMovementRecord> ReadMovements(SqliteDataReader reader)
    {
        var movements = new List<StockMovementRecord>();
        while (reader.Read())
        {
            movements.Add(
                new StockMovementRecord
                {
                    Id = reader.GetString(0),
                    ComponentId = reader.GetString(1),
                    ComponentSku = reader.GetString(2),
                    ComponentName = reader.GetString(3),
                    MovementType = reader.GetString(4),
                    Quantity = reader.GetInt32(5),
                    Reason = reader.GetString(6),
                    Note = reader.GetString(7),
                    HappenedAt = reader.GetString(8),
                    UpdatedAt = reader.GetString(9),
                    Deleted = reader.GetInt32(10) == 1,
                }
            );
        }

        return movements;
    }

    private ComponentRecord GetComponentById(string componentId)
    {
        using var connection = OpenConnection();
        var component = GetComponentById(connection, componentId);
        return component ?? throw new InvalidOperationException("保存后未找到对应元器件。");
    }

    private static ComponentRecord? GetComponentById(SqliteConnection connection, string componentId)
    {
        using var command = connection.CreateCommand();
        command.CommandText =
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
            WHERE id = $id
            """;
        command.Parameters.AddWithValue("$id", componentId);

        using var reader = command.ExecuteReader();
        return reader.Read()
            ? new ComponentRecord
            {
                Id = reader.GetString(0),
                Sku = reader.GetString(1),
                Name = reader.GetString(2),
                Category = reader.GetString(3),
                PackageName = reader.GetString(4),
                Location = reader.GetString(5),
                Description = reader.GetString(6),
                Quantity = reader.GetInt32(7),
                MinStock = reader.GetInt32(8),
                UpdatedAt = reader.GetString(9),
                Deleted = reader.GetInt32(10) == 1,
            }
            : null;
    }

    private static bool ComponentExists(SqliteConnection connection, string componentId)
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT COUNT(*) FROM components WHERE id = $id";
        command.Parameters.AddWithValue("$id", componentId);
        return Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture) > 0;
    }

    private static void EnsureUniqueActiveSku(
        SqliteConnection connection,
        string sku,
        string? componentId
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            SELECT COUNT(*)
            FROM components
            WHERE deleted = 0 AND sku = $sku AND id != COALESCE($id, '')
            """;
        command.Parameters.AddWithValue("$sku", sku);
        command.Parameters.AddWithValue("$id", (object?)componentId ?? DBNull.Value);
        var duplicates = Convert.ToInt32(command.ExecuteScalar(), CultureInfo.InvariantCulture);
        if (duplicates > 0)
        {
            throw new InvalidOperationException("已存在相同 SKU 的有效元器件。");
        }
    }

    private static void ValidateComponentDraft(ComponentDraft draft)
    {
        if (string.IsNullOrWhiteSpace(draft.Sku)
            || string.IsNullOrWhiteSpace(draft.Name)
            || string.IsNullOrWhiteSpace(draft.Category)
            || string.IsNullOrWhiteSpace(draft.PackageName)
            || string.IsNullOrWhiteSpace(draft.Location))
        {
            throw new InvalidOperationException("请填写 SKU、名称、分类、封装和库位。");
        }

        if (draft.Quantity < 0 || draft.MinStock < 0)
        {
            throw new InvalidOperationException("数量和最低库存不能为负数。");
        }
    }

    private static void ValidateMovementDraft(MovementEntryDraft draft)
    {
        if (string.IsNullOrWhiteSpace(draft.ComponentId)
            || string.IsNullOrWhiteSpace(draft.MovementType)
            || string.IsNullOrWhiteSpace(draft.Reason))
        {
            throw new InvalidOperationException("请选择元器件、变动类型并填写原因。");
        }

        if (draft.MovementType.Equals("adjustment", StringComparison.OrdinalIgnoreCase))
        {
            if (draft.Quantity == 0)
            {
                throw new InvalidOperationException("调整数量不能为 0。");
            }
        }
        else if (draft.Quantity <= 0)
        {
            throw new InvalidOperationException("变动数量必须大于 0。");
        }
    }

    private static int CalculateQuantityDelta(string movementType, int quantity) =>
        movementType.Trim().ToLowerInvariant() switch
        {
            "inbound" => quantity,
            "outbound" => -quantity,
            "adjustment" => quantity,
            _ => throw new InvalidOperationException("不支持的变动类型。"),
        };

    private static int NormalizeMovementQuantity(string movementType, int quantity) =>
        movementType.Trim().ToLowerInvariant() switch
        {
            "adjustment" => quantity,
            _ => Math.Abs(quantity),
        };

    private static string UtcNow() =>
        DateTimeOffset.UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'", CultureInfo.InvariantCulture);

    private static string NormalizeServerBaseUrl(string serverBaseUrl) =>
        serverBaseUrl.Trim().TrimEnd('/');

    private static void EnqueueEntity(
        SqliteConnection connection,
        string entityType,
        string entityId,
        string updatedAt
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            INSERT INTO sync_queue (
                entity_type,
                entity_id,
                entity_updated_at,
                created_at
            ) VALUES (
                $entity_type,
                $entity_id,
                $entity_updated_at,
                $created_at
            )
            ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                entity_updated_at = excluded.entity_updated_at,
                created_at = excluded.created_at
            """;
        command.Parameters.AddWithValue("$entity_type", entityType);
        command.Parameters.AddWithValue("$entity_id", entityId);
        command.Parameters.AddWithValue("$entity_updated_at", updatedAt);
        command.Parameters.AddWithValue("$created_at", UtcNow());
        command.ExecuteNonQuery();
    }

    private static IReadOnlyList<SyncEntityReference> GetQueuedEntities(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            SELECT entity_type, entity_id, entity_updated_at
            FROM sync_queue
            ORDER BY created_at ASC
            """;

        using var reader = command.ExecuteReader();
        var entities = new List<SyncEntityReference>();
        while (reader.Read())
        {
            entities.Add(
                new SyncEntityReference
                {
                    EntityType = reader.GetString(0),
                    EntityId = reader.GetString(1),
                    EntityUpdatedAt = reader.GetString(2),
                }
            );
        }

        return entities;
    }

    private static long? GetStoredSyncCursor(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            "SELECT last_sync_cursor FROM sync_settings WHERE id = 1";
        var value = command.ExecuteScalar();
        return value is null or DBNull ? null : Convert.ToInt64(value, CultureInfo.InvariantCulture);
    }

    private static bool StoredServerMatches(
        SqliteConnection connection,
        string expectedServerBaseUrl
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT server_base_url FROM sync_settings WHERE id = 1";
        var currentServerBaseUrl = command.ExecuteScalar() as string ?? string.Empty;
        return string.Equals(
            NormalizeServerBaseUrl(currentServerBaseUrl),
            NormalizeServerBaseUrl(expectedServerBaseUrl),
            StringComparison.Ordinal
        );
    }

    private static SyncComponentDto? GetComponentDtoById(
        SqliteConnection connection,
        string componentId
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
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
            WHERE id = $id
            """;
        command.Parameters.AddWithValue("$id", componentId);

        using var reader = command.ExecuteReader();
        if (!reader.Read())
        {
            return null;
        }

        return new SyncComponentDto
        {
            Id = reader.GetString(0),
            Sku = reader.GetString(1),
            Name = reader.GetString(2),
            Category = reader.GetString(3),
            PackageName = reader.GetString(4),
            Location = reader.GetString(5),
            Description = reader.IsDBNull(6) ? null : reader.GetString(6),
            Quantity = reader.GetInt32(7),
            MinStock = reader.GetInt32(8),
            UpdatedAt = reader.GetString(9),
            Deleted = reader.GetInt32(10) == 1,
        };
    }

    private static SyncStockMovementDto? GetMovementDtoById(
        SqliteConnection connection,
        string movementId
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
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
            WHERE id = $id
            """;
        command.Parameters.AddWithValue("$id", movementId);

        using var reader = command.ExecuteReader();
        if (!reader.Read())
        {
            return null;
        }

        return new SyncStockMovementDto
        {
            Id = reader.GetString(0),
            ComponentId = reader.GetString(1),
            MovementType = reader.GetString(2),
            Quantity = reader.GetInt32(3),
            Reason = reader.GetString(4),
            Note = reader.IsDBNull(5) ? null : reader.GetString(5),
            HappenedAt = reader.GetString(6),
            UpdatedAt = reader.GetString(7),
            Deleted = reader.GetInt32(8) == 1,
        };
    }

    private static void RemoveQueuedIfSuperseded(
        SqliteConnection connection,
        string entityType,
        string entityId,
        string remoteUpdatedAt
    )
    {
        var queuedUpdatedAt = GetQueuedEntityUpdatedAt(connection, entityType, entityId);
        if (
            queuedUpdatedAt is not null
            && TryCompareInstants(queuedUpdatedAt, remoteUpdatedAt, out var comparison)
            && comparison <= 0
        )
        {
            RemoveQueuedEntity(connection, entityType, entityId, queuedUpdatedAt);
        }
    }

    private static void RemoveQueuedEntityIfSnapshotMatches(
        SqliteConnection connection,
        SyncEntityReference snapshot
    )
    {
        var queuedUpdatedAt = GetQueuedEntityUpdatedAt(
            connection,
            snapshot.EntityType,
            snapshot.EntityId
        );
        if (
            queuedUpdatedAt is not null
            && TryCompareInstants(queuedUpdatedAt, snapshot.EntityUpdatedAt, out var comparison)
            && comparison == 0
        )
        {
            RemoveQueuedEntity(
                connection,
                snapshot.EntityType,
                snapshot.EntityId,
                queuedUpdatedAt
            );
        }
    }

    private static string? GetQueuedEntityUpdatedAt(
        SqliteConnection connection,
        string entityType,
        string entityId
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            "SELECT entity_updated_at FROM sync_queue WHERE entity_type = $entity_type AND entity_id = $entity_id";
        command.Parameters.AddWithValue("$entity_type", entityType);
        command.Parameters.AddWithValue("$entity_id", entityId);
        return command.ExecuteScalar() as string;
    }

    private static void RemoveQueuedEntity(
        SqliteConnection connection,
        string entityType,
        string entityId,
        string expectedUpdatedAt
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            DELETE FROM sync_queue
            WHERE entity_type = $entity_type
              AND entity_id = $entity_id
              AND entity_updated_at = $entity_updated_at
            """;
        command.Parameters.AddWithValue("$entity_type", entityType);
        command.Parameters.AddWithValue("$entity_id", entityId);
        command.Parameters.AddWithValue("$entity_updated_at", expectedUpdatedAt);
        command.ExecuteNonQuery();
    }

    private static bool TryCompareInstants(string left, string right, out int comparison)
    {
        if (
            DateTimeOffset.TryParse(
                left,
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var leftInstant
            )
            && DateTimeOffset.TryParse(
                right,
                CultureInfo.InvariantCulture,
                DateTimeStyles.AssumeUniversal | DateTimeStyles.AdjustToUniversal,
                out var rightInstant
            )
        )
        {
            comparison = leftInstant.UtcTicks.CompareTo(rightInstant.UtcTicks);
            return true;
        }

        comparison = 0;
        return false;
    }

    private static void UpdateStoredSyncCursor(SqliteConnection connection, long? syncCursor)
    {
        if (syncCursor is < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(syncCursor));
        }

        using var command = connection.CreateCommand();
        command.CommandText = "UPDATE sync_settings SET last_sync_cursor = $cursor WHERE id = 1";
        command.Parameters.AddWithValue("$cursor", syncCursor is null ? DBNull.Value : syncCursor.Value);
        command.ExecuteNonQuery();
    }

    private static void UpdateStoredSyncStatus(
        SqliteConnection connection,
        string? lastSyncedAt,
        string message,
        bool preserveTimestamp
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText = preserveTimestamp
            ? """
              UPDATE sync_settings
              SET last_sync_message = $message
              WHERE id = 1
              """
            : """
              UPDATE sync_settings
              SET
                  last_synced_at = $last_synced_at,
                  last_sync_message = $message
              WHERE id = 1
              """;
        command.Parameters.AddWithValue("$message", message);
        if (!preserveTimestamp)
        {
            command.Parameters.AddWithValue(
                "$last_synced_at",
                lastSyncedAt is null ? DBNull.Value : lastSyncedAt
            );
        }
        command.ExecuteNonQuery();
    }

    private static void UpsertRemoteComponent(
        SqliteConnection connection,
        SyncComponentDto component
    )
    {
        var existing = GetComponentById(connection, component.Id);
        if (
            existing is not null
            && string.CompareOrdinal(existing.UpdatedAt, component.UpdatedAt) > 0
        )
        {
            return;
        }

        using var command = connection.CreateCommand();
        command.CommandText =
            """
            INSERT INTO components (
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
            ) VALUES (
                $id,
                $sku,
                $name,
                $category,
                $package_name,
                $location,
                $description,
                $quantity,
                $min_stock,
                $updated_at,
                $deleted
            )
            ON CONFLICT(id) DO UPDATE SET
                sku = excluded.sku,
                name = excluded.name,
                category = excluded.category,
                package_name = excluded.package_name,
                location = excluded.location,
                description = excluded.description,
                quantity = excluded.quantity,
                min_stock = excluded.min_stock,
                updated_at = excluded.updated_at,
                deleted = excluded.deleted
            """;
        command.Parameters.AddWithValue("$id", component.Id);
        command.Parameters.AddWithValue("$sku", component.Sku);
        command.Parameters.AddWithValue("$name", component.Name);
        command.Parameters.AddWithValue("$category", component.Category);
        command.Parameters.AddWithValue("$package_name", component.PackageName);
        command.Parameters.AddWithValue("$location", component.Location);
        command.Parameters.AddWithValue("$description", (object?)component.Description ?? DBNull.Value);
        command.Parameters.AddWithValue("$quantity", component.Quantity);
        command.Parameters.AddWithValue("$min_stock", component.MinStock);
        command.Parameters.AddWithValue("$updated_at", component.UpdatedAt);
        command.Parameters.AddWithValue("$deleted", component.Deleted ? 1 : 0);
        command.ExecuteNonQuery();
    }

    private static void UpsertRemoteMovement(
        SqliteConnection connection,
        SyncStockMovementDto movement
    )
    {
        using var check = connection.CreateCommand();
        check.CommandText =
            "SELECT updated_at FROM stock_movements WHERE id = $id";
        check.Parameters.AddWithValue("$id", movement.Id);
        var existingUpdatedAt = check.ExecuteScalar() as string;
        if (
            existingUpdatedAt is not null
            && string.CompareOrdinal(existingUpdatedAt, movement.UpdatedAt) > 0
        )
        {
            return;
        }

        using var command = connection.CreateCommand();
        command.CommandText =
            """
            INSERT INTO stock_movements (
                id,
                component_id,
                movement_type,
                quantity,
                reason,
                note,
                happened_at,
                updated_at,
                deleted
            ) VALUES (
                $id,
                $component_id,
                $movement_type,
                $quantity,
                $reason,
                $note,
                $happened_at,
                $updated_at,
                $deleted
            )
            ON CONFLICT(id) DO UPDATE SET
                component_id = excluded.component_id,
                movement_type = excluded.movement_type,
                quantity = excluded.quantity,
                reason = excluded.reason,
                note = excluded.note,
                happened_at = excluded.happened_at,
                updated_at = excluded.updated_at,
                deleted = excluded.deleted
            """;
        command.Parameters.AddWithValue("$id", movement.Id);
        command.Parameters.AddWithValue("$component_id", movement.ComponentId);
        command.Parameters.AddWithValue("$movement_type", movement.MovementType);
        command.Parameters.AddWithValue("$quantity", movement.Quantity);
        command.Parameters.AddWithValue("$reason", movement.Reason);
        command.Parameters.AddWithValue(
            "$note",
            movement.Note is null ? DBNull.Value : movement.Note
        );
        command.Parameters.AddWithValue("$happened_at", movement.HappenedAt);
        command.Parameters.AddWithValue("$updated_at", movement.UpdatedAt);
        command.Parameters.AddWithValue("$deleted", movement.Deleted ? 1 : 0);
        command.ExecuteNonQuery();
    }
}

public sealed class SyncEnvelope
{
    public required SyncConfiguration Settings { get; init; }
    public required SyncPushRequest PushRequest { get; init; }
    public long? Cursor { get; init; }
    public required IReadOnlyList<SyncEntityReference> QueuedEntities { get; init; }
}

public sealed class SyncEntityReference
{
    public required string EntityType { get; init; }
    public required string EntityId { get; init; }
    public required string EntityUpdatedAt { get; init; }
}
