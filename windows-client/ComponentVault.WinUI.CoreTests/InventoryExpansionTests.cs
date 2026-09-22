using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using Xunit;
using Microsoft.Data.Sqlite;
using System.IO.Compression;
using System.Xml.Linq;

namespace ComponentVault.WinUI.CoreTests;

public sealed class InventoryExpansionTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), $"component-vault-expansion-{Guid.NewGuid():N}");

    [Fact]
    public void Transfer_MovesAllocationWithoutChangingTotalOrUsage()
    {
        var store = Store();
        var component = store.SaveComponent(Draft("C1", "A", 10));
        Assert.True(store.SaveStorageLocation("B", "Bin B").IsSuccess);

        var result = store.RecordMovement(new MovementEntryDraft
        {
            ComponentId = component.Id, MovementType = "transfer", Quantity = 4,
            LocationId = "A", DestinationLocationId = "B", Reason = "rebalance",
        });

        Assert.True(result.IsSuccess, result.Message);
        var current = Assert.Single(store.GetComponents());
        Assert.Equal(10, current.Quantity);
        Assert.Equal(6, current.Allocations.Single(item => item.LocationId == "A").Quantity);
        Assert.Equal(4, current.Allocations.Single(item => item.LocationId == "B").Quantity);
        Assert.Equal(0, store.GetCumulativeOutboundQuantities().GetValueOrDefault(component.Id));
        Assert.Equal("transfer", Assert.Single(store.GetMovements()).MovementType);
        store.SaveComponent(new ComponentDraft
        {
            Id = component.Id, Sku = "C1", Name = "renamed", Category = "test", PackageName = "0603",
            Location = "B", Description = string.Empty, Quantity = 10, MinStock = 0,
        });
        Assert.Equal(2, Assert.Single(store.GetComponents()).Allocations.Count);
        var envelope = store.CreateSyncEnvelope();
        var pushed = Assert.Single(envelope.PushRequest.Components);
        Assert.Equal(10, pushed.Allocations!.Sum(item => item.Quantity));
        var transfer = Assert.Single(envelope.PushRequest.StockMovements);
        Assert.Equal("A", transfer.LocationId);
        Assert.Equal("B", transfer.DestinationLocationId);
        Assert.Equal(1, envelope.PushRequest.InventoryProtocol);
    }

    [Fact]
    public void Movement_RejectsQuantityUnavailableAtSelectedLocation()
    {
        var store = Store();
        var component = store.SaveComponent(Draft("C2", "A", 3));
        store.SaveStorageLocation("B", "Bin B");
        var result = store.RecordMovement(new MovementEntryDraft
        {
            ComponentId = component.Id, MovementType = "outbound", Quantity = 1,
            LocationId = "B", Reason = "build",
        });
        Assert.False(result.IsSuccess);
        Assert.Equal(3, Assert.Single(store.GetComponents()).Quantity);
    }

    [Fact]
    public void LocationDeletion_RejectsPositiveStockAndKeepsZeroAllocationReference()
    {
        var store = Store(); var component = store.SaveComponent(Draft("C-DEL", "A", 2));
        Assert.False(store.DeleteStorageLocation("A").IsSuccess);
        Assert.True(store.SaveStorageLocation("B", "Bin B").IsSuccess);
        Assert.True(store.RecordMovement(new MovementEntryDraft { ComponentId=component.Id, MovementType="transfer", Quantity=2, LocationId="A", DestinationLocationId="B", Reason="move" }).IsSuccess);
        Assert.True(store.DeleteStorageLocation("A").IsSuccess);
        Assert.Contains(Assert.Single(store.GetComponents()).Allocations, allocation => allocation.LocationId=="A" && allocation.Quantity==0);
        Assert.DoesNotContain(store.GetStorageLocations(), location => location.Id=="A");
    }

    [Fact]
    public void LocationCreate_RejectsTrimmedDuplicateWithoutChangingInventoryOrRevivingTombstone()
    {
        var store = Store("location-duplicate.db");
        var component = store.SaveComponent(Draft("C-LOCATION", "A", 2));
        Assert.True(store.UpdateStorageLocation("A", "Original A").IsSuccess);
        var originalLocation = Assert.Single(store.GetStorageLocations());
        var originalComponent = Assert.Single(store.GetComponents());

        var duplicate = store.CreateStorageLocation("  A  ", "Replacement A");

        Assert.False(duplicate.IsSuccess);
        var activeLocation = Assert.Single(store.GetStorageLocations());
        Assert.Equal("Original A", activeLocation.Name);
        Assert.Equal(originalLocation.UpdatedAt, activeLocation.UpdatedAt);
        var unchangedComponent = Assert.Single(store.GetComponents());
        Assert.Equal(originalComponent.UpdatedAt, unchangedComponent.UpdatedAt);
        Assert.Equal(2, Assert.Single(unchangedComponent.Allocations).Quantity);

        Assert.True(store.CreateStorageLocation("B", "Original A").IsSuccess);
        Assert.True(store.RecordMovement(new MovementEntryDraft
        {
            ComponentId = component.Id, MovementType = "transfer", Quantity = 2,
            LocationId = "A", DestinationLocationId = "B", Reason = "move",
        }).IsSuccess);
        Assert.True(store.DeleteStorageLocation("A").IsSuccess);
        var tombstone = store.GetStorageLocations(includeDeleted: true).Single(location => location.Id == "A");

        var missingEdit = store.UpdateStorageLocation("MISSING", "Missing");
        var tombstoneEdit = store.UpdateStorageLocation(" A ", "Edited A");
        var tombstoneDuplicate = store.CreateStorageLocation(" A ", "Revived A");

        Assert.False(missingEdit.IsSuccess);
        Assert.False(tombstoneEdit.IsSuccess);
        Assert.False(tombstoneDuplicate.IsSuccess);
        var unchangedTombstone = store.GetStorageLocations(includeDeleted: true).Single(location => location.Id == "A");
        Assert.True(unchangedTombstone.Deleted);
        Assert.Equal("Original A", unchangedTombstone.Name);
        Assert.Equal(tombstone.UpdatedAt, unchangedTombstone.UpdatedAt);
        var finalComponent = Assert.Single(store.GetComponents());
        Assert.Equal(2, finalComponent.Allocations.Single(allocation => allocation.LocationId == "B").Quantity);
        Assert.Equal(0, finalComponent.Allocations.Single(allocation => allocation.LocationId == "A").Quantity);
    }

    [Fact]
    public void OwnWorkbook_RoundTripsExactContractAndImportsNewOnly()
    {
        var source = Store("source.db");
        source.SaveComponent(Draft("C3", "A", 7));
        var path = Path.Combine(_root, "backup.xlsx");
        new InventoryWorkbookBackup(source).Export(path);
        var wire = SimpleXlsx.Read(path);
        Assert.Equal(new[] { "meta", "components", "storage_locations", "allocations", "stock_movements" }, wire.Keys);
        Assert.IsType<bool>(wire["components"][1][10]);
        Assert.Equal("image_preview", wire["components"][0][12]);

        var target = Store("target.db");
        var backup = new InventoryWorkbookBackup(target);
        var preview = backup.Preview(path);
        Assert.True(preview.CanImport, string.Join(";", preview.Issues));
        Assert.Equal("component-vault", preview.SourceFormat);
        Assert.Equal(1, preview.NewComponents); Assert.Equal(0,preview.ConflictingComponents);
        Assert.True(target.SaveStorageLocation("CHANGED","Changed after preview").IsSuccess);
        Assert.False(backup.ImportNewOnly(path,preview).IsSuccess);
        preview=backup.Preview(path);
        Assert.True(backup.ImportNewOnly(path,preview).IsSuccess);
        Assert.True(backup.ImportNewOnly(path).IsSuccess);
        Assert.Equal(7, Assert.Single(target.GetComponents()).Allocations.Sum(item => item.Quantity));
    }

    [Fact]
    public void OwnWorkbook_RoundTripsEmbeddedPrivateImage()
    {
        var source=Store("image-source.db");
        var bytes=Convert.FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        var sourceImages=Path.Combine(_root,"images"); var key=PrivateProductImageStore.Persist(sourceImages,bytes);
        source.SaveComponent(new ComponentDraft { Sku="C-IMG", Name="C-IMG", Category="test", PackageName="0603", Location="A", Quantity=0, MinStock=0, Description=PrivateProductImageStore.AppendMarker("photo",key) });
        var path=Path.Combine(_root,"images.xlsx"); new InventoryWorkbookBackup(source).Export(path);
        Assert.Single(SimpleXlsx.ReadImages(path,"components",new HashSet<int>{12}));
        var target=Store(Path.Combine("image-target","inventory.db")); var backup=new InventoryWorkbookBackup(target); var preview=backup.Preview(path);
        Assert.True(preview.CanImport,string.Join(";",preview.Issues)); Assert.True(backup.ImportNewOnly(path,preview).IsSuccess);
        var imported=Assert.Single(target.GetComponents());
        Assert.NotNull(PrivateProductImageStore.Resolve(Path.Combine(_root,"image-target","images"),imported.Description));
    }

    [Fact]
    public void LegacyLcscSchema1_PreviewsAndCreatesExplicitInitialHistory()
    {
        Directory.CreateDirectory(_root);
        var path = Path.Combine(_root, "legacy.xlsx");
        SimpleXlsx.Write(path, new Dictionary<string, IReadOnlyList<object?[]>>
        {
            ["meta"] = [["schemaVersion", "1"]],
            ["storage_locations"] = [["id","code","displayName","colorHex","sortMode","remark","createdAt"], [1,"A1","Drawer A",null,"NONE",null,1_700_000_000_000]],
            ["components"] = [["id","partNumber","name","brand","packageName","category","specJson","description","sourceUrl","updatedAt","imagePreview"], [2,"C99","Part","Brand","0603","电阻","{}","note","https://item.szlcsc.com/9.html",1_700_000_001_000,null]],
            ["inventory_items"] = [["id","componentId","locationId","quantity","lastInboundAt","updatedAt"], [3,2,1,5,1_700_000_001_000,1_700_000_001_000]],
        });
        var store = Store("legacy-target.db");
        var backup = new InventoryWorkbookBackup(store);
        var preview = backup.Preview(path);
        Assert.True(preview.CanImport, string.Join(";", preview.Issues));
        Assert.Equal("lcsc-android-erp", preview.SourceFormat);
        Assert.True(backup.ImportNewOnly(path).IsSuccess);
        Assert.Equal(5, Assert.Single(store.GetComponents()).Quantity);
        Assert.Equal("LCSC schema1 initial inventory", Assert.Single(store.GetMovements()).Reason);
        Assert.Equal("A1",Assert.Single(store.GetComponents()).Location);
    }

    [Fact]
    public void SimpleXlsx_ReadsPoiNumericFormsExactlyAndAllowsMoreThanFiveThousandRows()
    {
        Directory.CreateDirectory(_root);var path=Path.Combine(_root,"numeric.xlsx");
        var rows=new List<object?[]>{new object?[]{"id","quantity","epoch"},new object?[]{2,5.0m,1.7E12m}};
        rows.AddRange(Enumerable.Range(0,5001).Select(i=>new object?[]{"row-"+i,i,1_700_000_000_000m}));
        SimpleXlsx.Write(path,new Dictionary<string,IReadOnlyList<object?[]>>{{"data",rows}});
        using(var zip=ZipFile.Open(path,ZipArchiveMode.Update))
        {
            var entry=zip.GetEntry("xl/worksheets/sheet1.xml")!;XDocument document;using(var input=entry.Open())document=XDocument.Load(input);
            XNamespace n="http://schemas.openxmlformats.org/spreadsheetml/2006/main";var cell=document.Descendants(n+"c").First(x=>(string?)x.Attribute("r")=="C2");cell.Attribute("t")?.Remove();cell.Element(n+"v")!.Value="1.7E12";
            entry.Delete();var replacement=zip.CreateEntry("xl/worksheets/sheet1.xml");using var output=replacement.Open();document.Save(output);
        }
        var read=SimpleXlsx.Read(path)["data"];
        Assert.Equal(5003,read.Count);Assert.Equal(5.0m,read[1][1]);Assert.Equal(1_700_000_000_000m,read[1][2]);
    }

    [Fact]
    public void EmptyComponentBackup_WithLocation_IsImportable()
    {
        var source=Store("empty-source.db");Assert.True(source.SaveStorageLocation("ONLY","Only bin").IsSuccess);
        var path=Path.Combine(_root,"empty.xlsx");new InventoryWorkbookBackup(source).Export(path);
        var target=Store("empty-target.db");var backup=new InventoryWorkbookBackup(target);var preview=backup.Preview(path);
        Assert.True(preview.CanImport,string.Join(";",preview.Issues));Assert.Equal(0,preview.Components);
        Assert.True(backup.ImportNewOnly(path,preview).IsSuccess);Assert.Contains(target.GetStorageLocations(),x=>x.Id=="ONLY");
    }

    [Fact]
    public void DatabaseUpgrade_CreatesPrivateSnapshotAndMigratesLegacyAllocation()
    {
        Directory.CreateDirectory(_root);
        var path = Path.Combine(_root, "old.db");
        using (var connection = new SqliteConnection($"Data Source={path}"))
        {
            connection.Open();
            using var command = connection.CreateCommand();
            command.CommandText = "CREATE TABLE components(id TEXT PRIMARY KEY,sku TEXT NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,package_name TEXT NOT NULL,location TEXT NOT NULL,description TEXT,quantity INTEGER NOT NULL,min_stock INTEGER NOT NULL,updated_at TEXT NOT NULL,deleted INTEGER NOT NULL);" +
                "INSERT INTO components VALUES('cmp-old','C8','old','test','0603','A-8','',8,0,'2026-01-01T00:00:00.000Z',0)";
            command.ExecuteNonQuery();
        }
        var store = new InventoryStore(path);
        store.Initialize();
        Assert.Single(Directory.GetFiles(_root, "pre-inventory-protocol1-*.db"));
        var component = Assert.Single(store.GetComponents());
        Assert.Equal(8, Assert.Single(component.Allocations).Quantity);
        Assert.Equal(component.UpdatedAt, component.BaseUpdatedAt);
    }

    private InventoryStore Store(string file = "inventory.db")
    {
        Directory.CreateDirectory(_root);
        var store = new InventoryStore(Path.Combine(_root, file));
        store.Initialize();
        return store;
    }

    private static ComponentDraft Draft(string sku, string location, int quantity) => new()
    {
        Sku = sku, Name = sku, Category = "test", PackageName = "0603", Location = location,
        Description = string.Empty, Quantity = quantity, MinStock = 0,
    };

    public void Dispose()
    {
        Microsoft.Data.Sqlite.SqliteConnection.ClearAllPools();
        if (Directory.Exists(_root)) Directory.Delete(_root, true);
    }
}
