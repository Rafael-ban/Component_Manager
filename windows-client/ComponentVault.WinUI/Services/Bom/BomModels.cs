namespace ComponentVault.WinUI.Services.Bom;

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

public sealed record BomMatchCandidate(string ComponentId, string Sku, string Name, string PackageName);

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
    IReadOnlyDictionary<int, IReadOnlyList<BomMatchCandidate>> Candidates
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
