using System.Text.Json;

namespace ComponentVault.WinUI.Services.Bom;

public sealed record BomPreset(
    string Name,
    BomDocument Document,
    string ProjectName,
    int BatchQuantity,
    Dictionary<int, string> SelectedComponents,
    List<int> SkippedRows
);

public sealed class BomPresetStore
{
    private readonly string _path;

    public BomPresetStore(string? path = null) => _path = path ?? Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "ComponentVault", "bom-presets.json");

    public IReadOnlyList<BomPreset> LoadAll()
    {
        if (!File.Exists(_path)) return [];
        try
        {
            var presets = JsonSerializer.Deserialize<List<BomPreset>>(File.ReadAllText(_path))
                ?? throw new InvalidDataException("预设内容为空。");
            if (presets.Any(preset => string.IsNullOrWhiteSpace(preset.Name) || preset.Document is null || preset.Document.Rows is null))
                throw new InvalidDataException("预设内容不完整。");
            return presets;
        }
        catch (Exception error) when (error is JsonException or IOException or UnauthorizedAccessException or InvalidDataException)
        {
            throw new InvalidDataException("BOM 预设无法读取；原文件已保留，请先备份或修复。", error);
        }
    }

    public void Save(BomPreset preset)
    {
        if (string.IsNullOrWhiteSpace(preset.Name) || preset.Name.Trim().Length > 100 || preset.Document.Rows.Count == 0)
            throw new ArgumentException("预设名称或 BOM 内容无效。", nameof(preset));
        var presets = LoadAll().Where(item => !string.Equals(item.Name, preset.Name.Trim(), StringComparison.OrdinalIgnoreCase)).ToList();
        presets.Add(preset with { Name = preset.Name.Trim() });
        Write(presets.OrderBy(item => item.Name, StringComparer.OrdinalIgnoreCase).ToArray());
    }

    public bool Delete(string name)
    {
        var presets = LoadAll();
        var remaining = presets.Where(item => !string.Equals(item.Name, name, StringComparison.OrdinalIgnoreCase)).ToArray();
        if (remaining.Length == presets.Count) return false;
        Write(remaining);
        return true;
    }

    private void Write(IReadOnlyList<BomPreset> presets)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_path)!);
        var temporary = _path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            File.WriteAllText(temporary, JsonSerializer.Serialize(presets));
            File.Move(temporary, _path, true);
        }
        finally
        {
            if (File.Exists(temporary)) File.Delete(temporary);
        }
    }
}
