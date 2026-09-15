using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text.RegularExpressions;

namespace ComponentVault.WinUI.Services;

public static partial class PrivateProductImageStore
{
    public const int MaxImageBytes = 5 * 1024 * 1024;
    public const int MaxDimension = 4096;
    public const string MarkerPrefix = "本地图片：";

    public static string Persist(string root, ReadOnlySpan<byte> bytes)
    {
        var extension = Validate(bytes);
        Directory.CreateDirectory(root);
        var key = $"{Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant()}.{extension}";
        var path = Path.Combine(root, key);
        if (!File.Exists(path)) File.WriteAllBytes(path, bytes.ToArray());
        return key;
    }

    public static string? KeyFromDescription(string? description) => description?
        .Split(['\r','\n','；',';'], StringSplitOptions.RemoveEmptyEntries)
        .Select(item => item.Trim()).FirstOrDefault(item => item.StartsWith(MarkerPrefix, StringComparison.Ordinal))?
        .Substring(MarkerPrefix.Length).Trim() is { } key && KeyPattern().IsMatch(key) ? key : null;

    public static string? Resolve(string root, string? description)
    {
        var key = KeyFromDescription(description); if (key is null) return null;
        var canonicalRoot = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        var candidate = Path.GetFullPath(Path.Combine(canonicalRoot, key));
        return candidate.StartsWith(canonicalRoot, StringComparison.OrdinalIgnoreCase) && File.Exists(candidate) ? candidate : null;
    }

    public static string AppendMarker(string? description, string key)
    {
        var lines = (description ?? string.Empty).Split(['\r','\n'], StringSplitOptions.RemoveEmptyEntries)
            .Select(line => line.Trim()).Where(line => !line.StartsWith(MarkerPrefix, StringComparison.Ordinal)).ToList();
        lines.Add(MarkerPrefix + key); return string.Join("\n", lines);
    }

    private static string Validate(ReadOnlySpan<byte> bytes)
    {
        if (bytes.Length is 0 or > MaxImageBytes) throw new InvalidDataException("图片为空或超过 5 MiB 限制。");
        int width, height; string extension;
        if (bytes.Length >= 24 && bytes[..8].SequenceEqual(new byte[]{137,80,78,71,13,10,26,10}))
        { extension="png"; width=BinaryPrimitives.ReadInt32BigEndian(bytes.Slice(16,4)); height=BinaryPrimitives.ReadInt32BigEndian(bytes.Slice(20,4)); }
        else if (bytes.Length>=4 && bytes[0]==0xff && bytes[1]==0xd8)
        { extension="jpg"; (width,height)=JpegSize(bytes); }
        else throw new InvalidDataException("仅支持有效 PNG 或 JPEG 图片。");
        if (width<=0||height<=0||width>MaxDimension||height>MaxDimension) throw new InvalidDataException("图片尺寸必须在 1..4096 像素范围内。");
        return extension;
    }

    private static (int Width,int Height) JpegSize(ReadOnlySpan<byte> bytes)
    {
        var i=2; while(i+9<bytes.Length) { if(bytes[i++]!=0xff) continue; var marker=bytes[i++]; if(marker is 0xd8 or 0xd9)continue; if(i+2>bytes.Length)break;var length=BinaryPrimitives.ReadUInt16BigEndian(bytes.Slice(i,2));if(length<2||i+length>bytes.Length)break;if(marker is >=0xc0 and <=0xc3 or >=0xc5 and <=0xc7 or >=0xc9 and <=0xcb or >=0xcd and <=0xcf)return(BinaryPrimitives.ReadUInt16BigEndian(bytes.Slice(i+5,2)),BinaryPrimitives.ReadUInt16BigEndian(bytes.Slice(i+3,2)));i+=length; }
        throw new InvalidDataException("JPEG 缺少有效尺寸信息。");
    }

    [GeneratedRegex("^[0-9a-f]{64}\\.(png|jpg)$", RegexOptions.CultureInvariant)] private static partial Regex KeyPattern();
}
