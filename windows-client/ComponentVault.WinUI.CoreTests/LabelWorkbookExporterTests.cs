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

    public void Dispose() { if (File.Exists(_path)) File.Delete(_path); }
}
