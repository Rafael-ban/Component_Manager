using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class LabelWorkbookExporterTests : IDisposable
{
    private readonly string _path = Path.Combine(Path.GetTempPath(), $"component-vault-labels-{Guid.NewGuid():N}.xlsx");

    [Fact]
    public void ExportWritesRealWorkbookWithSelectedTextColumnsAndBothScanPayloads()
    {
        var component = Component("0012|<&\"", "电阻 & <测试>");
        LabelWorkbookExporter.Export(_path, [component], LabelWorkbookExporter.DefaultColumns);

        var rows = Assert.Single(SimpleXlsx.Read(_path)).Value;
        Assert.Equal(new[] { "名称", "SKU", "长二维码文本", "短二维码文本" }, rows[0].Select(Convert.ToString));
        Assert.Equal("0012|<&\"", rows[1][1]);
        Assert.Equal("{\"fmt\":\"component-vault-label\",\"v\":1,\"sku\":\"0012|<&\\\"\",\"name\":\"电阻 & <测试>\",\"cat\":\"电阻\",\"pkg\":\"0603\",\"loc\":\"A-01\",\"qty\":0,\"min\":0,\"model\":\"R<&\\\"\",\"brand\":\"中文牌\"}", Assert.IsType<string>(rows[1][2]));
        Assert.Equal("cvl3|0012%7C%3C%26%22|0", Assert.IsType<string>(rows[1][3]));
    }

    [Fact]
    public void ExportSupportsEmptyInventoryAndSkipsDeletedComponents()
    {
        LabelWorkbookExporter.Export(_path, [Component("gone", deleted: true)], LabelWorkbookExporter.DefaultColumns);
        var rows = Assert.Single(SimpleXlsx.Read(_path)).Value;
        Assert.Single(rows);
    }

    [Fact]
    public void LongQrTextKeepsExistingJlcCompatibleCodecBehavior()
    {
        var component = WithDescription(Component("C1234"), "型号：RC 0603\n导入来源：JLC 包装二维码\n原始载荷：{on:old}");
        var payload = LabelWorkbookExporter.LongQrText(component);
        Assert.StartsWith("{on:local-C1234,pc:C1234,pm:RC+0603,qty:0", payload);
        Assert.Contains("nm:%E5%90%8D%E7%A7%B0", payload);
    }

    [Fact]
    public void SelectedLocationsExportAllocationRowsAndMatchingQrQuantities()
    {
        var component = WithAllocations(Component("parts"),
            new("id-parts", "A-01", 2), new("id-parts", "B-02", 5));
        var locations = new[] { Location("A-01"), Location("B-02") };
        var columns = new HashSet<LabelWorkbookColumn>
        {
            LabelWorkbookColumn.Location, LabelWorkbookColumn.Quantity,
            LabelWorkbookColumn.LongQrText, LabelWorkbookColumn.ShortQrText,
        };
        var rows = LabelWorkbookExporter.CreateRows([component], locations, new HashSet<string> { "A-01", "B-02" });
        LabelWorkbookExporter.ExportRows(_path, rows, columns);

        var sheet = Assert.Single(SimpleXlsx.Read(_path)).Value;
        Assert.Equal(3, sheet.Count);
        Assert.Equal("A-01", sheet[1][0]);
        Assert.Equal("2", Convert.ToString(sheet[1][1]));
        Assert.Contains("\"loc\":\"A-01\",\"qty\":2", Assert.IsType<string>(sheet[1][2]));
        Assert.Equal("cvl3|parts|2", sheet[1][3]);
        Assert.Equal("B-02", sheet[2][0]);
        Assert.Equal("5", Convert.ToString(sheet[2][1]));
        Assert.Contains("\"loc\":\"B-02\",\"qty\":5", Assert.IsType<string>(sheet[2][2]));
        Assert.Equal("cvl3|parts|5", sheet[2][3]);
        Assert.Single(LabelWorkbookExporter.CreateRows([component], locations, null));
    }

    [Fact]
    public void EmptySelectionAndNoMatchingAllocationYieldNoRows()
    {
        var component = WithAllocations(Component("parts"), new("id-parts", "A-01", 2));
        var locations = new[] { Location("A-01"), Location("B-02") };
        Assert.Empty(LabelWorkbookExporter.CreateRows([component], locations, new HashSet<string>()));
        Assert.Empty(LabelWorkbookExporter.CreateRows([component], locations, new HashSet<string> { "B-02" }));
    }

    [Fact]
    public void DeletedComponentAndLocationAreExcludedButZeroAllocationIsKept()
    {
        var active = WithAllocations(Component("active"),
            new("id-active", "A-01", 0), new("id-active", "B-02", 3));
        var deleted = WithAllocations(Component("gone", deleted: true), new("id-gone", "B-02", 4));
        var rows = LabelWorkbookExporter.CreateRows([active, deleted],
            [Location("A-01"), Location("B-02", deleted: true)], new HashSet<string> { "A-01", "B-02" });
        var row = Assert.Single(rows);
        Assert.Equal("A-01", row.Location);
        Assert.Equal(0, row.Quantity);
    }

    [Fact]
    public void LegacyComponentWithoutAllocationsUsesSingleLocationAndTotal()
    {
        var rows = LabelWorkbookExporter.CreateRows([Component("legacy")], [Location("A-01")],
            new HashSet<string> { "A-01" });
        var row = Assert.Single(rows);
        Assert.Equal("A-01", row.Location);
        Assert.Equal(0, row.Quantity);
        Assert.Single(LabelWorkbookExporter.CreateRows([Component("legacy")], [], new HashSet<string> { "A-01" }));
        var blank = new ComponentRecord
        {
            Id = "blank", Sku = "blank", Name = "空位", Category = "电阻", PackageName = "0603",
            Location = "", Description = "", Quantity = 4, MinStock = 0,
            UpdatedAt = "2026-09-22T00:00:00.000Z", Deleted = false,
        };
        Assert.Equal(4, Assert.Single(LabelWorkbookExporter.CreateRows([blank], [], new HashSet<string> { "" })).Quantity);
    }

    private static ComponentRecord Component(string sku, string name = "名称", bool deleted = false) => new()
    {
        Id = $"id-{sku}", Sku = sku, Name = name, Category = "电阻", PackageName = "0603", Location = "A-01",
        Description = "型号：R<&\"\n品牌：中文牌", Quantity = 0, MinStock = 0,
        UpdatedAt = "2026-09-22T00:00:00.000Z", Deleted = deleted,
    };

    private static ComponentRecord WithDescription(ComponentRecord component, string description) => new()
    {
        Id = component.Id, Sku = component.Sku, Name = component.Name, Category = component.Category,
        PackageName = component.PackageName, Location = component.Location, Description = description,
        Quantity = component.Quantity, MinStock = component.MinStock, UpdatedAt = component.UpdatedAt, Deleted = component.Deleted,
    };

    private static ComponentRecord WithAllocations(ComponentRecord component, params ComponentAllocationRecord[] allocations) => new()
    {
        Id = component.Id, Sku = component.Sku, Name = component.Name, Category = component.Category,
        PackageName = component.PackageName, Location = component.Location, Description = component.Description,
        Quantity = component.Quantity, MinStock = component.MinStock, UpdatedAt = component.UpdatedAt,
        Deleted = component.Deleted, Allocations = allocations,
    };

    private static StorageLocationRecord Location(string id, bool deleted = false) =>
        new(id, id, "2026-09-22T00:00:00.000Z", deleted);

    public void Dispose() { if (File.Exists(_path)) File.Delete(_path); }
}
