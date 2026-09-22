using System.Globalization;
using System.IO.Compression;
using System.Text;
using System.Xml.Linq;

namespace ComponentVault.WinUI.Services.Bom;

public sealed class BomFileReader
{
    public const long MaxFileBytes = 10 * 1024 * 1024;
    public const int MaxRows = 5000;

    private static readonly string[] SkuHeaders = ["sku", "supplier part", "supplier part number", "lcsc part", "lcsc part #", "立创编号", "立创料号", "库存编码", "物料编码", "物料编号"];
    private static readonly string[] PartHeaders = ["mpn", "manufacturer part", "manufacturer part number", "供应商型号", "制造商型号", "型号"];
    private static readonly string[] PackageHeaders = ["package", "footprint", "封装"];
    private static readonly string[] QuantityHeaders = ["quantity", "qty", "用量", "数量"];

    public IReadOnlyList<string> GetWorksheetNames(string filePath)
    {
        ValidateFile(filePath, ".xlsx");
        using var archive = ZipFile.OpenRead(filePath);
        return ReadWorkbookSheets(archive).Select(sheet => sheet.Name).ToArray();
    }

    public BomDocument Read(string filePath, string? worksheetName = null, BomColumnMapping? mapping = null)
    {
        ValidateFile(filePath, ".csv", ".xlsx");
        return Path.GetExtension(filePath).Equals(".csv", StringComparison.OrdinalIgnoreCase)
            ? ReadCsv(filePath, mapping)
            : ReadXlsx(filePath, worksheetName, mapping);
    }

    public BomTableInspection Inspect(string filePath, string? worksheetName = null)
    {
        ValidateFile(filePath, ".csv", ".xlsx");
        var raw = Path.GetExtension(filePath).Equals(".csv", StringComparison.OrdinalIgnoreCase)
            ? ReadCsvRecords(filePath)
            : ReadXlsxRecords(filePath, worksheetName);
        var headerIndex = FindHeaderIndex(raw.Records);
        var headers = raw.Records[headerIndex].Select(value => value.Trim()).ToArray();
        return new(raw.SourceName, raw.WorksheetName, headers, headerIndex + 1, DetectMapping(headers));
    }

    private static BomDocument ReadCsv(string filePath, BomColumnMapping? mapping)
    {
        var raw = ReadCsvRecords(filePath);
        return BuildDocument(raw.SourceName, null, raw.Records, mapping);
    }

    private static RawTable ReadCsvRecords(string filePath) { using var reader = new StreamReader(filePath, Encoding.UTF8, true); return new(Path.GetFileName(filePath), null, ParseCsvRecords(reader)); }

    private static BomDocument ReadXlsx(string filePath, string? worksheetName, BomColumnMapping? mapping)
    {
        var raw = ReadXlsxRecords(filePath, worksheetName);
        return BuildDocument(raw.SourceName, raw.WorksheetName, raw.Records, mapping);
    }

    private static RawTable ReadXlsxRecords(string filePath, string? worksheetName)
    {
        using var archive = ZipFile.OpenRead(filePath);
        var sheets = ReadWorkbookSheets(archive);
        var sheet = string.IsNullOrWhiteSpace(worksheetName)
            ? sheets.FirstOrDefault()
            : sheets.FirstOrDefault(item => string.Equals(item.Name, worksheetName, StringComparison.Ordinal));
        if (sheet is null)
        {
            throw new InvalidDataException("未找到指定的工作表。");
        }

        var sharedStrings = ReadSharedStrings(archive);
        var entry = archive.GetEntry(sheet.Path) ?? throw new InvalidDataException("工作表数据缺失。");
        ValidateXmlEntry(entry);
        using var stream = entry.Open();
        var document = XDocument.Load(stream, LoadOptions.None);
        XNamespace ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        var records = new List<IReadOnlyList<string>>();
        foreach (var row in document.Descendants(ns + "row"))
        {
            if (records.Count > MaxRows)
            {
                throw new InvalidDataException($"BOM 超过 {MaxRows} 行限制。");
            }
            var values = new SortedDictionary<int, string>();
            foreach (var cell in row.Elements(ns + "c"))
            {
                if (cell.Element(ns + "f") is not null)
                {
                    throw new InvalidDataException("BOM 工作表包含公式单元格；请粘贴为数值后再导入，避免使用过期缓存值。");
                }
                var reference = (string?)cell.Attribute("r") ?? string.Empty;
                var index = ColumnIndex(reference);
                var type = (string?)cell.Attribute("t");
                var raw = (string?)cell.Element(ns + "v") ?? string.Concat(cell.Descendants(ns + "t").Select(item => item.Value));
                if (type == "s" && int.TryParse(raw, NumberStyles.None, CultureInfo.InvariantCulture, out var sharedIndex) && sharedIndex >= 0 && sharedIndex < sharedStrings.Count)
                {
                    raw = sharedStrings[sharedIndex];
                }
                values[index] = raw;
            }
            var width = values.Count == 0 ? 0 : values.Keys.Max() + 1;
            var record = Enumerable.Range(0, width).Select(index => values.GetValueOrDefault(index, string.Empty)).ToArray();
            records.Add(record);
        }
        return new(Path.GetFileName(filePath), sheet.Name, records);
    }

    private static BomDocument BuildDocument(
        string sourceName,
        string? worksheetName,
        IReadOnlyList<IReadOnlyList<string>> records,
        BomColumnMapping? mapping = null
    )
    {
        var headerIndex = FindHeaderIndex(records);
        var header = records[headerIndex];
        var selected = mapping ?? DetectMapping(header);
        selected.Validate(header.Count);
        var skuColumn = selected.Sku ?? -1;
        var partColumn = selected.Model ?? -1;
        var packageColumn = selected.Package ?? -1;
        var quantityColumn = selected.Quantity!.Value;

        var rows = new List<BomSourceRow>();
        for (var index = headerIndex + 1; index < records.Count; index++)
        {
            var record = records[index];
            if (record.All(string.IsNullOrWhiteSpace))
            {
                continue;
            }
            var quantityText = Cell(record, quantityColumn);
            int? quantity = int.TryParse(quantityText, NumberStyles.Integer, CultureInfo.InvariantCulture, out var parsed) ? parsed : null;
            rows.Add(new(index + 1, Cell(record, skuColumn), Cell(record, partColumn), Cell(record, packageColumn), quantity));
        }
        return new(sourceName, worksheetName, rows, []);
    }

    private static int FindHeaderIndex(IReadOnlyList<IReadOnlyList<string>> records)
    {
        if (records.Count == 0) throw new InvalidDataException("工作表没有表头。");
        var recognized = records.Select((row, index) => (row, index)).FirstOrDefault(item => FindColumn(item.row, QuantityHeaders) >= 0);
        if (recognized.row is not null) return recognized.index;
        var first = records.Select((row, index) => (row, index)).FirstOrDefault(item => item.row.Any(value => !string.IsNullOrWhiteSpace(value)));
        if (first.row is null) throw new InvalidDataException("工作表没有表头。");
        return first.index;
    }

    private static BomColumnMapping DetectMapping(IReadOnlyList<string> header) => new(
        NullColumn(FindColumn(header, SkuHeaders)), NullColumn(FindColumn(header, PartHeaders)),
        NullColumn(FindColumn(header, PackageHeaders)), NullColumn(FindColumn(header, QuantityHeaders)));

    private static int? NullColumn(int value) => value < 0 ? null : value;

    private static IReadOnlyList<SheetReference> ReadWorkbookSheets(ZipArchive archive)
    {
        XNamespace main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        XNamespace relationships = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
        XNamespace packageRelationships = "http://schemas.openxmlformats.org/package/2006/relationships";
        var workbook = LoadXml(archive, "xl/workbook.xml");
        var relationDocument = LoadXml(archive, "xl/_rels/workbook.xml.rels");
        var relationPaths = relationDocument.Descendants(packageRelationships + "Relationship")
            .ToDictionary(item => (string)item.Attribute("Id")!, item => (string)item.Attribute("Target")!);
        return workbook.Descendants(main + "sheet").Select(item =>
        {
            var id = (string)item.Attribute(relationships + "id")!;
            var target = relationPaths[id].Replace('\\', '/').TrimStart('/');
            return new SheetReference((string)item.Attribute("name")!, target.StartsWith("xl/") ? target : $"xl/{target}");
        }).ToArray();
    }

    private static IReadOnlyList<string> ReadSharedStrings(ZipArchive archive)
    {
        var entry = archive.GetEntry("xl/sharedStrings.xml");
        if (entry is null) return [];
        ValidateXmlEntry(entry);
        using var stream = entry.Open();
        var document = XDocument.Load(stream, LoadOptions.None);
        XNamespace ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
        return document.Descendants(ns + "si").Select(item => string.Concat(item.Descendants(ns + "t").Select(text => text.Value))).ToArray();
    }

    private static XDocument LoadXml(ZipArchive archive, string path)
    {
        var entry = archive.GetEntry(path) ?? throw new InvalidDataException($"XLSX 缺少 {path}。");
        ValidateXmlEntry(entry);
        using var stream = entry.Open();
        return XDocument.Load(stream, LoadOptions.None);
    }

    private static IReadOnlyList<IReadOnlyList<string>> ParseCsvRecords(TextReader reader)
    {
        var records = new List<IReadOnlyList<string>>();
        var record = new List<string>();
        var value = new StringBuilder();
        var quoted = false;
        while (reader.Read() is var next && next >= 0)
        {
            var character = (char)next;
            if (character == '"')
            {
                if (quoted && reader.Peek() == '"')
                {
                    value.Append('"');
                    reader.Read();
                }
                else quoted = !quoted;
            }
            else if (character == ',' && !quoted)
            {
                record.Add(value.ToString());
                value.Clear();
            }
            else if ((character == '\r' || character == '\n') && !quoted)
            {
                if (character == '\r' && reader.Peek() == '\n') reader.Read();
                record.Add(value.ToString()); value.Clear();
                records.Add(record.ToArray()); record.Clear();
                if (records.Count > MaxRows + 1) throw new InvalidDataException($"BOM 超过 {MaxRows} 行限制。");
            }
            else
            {
                value.Append(character);
                if (value.Length > MaxFileBytes) throw new InvalidDataException("CSV 单元格超过文件限制。");
            }
        }
        if (quoted) throw new InvalidDataException("CSV 包含未闭合的引号。");
        if (value.Length > 0 || record.Count > 0)
        {
            record.Add(value.ToString()); records.Add(record.ToArray());
        }
        return records;
    }

    private static int FindColumn(IReadOnlyList<string> header, IReadOnlyList<string> aliases) =>
        Enumerable.Range(0, header.Count).FirstOrDefault(index => aliases.Contains(header[index].Trim(), StringComparer.OrdinalIgnoreCase), -1);

    private static string? Cell(IReadOnlyList<string> row, int index) => index >= 0 && index < row.Count ? row[index].Trim() : null;

    private static int ColumnIndex(string reference)
    {
        var result = 0;
        foreach (var character in reference.TakeWhile(char.IsLetter)) result = checked(result * 26 + char.ToUpperInvariant(character) - 'A' + 1);
        var index = Math.Max(0, result - 1);
        if (index >= 16_384) throw new InvalidDataException("XLSX 列索引超出支持范围。");
        return index;
    }

    private static void ValidateXmlEntry(ZipArchiveEntry entry)
    {
        if (entry.Length > MaxFileBytes) throw new InvalidDataException("XLSX 解压后的 XML 超过 10 MiB 限制。");
    }

    private static void ValidateFile(string filePath, params string[] extensions)
    {
        var info = new FileInfo(filePath);
        if (!info.Exists) throw new FileNotFoundException("BOM 文件不存在。", filePath);
        if (info.Length > MaxFileBytes) throw new InvalidDataException($"BOM 文件超过 {MaxFileBytes / 1024 / 1024} MiB 限制。");
        if (!extensions.Contains(info.Extension, StringComparer.OrdinalIgnoreCase)) throw new InvalidDataException("仅支持 CSV 或 XLSX BOM。" );
    }

    private sealed record SheetReference(string Name, string Path);
    private sealed record RawTable(string SourceName, string? WorksheetName, IReadOnlyList<IReadOnlyList<string>> Records);
}
