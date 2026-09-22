using System.Globalization;

namespace ComponentVault.WinUI.Services.Catalog;

public enum LcscCatalogSource { Domestic, International }
public enum LcscCatalogFailureKind { Blocked, Unreachable, NoMatch, InvalidResponse }

public sealed record LcscCatalogAttempt(LcscCatalogSource Source, LcscCatalogFailureKind Failure);

public sealed record LcscCatalogLookupResult(
    LcscProductMetadata? Metadata,
    LcscCatalogSource PreferredSource,
    LcscCatalogSource? ResolvedSource,
    IReadOnlyList<LcscCatalogAttempt> Attempts)
{
    public bool UsedFallback => ResolvedSource is not null && ResolvedSource != PreferredSource;

    public string UserMessage
    {
        get
        {
            var english = PreferredSource == LcscCatalogSource.International;
            string Source(LcscCatalogSource source) => source == LcscCatalogSource.Domestic
                ? (english ? "LCSC China" : "国内立创") : (english ? "international LCSC" : "国际 LCSC");
            string Failure(LcscCatalogFailureKind failure) => failure switch
            {
                LcscCatalogFailureKind.Blocked => english ? "site verification blocked the request" : "站点验证阻断",
                LcscCatalogFailureKind.Unreachable => english ? "network or site unavailable" : "网络或站点不可达",
                LcscCatalogFailureKind.NoMatch => english ? "no exact match" : "未找到精确匹配",
                _ => english ? "unexpected site response" : "站点响应格式异常",
            };
            if (ResolvedSource is { } resolved && !UsedFallback)
                return english ? $"Exact metadata loaded from {Source(resolved)}." : $"已从{Source(resolved)}取得精确资料。";
            if (ResolvedSource is { } fallback)
                return english
                    ? $"Preferred source {Source(PreferredSource)} failed ({Failure(Attempts[0].Failure)}); using {Source(fallback)}."
                    : $"首选{Source(PreferredSource)}查询失败（{Failure(Attempts[0].Failure)}），已改用{Source(fallback)}。";
            return string.Join(english ? "; " : "；", Attempts.Select(x => $"{Source(x.Source)}{(english ? ": " : "：")}{Failure(x.Failure)}"));
        }
    }
}

public sealed record LcscCatalogSearchResult(
    IReadOnlyList<LcscProductMetadata> Items,
    LcscCatalogSource Source,
    string? Notice);

public static class LcscCatalogRoutePolicy
{
    public static LcscCatalogSource PreferredSource(CultureInfo culture) =>
        culture.TwoLetterISOLanguageName.Equals("zh", StringComparison.OrdinalIgnoreCase)
            ? LcscCatalogSource.Domestic : LcscCatalogSource.International;

    public static IReadOnlyList<LcscCatalogSource> Order(LcscCatalogSource preferred) =>
        preferred == LcscCatalogSource.Domestic
            ? [LcscCatalogSource.Domestic, LcscCatalogSource.International]
            : [LcscCatalogSource.International, LcscCatalogSource.Domestic];

    public static LcscCatalogFailureKind Classify(Exception exception) => exception switch
    {
        LcscDomesticBlockedException => LcscCatalogFailureKind.Blocked,
        InvalidDataException => LcscCatalogFailureKind.InvalidResponse,
        HttpRequestException or TaskCanceledException => LcscCatalogFailureKind.Unreachable,
        _ => LcscCatalogFailureKind.InvalidResponse,
    };
}
