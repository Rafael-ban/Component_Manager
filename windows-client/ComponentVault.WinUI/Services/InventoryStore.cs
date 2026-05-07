using System.Globalization;
using ComponentVault.WinUI.Models;
using Microsoft.Data.Sqlite;

namespace ComponentVault.WinUI.Services;

public sealed class InventoryStore
{
    private readonly string _databasePath;

    public InventoryStore()
    {
        var root = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "ComponentVault"
        );
        Directory.CreateDirectory(root);
        _databasePath = Path.Combine(root, "component-vault.db");
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
                auto_sync_enabled = $auto_sync_enabled
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
            Since = GetStoredLastSyncedAt(connection),
            QueuedEntities = queuedEntities,
            PushRequest = new SyncPushRequest
            {
                DeviceId = settings.DeviceId,
                Components = components,
                StockMovements = stockMovements,
            },
        };
    }

    public void ApplySyncResult(SyncRunResult result, IReadOnlyList<SyncEntityReference> pushedEntities)
    {
        if (result.PullResponse is null)
        {
            throw new InvalidOperationException("同步成功结果必须包含拉取数据。");
        }

        using var connection = OpenConnection();
        using var transaction = connection.BeginTransaction();

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
            RemoveQueuedEntity(connection, pushedEntity.EntityType, pushedEntity.EntityId);
        }

        UpdateStoredSyncStatus(
            connection,
            result.PullResponse.ServerTime,
            $"同步完成。上传 元器件:{result.AcceptedComponents} 变动:{result.AcceptedStockMovements}；下载 元器件:{result.PullResponse.Components.Count} 变动:{result.PullResponse.StockMovements.Count}。",
            preserveTimestamp: false
        );

        transaction.Commit();
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
                last_synced_at TEXT,
                last_sync_message TEXT NOT NULL DEFAULT '尚未同步。'
            )
            """,
        };

        foreach (var statement in statements)
        {
            using var command = connection.CreateCommand();
            command.CommandText = statement;
            command.ExecuteNonQuery();
        }
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

    private static string? GetStoredLastSyncedAt(SqliteConnection connection)
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            "SELECT last_synced_at FROM sync_settings WHERE id = 1";
        var value = command.ExecuteScalar() as string;
        return string.IsNullOrWhiteSpace(value) ? null : value;
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
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            DELETE FROM sync_queue
            WHERE
                entity_type = $entity_type
                AND entity_id = $entity_id
                AND entity_updated_at <= $entity_updated_at
            """;
        command.Parameters.AddWithValue("$entity_type", entityType);
        command.Parameters.AddWithValue("$entity_id", entityId);
        command.Parameters.AddWithValue("$entity_updated_at", remoteUpdatedAt);
        command.ExecuteNonQuery();
    }

    private static void RemoveQueuedEntity(
        SqliteConnection connection,
        string entityType,
        string entityId
    )
    {
        using var command = connection.CreateCommand();
        command.CommandText =
            """
            DELETE FROM sync_queue
            WHERE entity_type = $entity_type AND entity_id = $entity_id
            """;
        command.Parameters.AddWithValue("$entity_type", entityType);
        command.Parameters.AddWithValue("$entity_id", entityId);
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
    public string? Since { get; init; }
    public required IReadOnlyList<SyncEntityReference> QueuedEntities { get; init; }
}

public sealed class SyncEntityReference
{
    public required string EntityType { get; init; }
    public required string EntityId { get; init; }
    public required string EntityUpdatedAt { get; init; }
}
