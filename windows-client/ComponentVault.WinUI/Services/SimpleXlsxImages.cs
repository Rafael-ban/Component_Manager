using System.IO.Compression;
using System.Xml.Linq;

namespace ComponentVault.WinUI.Services;

internal static partial class SimpleXlsx
{
    private static readonly XNamespace Drawing = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing";
    private static readonly XNamespace A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static readonly XNamespace R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static readonly XNamespace PackageRels = "http://schemas.openxmlformats.org/package/2006/relationships";

    private static void AppendImages(string path, string[] sheetNames, IReadOnlyList<WorkbookImage> images)
    {
        using var zip = ZipFile.Open(path, ZipArchiveMode.Update);
        var contentTypes = Load(zip, "[Content_Types].xml");
        var types = contentTypes.Root!;
        XNamespace ct = types.Name.Namespace;
        EnsureDefault(types, ct, "png", "image/png"); EnsureDefault(types, ct, "jpg", "image/jpeg");
        var drawingNumber = 0; var mediaNumber = 0;
        foreach (var group in images.GroupBy(image => image.SheetName))
        {
            var sheetIndex = Array.IndexOf(sheetNames, group.Key) + 1;
            if (sheetIndex <= 0) throw new InvalidDataException("图片引用了不存在的工作表。");
            drawingNumber++;
            var sheetPath = $"xl/worksheets/sheet{sheetIndex}.xml";
            var sheet = Load(zip, sheetPath);
            sheet.Root!.SetAttributeValue(XNamespace.Xmlns + "r", R);
            sheet.Root!.Add(new XElement(N + "drawing", new XAttribute(R + "id", "rIdImage")));
            Replace(zip, sheetPath, sheet);
            Replace(zip, $"xl/worksheets/_rels/sheet{sheetIndex}.xml.rels", Rels([("rIdImage", $"../drawings/drawing{drawingNumber}.xml", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing")]));
            var anchors = new List<XElement>(); var rels = new List<(string,string,string)>(); var local = 0;
            foreach (var image in group)
            {
                local++; mediaNumber++; var ext = ImageExtension(image.Bytes); var mediaPath = $"xl/media/image{mediaNumber}.{ext}";
                var mediaEntry = zip.CreateEntry(mediaPath); using (var output = mediaEntry.Open()) output.Write(image.Bytes);
                var relId = $"rId{local}"; rels.Add((relId, $"../media/image{mediaNumber}.{ext}", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"));
                anchors.Add(OneCellAnchor(image, relId, local));
            }
            Replace(zip, $"xl/drawings/drawing{drawingNumber}.xml", new XDocument(new XElement(Drawing + "wsDr", new XAttribute(XNamespace.Xmlns + "xdr", Drawing), new XAttribute(XNamespace.Xmlns + "a", A), anchors)));
            Replace(zip, $"xl/drawings/_rels/drawing{drawingNumber}.xml.rels", Rels(rels));
            types.Add(new XElement(ct + "Override", new XAttribute("PartName", $"/xl/drawings/drawing{drawingNumber}.xml"), new XAttribute("ContentType", "application/vnd.openxmlformats-officedocument.drawing+xml")));
        }
        Replace(zip, "[Content_Types].xml", contentTypes);
    }

    public static Dictionary<int, byte[]> ReadImages(string path, string sheetName, IReadOnlySet<int> allowedColumns)
    {
        using var zip = ZipFile.OpenRead(path);
        ValidateArchive(zip);
        var workbook = Load(zip, "xl/workbook.xml"); var workbookRels = Load(zip, "xl/_rels/workbook.xml.rels");
        var sheet = workbook.Descendants(N + "sheet").FirstOrDefault(x => (string?)x.Attribute("name") == sheetName);
        if (sheet is null) return [];
        var relationId = (string?)sheet.Attribute(R + "id") ?? "";
        var target = workbookRels.Descendants(PackageRels + "Relationship").First(x => (string?)x.Attribute("Id") == relationId).Attribute("Target")!.Value;
        var sheetPath = ResolvePart("xl/workbook.xml", target); var sheetDocument = Load(zip, sheetPath);
        var drawingId = (string?)sheetDocument.Descendants(N + "drawing").FirstOrDefault()?.Attribute(R + "id");
        if (drawingId is null) return [];
        var sheetRelsPath = RelationshipPath(sheetPath); var sheetRels = Load(zip, sheetRelsPath);
        var drawingTarget = sheetRels.Descendants(PackageRels + "Relationship").First(x => (string?)x.Attribute("Id") == drawingId).Attribute("Target")!.Value;
        var drawingPath = ResolvePart(sheetPath, drawingTarget); var drawing = Load(zip, drawingPath); var drawingRels = Load(zip, RelationshipPath(drawingPath));
        var imageTargets = drawingRels.Descendants(PackageRels + "Relationship").ToDictionary(x => (string)x.Attribute("Id")!, x => (string)x.Attribute("Target")!);
        var result = new Dictionary<int, byte[]>();
        foreach (var anchor in drawing.Root!.Elements().Where(x => x.Name == Drawing + "oneCellAnchor" || x.Name == Drawing + "twoCellAnchor"))
        {
            var from = anchor.Element(Drawing + "from");
            if (!int.TryParse(from?.Element(Drawing + "row")?.Value, out var row) || !int.TryParse(from?.Element(Drawing + "col")?.Value, out var col) || !allowedColumns.Contains(col)) continue;
            var embed = (string?)anchor.Descendants(A + "blip").FirstOrDefault()?.Attribute(R + "embed");
            if (embed is null || !imageTargets.TryGetValue(embed, out var imageTarget)) continue;
            var entry = zip.GetEntry(ResolvePart(drawingPath, imageTarget)) ?? throw new InvalidDataException("XLSX 图片关系损坏。");
            if (entry.Length > PrivateProductImageStore.MaxImageBytes) throw new InvalidDataException("单张图片超过 5 MiB 限制。");
            using var input = entry.Open(); using var memory = new MemoryStream(); input.CopyTo(memory);
            if (!result.TryAdd(row, memory.ToArray())) throw new InvalidDataException("同一组件行包含多张图片。");
        }
        return result;
    }

    private static XElement OneCellAnchor(WorkbookImage image, string relationId, int id) => new(Drawing + "oneCellAnchor",
        new XElement(Drawing + "from", new XElement(Drawing + "col", image.Column), new XElement(Drawing + "colOff", 0), new XElement(Drawing + "row", image.Row), new XElement(Drawing + "rowOff", 0)),
        new XElement(Drawing + "ext", new XAttribute("cx", 952500), new XAttribute("cy", 952500)),
        new XElement(Drawing + "pic", new XElement(Drawing + "nvPicPr", new XElement(Drawing + "cNvPr", new XAttribute("id", id), new XAttribute("name", $"image{id}")), new XElement(Drawing + "cNvPicPr")), new XElement(Drawing + "blipFill", new XElement(A + "blip", new XAttribute(R + "embed", relationId)), new XElement(A + "stretch", new XElement(A + "fillRect"))), new XElement(Drawing + "spPr", new XElement(A + "xfrm", new XElement(A + "off", new XAttribute("x", 0), new XAttribute("y", 0)), new XElement(A + "ext", new XAttribute("cx", 952500), new XAttribute("cy", 952500))), new XElement(A + "prstGeom", new XAttribute("prst", "rect"), new XElement(A + "avLst")))),
        new XElement(Drawing + "clientData"));

    private static void EnsureDefault(XElement types, XNamespace ct, string extension, string contentType) { if (!types.Elements(ct + "Default").Any(x => (string?)x.Attribute("Extension") == extension)) types.Add(new XElement(ct + "Default", new XAttribute("Extension", extension), new XAttribute("ContentType", contentType))); }
    private static string ImageExtension(byte[] bytes) => bytes.AsSpan().StartsWith(new byte[] { 137,80,78,71,13,10,26,10 }) ? "png" : bytes.Length > 2 && bytes[0] == 0xff && bytes[1] == 0xd8 ? "jpg" : throw new InvalidDataException("仅支持 PNG/JPEG 图片。");
    private static string RelationshipPath(string part) { var slash = part.LastIndexOf('/'); return part[..(slash + 1)] + "_rels/" + part[(slash + 1)..] + ".rels"; }
    private static string ResolvePart(string sourcePart, string target) { var baseUri = new Uri("http://local/" + sourcePart); return Uri.UnescapeDataString(new Uri(baseUri, target).AbsolutePath.TrimStart('/')); }
    private static void Replace(ZipArchive zip, string path, XDocument document) { zip.GetEntry(path)?.Delete(); Entry(zip, path, document); }
}
