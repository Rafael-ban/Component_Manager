namespace ComponentVault.WinUI.Models;

public sealed record StorageLocationRecord(string Id, string Name, string UpdatedAt, bool Deleted)
{
    public override string ToString() => string.Equals(Id, Name, StringComparison.Ordinal) ? Id : $"{Id} · {Name}";
}

public sealed record ComponentAllocationRecord(string ComponentId, string LocationId, int Quantity)
{
    public override string ToString() => $"{LocationId} · {Quantity}";
}
