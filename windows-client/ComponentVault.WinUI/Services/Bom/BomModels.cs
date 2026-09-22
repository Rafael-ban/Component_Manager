namespace ComponentVault.WinUI.Services.Bom;

public sealed record BomColumnMapping(int? Sku, int? Model, int? Package, int? Quantity, int? Name = null, int? Reference = null)
{
    public void Validate(int columnCount)
    {
        if (Quantity is null) throw new InvalidDataException("请选择需求数量列。");
        if (Sku is null && Model is null) throw new InvalidDataException("请至少选择 SKU 或型号列。");
        if (new[] { Sku, Model, Package, Quantity, Name, Reference }.Where(value => value is not null).Any(value => value < 0 || value >= columnCount))
            throw new InvalidDataException("列映射超出表头范围。");
    }
}

public sealed record BomTableInspection(string SourceName, string? WorksheetName, IReadOnlyList<string> Headers, int HeaderRowNumber, BomColumnMapping AutomaticMapping);

public sealed record BomSourceRow(
    int RowNumber,
    string? Sku,
    string? SupplierPartNumber,
    string? PackageName,
    int? Quantity
);

public sealed record BomDocument(
    string SourceName,
    string? WorksheetName,
    IReadOnlyList<BomSourceRow> Rows,
    IReadOnlyList<string> Warnings
);

public sealed record BomImportOptions(string ProjectName, int BatchQuantity);

public sealed record BomIssue(int? RowNumber, string Code, string Message);

public sealed record BomMatchCandidate(string ComponentId, string Sku, string Name, string PackageName, string? Model = null)
{
    public override string ToString() => string.Join(" · ", new[] { Sku, Model, PackageName, Name }.Where(value => !string.IsNullOrWhiteSpace(value)).Distinct());
}

public sealed record BomConsumptionLine(
    string ComponentId,
    string ComponentSku,
    int UnitQuantity,
    int RequiredQuantity,
    int AvailableQuantity,
    string ExpectedUpdatedAt,
    IReadOnlyList<int> SourceRows
);

public sealed record BomPreview(
    string ProjectName,
    int BatchQuantity,
    IReadOnlyList<BomConsumptionLine> Lines,
    IReadOnlyList<BomIssue> Issues,
    IReadOnlyDictionary<int, IReadOnlyList<BomMatchCandidate>> Candidates,
    IReadOnlyDictionary<int, IReadOnlyList<int>>? SelectionGroups = null
)
{
    public bool CanConfirm => Issues.Count == 0 && Lines.Count > 0;
}

public sealed record BomConfirmRequest(
    string ReleaseId,
    string BatchId,
    string ProjectName,
    int BatchQuantity,
    string SourceFingerprint,
    IReadOnlyList<BomConsumptionLine> Lines
);

public sealed record BomConfirmResult(bool IsSuccess, bool AlreadyApplied, string Message)
{
    public static BomConfirmResult Success(string message) => new(true, false, message);
    public static BomConfirmResult Duplicate(string message) => new(true, true, message);
    public static BomConfirmResult Failure(string message) => new(false, false, message);
}
