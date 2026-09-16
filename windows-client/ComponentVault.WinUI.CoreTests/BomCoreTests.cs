using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.Services.Bom;
using ComponentVault.WinUI.Services.Migration;
using Microsoft.Data.Sqlite;
using System.IO.Compression;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class BomCoreTests : IDisposable
{
    private readonly string _testRoot = Path.Combine(Path.GetTempPath(), $"component-vault-bom-{Guid.NewGuid():N}");

    [Fact]
    public void Preview_AggregatesDuplicateSkuRows()
    {
        var inventory = new[] { Component("c1", "ABC", 20) };
        var document = Document(
            new(2, "ABC", null, null, 2),
            new(3, "abc", null, null, 3)
        );

        var preview = new BomPlanningService().CreatePreview(document, new("Project", 2), inventory);

        Assert.True(preview.CanConfirm);
        var line = Assert.Single(preview.Lines);
        Assert.Equal(5, line.UnitQuantity);
        Assert.Equal(10, line.RequiredQuantity);
        Assert.Equal([2, 3], line.SourceRows);
    }

    [Fact]
    public void Preview_BlocksAmbiguousPartAndPackageMatch()
    {
        var inventory = new[]
        {
            Component("c1", "A", 20, "型号：C12345", "0603"),
            Component("c2", "B", 20, "MPN: C12345", "0603"),
        };
        var preview = new BomPlanningService().CreatePreview(
            Document(new BomSourceRow(2, null, "C12345", "0603", 1)),
            new("Project", 1),
            inventory
        );

        Assert.False(preview.CanConfirm);
        Assert.Contains(preview.Issues, issue => issue.Code == "ambiguous");
        Assert.Equal(2, preview.Candidates[2].Count);
    }

    [Fact]
    public void Preview_ExactModelWithoutPackageAutoMatchesOnlyWhenUnique()
    {
        var unique = new BomPlanningService().CreatePreview(
            Document(new BomSourceRow(2, null, "MODEL-1", null, 1)),
            new("Project", 1),
            [Component("c1", "C1", 5, "型号：MODEL-1", "QFN")]
        );
        Assert.True(unique.CanConfirm);
        Assert.Equal("c1", Assert.Single(unique.Lines).ComponentId);

        var ambiguous = new BomPlanningService().CreatePreview(
            Document(new BomSourceRow(2, null, "MODEL-1", null, 1)),
            new("Project", 1),
            [
                Component("c1", "C1", 5, "型号：MODEL-1", "QFN"),
                Component("c2", "C2", 5, "MPN: MODEL-1", "DIP"),
            ]
        );
        Assert.False(ambiguous.CanConfirm);
        Assert.Contains(ambiguous.Issues, issue => issue.Code == "ambiguous");
    }

    [Fact]
    public void SearchInventoryRanksFieldsAndExcludesDeletedItems()
    {
        var items = new[]
        {
            Component("contains", "C3", 1, "型号：X-STM32-G0", "QFN"),
            Component("prefix", "C2", 1, "型号：STM32F103", "LQFP"),
            Component("exact", "C1", 1, "型号：STM32", "LQFP"),
            new ComponentRecord { Id = "deleted", Sku = "STM32", Name = "Old", Category = "Test", PackageName = "DIP", Location = "A", Description = "", Quantity = 1, MinStock = 0, UpdatedAt = "2026-09-15T00:00:00Z", Deleted = true },
        };

        Assert.Equal(
            ["exact", "prefix", "contains"],
            new BomPlanningService().SearchInventory(items, " stm32 ").Select(item => item.ComponentId)
        );
    }

    [Fact]
    public void Preview_SelectionGroupsPreserveEverySourceRowAfterComponentAggregation()
    {
        var component = Component("shared", "C1", 20, "型号：M1\n型号：M2", "0603");
        var replacement = Component("replacement", "C2", 20, "", "0603");
        var document = Document(
            new BomSourceRow(2, null, "M1", "0603", 1),
            new BomSourceRow(3, null, "M2", "0603", 2)
        );
        var preview = new BomPlanningService().CreatePreview(
            document,
            new("Project", 1),
            [component, replacement]
        );

        Assert.Single(preview.Lines);
        Assert.Equal([2], preview.SelectionGroups![2]);
        Assert.Equal([3], preview.SelectionGroups[3]);

        var movedTogether = new BomPlanningService().CreatePreview(
            document,
            new("Project", 1),
            [component, replacement],
            new Dictionary<int, string> { [2] = "replacement", [3] = "replacement" }
        );
        Assert.Equal("replacement", Assert.Single(movedTogether.Lines).ComponentId);
        Assert.Equal(3, movedTogether.Lines[0].RequiredQuantity);

        var splitAgain = new BomPlanningService().CreatePreview(
            document,
            new("Project", 1),
            [component, replacement],
            new Dictionary<int, string> { [2] = "replacement", [3] = "shared" }
        );
        Assert.Equal(2, splitAgain.Lines.Count);
    }

    [Fact]
    public void CsvReader_RecognizesLcscHeaderAndQuotedFields()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "bom.csv");
        File.WriteAllText(path, "LCSC Part #,Footprint,Quantity\n\"C12345\",0603,2");

        var document = new BomFileReader().Read(path);

        var row = Assert.Single(document.Rows);
        Assert.Equal("C12345", row.Sku);
        Assert.Equal("0603", row.PackageName);
        Assert.Equal(2, row.Quantity);
    }

    [Fact]
    public void CsvReader_AllowsQuotedNewlines()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "multiline.csv");
        File.WriteAllText(path, "SKU,Quantity,Note\nC1,2,\"first\nsecond\"");

        var row = Assert.Single(new BomFileReader().Read(path).Rows);

        Assert.Equal("C1", row.Sku);
        Assert.Equal(2, row.Quantity);
    }

    [Fact]
    public void Preview_AggregatesDifferentKeysResolvedToSameComponent()
    {
        var component = Component("c1", "C1", 20, "型号：MPN-1\n品牌：MPN-1", "0603");
        var preview = new BomPlanningService().CreatePreview(
            Document(
                new BomSourceRow(2, "C1", null, null, 1),
                new BomSourceRow(3, null, "MPN-1", "0603", 2)
            ),
            new("Project", 1),
            [component]
        );

        var line = Assert.Single(preview.Lines);
        Assert.Equal(3, line.RequiredQuantity);
        Assert.Equal([2, 3], line.SourceRows);
    }

    [Fact]
    public void Preview_DoesNotMatchPartNumberFromUnrelatedNote()
    {
        var component = Component("c1", "OTHER", 20, "品牌：MPN-1", "0603");
        var preview = new BomPlanningService().CreatePreview(
            Document(new BomSourceRow(2, null, "MPN-1", "0603", 1)),
            new("Project", 1),
            [component]
        );

        Assert.Contains(preview.Issues, issue => issue.Code == "unmatched");
    }

    [Fact]
    public void XlsxReader_RejectsFormulaCellsInsteadOfCachedQuantity()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "formula.xlsx");
        using (var archive = ZipFile.Open(path, ZipArchiveMode.Create))
        {
            WriteEntry(archive, "xl/workbook.xml", """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="BOM" sheetId="1" r:id="rId1"/></sheets></workbook>""");
            WriteEntry(archive, "xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"/></Relationships>""");
            WriteEntry(archive, "xl/worksheets/sheet1.xml", """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>SKU</t></is></c><c r="B1" t="inlineStr"><is><t>Quantity</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>C1</t></is></c><c r="B2"><f>1+1</f><v>2</v></c></row></sheetData></worksheet>""");
        }

        Assert.Throws<InvalidDataException>(() => new BomFileReader().Read(path));
    }

    [Fact]
    public void Confirm_RejectsInventoryChangedAfterPreview()
    {
        var store = CreateStore();
        var saved = store.SaveComponent(Draft("A", 10));
        var line = Line(saved, 2);
        store.SaveComponent(Draft("A", 9, saved.Id));

        var result = store.ConfirmBomConsumption(Request("release-1", "batch-1", [line]));

        Assert.False(result.IsSuccess);
        Assert.Equal(9, Assert.Single(store.GetComponents()).Quantity);
        Assert.Empty(store.GetMovements());
    }

    [Fact]
    public void Confirm_RollsBackAllLinesWhenAnyStockIsInsufficient()
    {
        var store = CreateStore();
        var first = store.SaveComponent(Draft("A", 10));
        var second = store.SaveComponent(Draft("B", 1));

        var result = store.ConfirmBomConsumption(Request(
            "release-2",
            "batch-2",
            [Line(first, 3), Line(second, 2)]
        ));

        Assert.False(result.IsSuccess);
        Assert.Equal(10, store.GetComponents().Single(item => item.Id == first.Id).Quantity);
        Assert.Equal(1, store.GetComponents().Single(item => item.Id == second.Id).Quantity);
        Assert.Empty(store.GetMovements());
    }

    [Fact]
    public void Confirm_RejectsDuplicateComponentLinesAsTransactionGuard()
    {
        var store = CreateStore();
        var component = store.SaveComponent(Draft("A", 10));
        var line = Line(component, 2);

        var result = store.ConfirmBomConsumption(Request("release-duplicate", "batch-duplicate", [line, line]));

        Assert.False(result.IsSuccess);
        Assert.Equal(10, Assert.Single(store.GetComponents()).Quantity);
        Assert.Empty(store.GetMovements());
    }

    [Fact]
    public void Confirm_IsIdempotentForReleaseId()
    {
        var store = CreateStore();
        var component = store.SaveComponent(Draft("A", 10));
        var request = Request("release-3", "batch-3", [Line(component, 4)]);

        var first = store.ConfirmBomConsumption(request);
        var second = store.ConfirmBomConsumption(request);

        Assert.True(first.IsSuccess);
        Assert.True(second.IsSuccess);
        Assert.True(second.AlreadyApplied);
        Assert.Equal(6, Assert.Single(store.GetComponents()).Quantity);
        Assert.Single(store.GetMovements());
    }

    [Fact]
    public void ComponentHubPreview_BlocksDuplicateByDefaultAndCanExplicitlySkip()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "hub.json");
        File.WriteAllText(path, """{"version":"2.0","components":[{"name":"R","productCode":"C1","stock":2,"threshold":0},{"name":"R2","productCode":"C1","stock":3,"threshold":0}]}""");
        var reader = new ComponentHubMigrationReader();

        var blocked = reader.Read(path, [], DuplicateSkuPolicy.Block);
        var skipped = reader.Read(path, [], DuplicateSkuPolicy.Skip);

        Assert.False(blocked.CanConfirm);
        Assert.True(skipped.CanConfirm);
        Assert.Single(skipped.Items);
        Assert.Equal(1, skipped.SkippedDuplicates);
    }

    [Fact]
    public void ComponentHubImport_IsAtomicAndFingerprintPreventsRepeat()
    {
        var store = CreateStore();
        var items = new[]
        {
            new ComponentHubImportItem(1, "C1", "Part", "电阻", "0603", "A", "品牌：X", 5, 1),
        };
        var request = new ComponentHubImportRequest("HASH", DuplicateSkuPolicy.Block, items);

        var first = store.ImportComponentHub(request);
        var second = store.ImportComponentHub(request);

        Assert.True(first.IsSuccess);
        Assert.False(second.IsSuccess);
        Assert.Equal(5, Assert.Single(store.GetComponents()).Quantity);
        var movement = Assert.Single(store.GetMovements());
        Assert.Equal("inbound", movement.MovementType);
        Assert.Equal(5, movement.Quantity);
    }

    [Fact]
    public void OutboundStatistics_AreNotLimitedByRecentMovementList()
    {
        var store = CreateStore();
        var component = store.SaveComponent(Draft("A", 250));
        for (var index = 0; index < 205; index++)
        {
            Assert.True(store.RecordMovement(new MovementEntryDraft
            {
                ComponentId = component.Id,
                MovementType = "outbound",
                Quantity = 1,
                Reason = "test",
            }).IsSuccess);
        }

        Assert.Equal(200, store.GetMovements().Count);
        Assert.Equal(205, store.GetCumulativeOutboundQuantities()[component.Id]);
        Assert.Equal(45, Assert.Single(store.GetComponents()).Quantity);
    }

    [Fact]
    public void ComponentHubPreview_PreservesStructuredMetadataWithoutInventingPackage()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "hub-metadata.json");
        File.WriteAllText(path, """{"components":[{"id":"legacy","name":"Part","model":"MSAP3032KTR-G1","stock":2,"threshold":0,"category":"ic","catalogName":"LED驱动","params":{"voltage":"5V"},"price":1.2,"image":"https://user@assets.lcsc.com/photo.jpg"}]}""");
        var preview = new ComponentHubMigrationReader().Read(path, [], DuplicateSkuPolicy.Block);
        var item = Assert.Single(preview.Items);
        Assert.True(preview.CanConfirm);
        Assert.Equal("未知封装", item.PackageName);
        Assert.Equal("LED驱动", item.Category);
        Assert.Contains("5V", item.Description);
        Assert.Contains("原价格：1.2", item.Description);
        Assert.DoesNotContain("商品图片：", item.Description);
    }

    [Fact]
    public void ComponentHubPreview_NumericIdUsesAndroidCompatibleStableSku()
    {
        Directory.CreateDirectory(_testRoot);
        var path = Path.Combine(_testRoot, "hub-numeric-id.json");
        File.WriteAllText(path, """{"components":[{"id":123,"name":"Part","stock":1,"threshold":0}]}""");
        var reader = new ComponentHubMigrationReader();
        Assert.Equal("CH-D75975A6B3CB", Assert.Single(reader.Read(path, [], DuplicateSkuPolicy.Block).Items).Sku);
    }

    public void Dispose()
    {
        SqliteConnection.ClearAllPools();
        if (Directory.Exists(_testRoot)) Directory.Delete(_testRoot, true);
    }

    private InventoryStore CreateStore()
    {
        var store = new InventoryStore(Path.Combine(_testRoot, "inventory.db"));
        store.Initialize();
        return store;
    }

    private static BomDocument Document(params BomSourceRow[] rows) => new("test.csv", null, rows, []);

    private static ComponentRecord Component(string id, string sku, int quantity, string description = "", string package = "0603") =>
        new() { Id = id, Sku = sku, Name = sku, Category = "Test", PackageName = package, Location = "A", Description = description, Quantity = quantity, MinStock = 0, UpdatedAt = "2026-09-15T00:00:00Z", Deleted = false };

    private static ComponentDraft Draft(string sku, int quantity, string? id = null) =>
        new() { Id = id, Sku = sku, Name = sku, Category = "Test", PackageName = "0603", Location = "A", Description = "", Quantity = quantity, MinStock = 0 };

    private static BomConsumptionLine Line(ComponentRecord component, int required) =>
        new(component.Id, component.Sku, required, required, component.Quantity, component.UpdatedAt, [2]);

    private static BomConfirmRequest Request(string release, string batch, IReadOnlyList<BomConsumptionLine> lines) =>
        new(release, batch, "Project", 1, "fingerprint", lines);

    private static void WriteEntry(ZipArchive archive, string path, string content)
    {
        using var writer = new StreamWriter(archive.CreateEntry(path).Open());
        writer.Write(content);
    }
}
