using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.Services.BatchInbound;
using Microsoft.Data.Sqlite;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class BatchJlcInboundTests : IDisposable
{
    private readonly string _root = Path.Combine(Path.GetTempPath(), "cv-batch-" + Guid.NewGuid().ToString("N"));
    [Fact]
    public void Collector_DeduplicatesExactRawButKeepsDifferentPackagesForSameSku()
    { var drafts = new BatchInboundDraftStore(Path.Combine(_root, "draft.json")); var a = "{\"pc\":\"C123\",\"qty\":\"2\",\"on\":\"bag-a\"}"; var b = "{\"pc\":\"C123\",\"qty\":\"3\",\"on\":\"bag-b\"}"; var result = drafts.Merge([a, "  " + a + "  ", b]); Assert.Equal(2, result.Items.Count); Assert.Equal(1, result.Duplicates); Assert.Equal(new int?[] { 2, 3 }, result.Items.Select(x => JlcQrParser.Parse(x.Raw).Quantity)); Assert.Equal(2, drafts.Load().Count); }
    [Theory]
    [InlineData("{\"pc\":\"C1\"}")]
    [InlineData("{\"pc\":\"C1\",\"qty\":0}")]
    [InlineData("{\"pc\":\"C1\",\"qty\":\"x\"}")]
    [InlineData("https://example.test/order/123")]
    public void Parser_NeverDefaultsMissingOrInvalidQuantity(string raw) { var parsed = JlcQrParser.Parse(raw); Assert.Null(parsed.Quantity); Assert.NotNull(parsed.ParseError); }
    [Fact] public void Parser_AcceptsUniqueCInUriButRejectsPcThatIsNotC() { Assert.Equal("C88", JlcQrParser.Parse("https://x.test/item/C88?order=123").Sku); Assert.Empty(JlcQrParser.Parse("{\"pc\":\"123\",\"qty\":1}").Sku); }
    [Fact] public void Parser_AcceptsStandardUnquotedJlcObject() { var parsed = JlcQrParser.Parse("{on:SO2502,pc:C30926,pm:0603B104K500NT,qty:300,mc:null,cc:1,pdi:144390018,hp:11}"); Assert.Equal("C30926", parsed.Sku); Assert.Equal(300, parsed.Quantity); Assert.Equal("0603B104K500NT", parsed.Model); }
    [Fact]
    public void Commit_AppendsExistingAllocationAndReceiptReplayIsIdempotent()
    { var store = Store(); var component = store.SaveComponent(new ComponentDraft { Sku = "C50", Name = "old", Category = "old", PackageName = "old", Location = "A", Description = "keep", Quantity = 4, MinStock = 0 }); store.SaveStorageLocation("B", "B"); var line = Line("C50", 3, "B", name: "replacement"); var request = new BatchInboundCommitRequest([line]); Assert.True(store.CommitBatchInbound(request).IsSuccess); Assert.True(store.CommitBatchInbound(request).IsSuccess); var current = Assert.Single(store.GetComponents()); Assert.Equal("old", current.Name); Assert.Equal("keep", current.Description); Assert.Equal(7, current.Quantity); Assert.Equal(3, current.Allocations.Single(x => x.LocationId == "B").Quantity); Assert.Single(store.GetMovements()); Assert.True(store.IsBatchReceiptCommitted(line.ReceiptId)); }
    [Fact]
    public void MixedNewAndExistingFailure_RollsBackEntireBatch()
    { var store = Store(); store.SaveComponent(new ComponentDraft { Sku = "C1", Name = "old", Category = "cat", PackageName = "pkg", Location = "A", Description = "", Quantity = 1, MinStock = 0 }); var good = Line("C1", 2, "A"); var invalid = Line("C2", 1, "A", name: "") with { Category = "", PackageName = "" }; var result = store.CommitBatchInbound(new([good, invalid])); Assert.False(result.IsSuccess); Assert.Equal(1, Assert.Single(store.GetComponents()).Quantity); Assert.Empty(store.GetMovements()); Assert.False(store.IsBatchReceiptCommitted(good.ReceiptId)); }
    [Fact]
    public void SingleCatalogInbound_AppendsQuantityAndRejectsStaleConfirmation()
    { var store = Store(); var existing = store.SaveComponent(new ComponentDraft { Sku = "C15", Name = "keep", Category = "cat", PackageName = "pkg", Location = "A", Description = "metadata", Quantity = 15, MinStock = 0 }); var added = store.AppendCatalogInbound("c15", 15, "A", existing.UpdatedAt); Assert.True(added.IsSuccess, added.Message); var current = Assert.Single(store.GetComponents()); Assert.Equal(30, current.Quantity); Assert.Equal("keep", current.Name); Assert.Equal("metadata", current.Description); Assert.Single(store.GetMovements()); var stale = store.AppendCatalogInbound("C15", 1, "A", existing.UpdatedAt); Assert.False(stale.IsSuccess); Assert.Equal(30, Assert.Single(store.GetComponents()).Quantity); Assert.Single(store.GetMovements()); }
    [Fact]
    public void DraftRecovery_CanFilterAlreadyCommittedReceipt()
    { var path = Path.Combine(_root, "draft.json"); var drafts = new BatchInboundDraftStore(path); var item = Assert.Single(drafts.Merge(["{\"pc\":\"C9\",\"qty\":1}"]).Items); var store = Store(); store.SaveStorageLocation("A", "A"); Assert.True(store.CommitBatchInbound(new([Line("C9", 1, "A", item.ReceiptId)])).IsSuccess); Assert.True(store.IsBatchReceiptCommitted(Assert.Single(new BatchInboundDraftStore(path).Load()).ReceiptId)); }
    [Fact]
    public void DraftRecovery_PreservesEditsAndNeverSilentlyOverwritesCorruption()
    { Directory.CreateDirectory(_root); var path = Path.Combine(_root, "state.json"); var store = new BatchInboundDraftStore(path); var item = new BatchScanItem(Guid.NewGuid().ToString("D"), "raw") { Status = "pending", Sku = "C7", QuantityText = "9", LocationId = "A", Name = "edited", Category = "cat", PackageName = "pkg", Description = "notes", Error = "retry" }; store.Save([item]); Assert.Equal(item, Assert.Single(new BatchInboundDraftStore(path).Load())); File.WriteAllText(path, "{broken"); Assert.Throws<InvalidDataException>(() => store.Load()); Assert.Equal("{broken", File.ReadAllText(path)); }
    private InventoryStore Store() { Directory.CreateDirectory(_root); var store = new InventoryStore(Path.Combine(_root, "db.sqlite")); store.Initialize(); return store; }
    private static BatchInboundLine Line(string sku, int qty, string location, string? receipt = null, string name = "part") => new(receipt ?? Guid.NewGuid().ToString("D"), "private raw", sku, qty, location, name, "cat", "0603", "");
    public void Dispose() { SqliteConnection.ClearAllPools(); if (Directory.Exists(_root)) Directory.Delete(_root, true); }
}
