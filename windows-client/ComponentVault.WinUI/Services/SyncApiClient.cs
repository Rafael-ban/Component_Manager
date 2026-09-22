using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services;

public sealed class SyncApiClient
{
    private readonly HttpClient _httpClient;
    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        PropertyNameCaseInsensitive = true,
        WriteIndented = false,
    };

    public SyncApiClient()
        : this(
            new HttpClient
            {
                Timeout = TimeSpan.FromSeconds(15),
            }
        )
    {
    }

    internal SyncApiClient(HttpClient httpClient)
    {
        _httpClient = httpClient ?? throw new ArgumentNullException(nameof(httpClient));
    }

    public async Task<OperationResult> TestConnectionAsync(
        SyncConfiguration settings,
        CancellationToken cancellationToken = default
    )
    {
        if (string.IsNullOrWhiteSpace(settings.ServerBaseUrl))
        {
            return OperationResult.Failure("请先填写服务器地址。");
        }

        if (string.IsNullOrWhiteSpace(settings.ApiToken))
        {
            return OperationResult.Failure("请先填写 API 令牌。");
        }

        try
        {
            var (serverBaseUrl, payload) = await ResolveServerAsync(settings, cancellationToken);
            if (payload.InventoryProtocol != 1)
                return OperationResult.Failure("服务器不支持多库位库存协议 inventory_protocol=1；本地待同步内容已保留。");
            return OperationResult.Success($"连接成功（{serverBaseUrl}）。服务器时间：{payload.ServerTime}");
        }
        catch (Exception exception)
        {
            return OperationResult.Failure($"连接失败：{exception.Message}");
        }
    }

    public async Task<SyncRunResult> RunSyncAsync(
        SyncConfiguration settings,
        SyncPushRequest pushRequest,
        long? cursor,
        CancellationToken cancellationToken = default
    )
    {
        if (string.IsNullOrWhiteSpace(settings.ServerBaseUrl))
        {
            return SyncRunResult.Failure("同步前请先填写服务器地址。");
        }

        if (string.IsNullOrWhiteSpace(settings.ApiToken))
        {
            return SyncRunResult.Failure("同步前请先填写 API 令牌。");
        }

        try
        {
            var (serverBaseUrl, capability) = await ResolveServerAsync(settings, cancellationToken);
            if (capability.InventoryProtocol != 1)
                return SyncRunResult.Failure("服务器不支持多库位库存协议 inventory_protocol=1；同步已停止，本地待同步内容已保留。");
            var pushResponse = await PushAsync(settings, serverBaseUrl, pushRequest, cancellationToken);
            if (pushResponse.Result is not null)
            {
                return pushResponse.Result;
            }

            var pullResponse = await PullAsync(settings, serverBaseUrl, cursor, cancellationToken);
            if (pullResponse.Result is not null)
            {
                return pullResponse.Result;
            }

            return new SyncRunResult
            {
                IsSuccess = true,
                Message = "同步已成功完成。",
                UsedServerBaseUrl = serverBaseUrl,
                AcceptedComponents = pushResponse.Payload!.AcceptedComponents,
                AcceptedStockMovements = pushResponse.Payload.AcceptedStockMovements,
                PullResponse = pullResponse.Payload!,
            };
        }
        catch (Exception exception)
        {
            return SyncRunResult.Failure($"同步失败：{exception.Message}");
        }
    }

    private async Task<(string ServerBaseUrl, SyncTokenStatusResponse Capability)> ResolveServerAsync(
        SyncConfiguration settings,
        CancellationToken cancellationToken
    )
    {
        var hasDistinctFallback = !string.IsNullOrWhiteSpace(settings.FallbackServerBaseUrl)
            && !string.Equals(
                settings.ServerBaseUrl,
                settings.FallbackServerBaseUrl,
                StringComparison.OrdinalIgnoreCase
            );
        if (!hasDistinctFallback)
        {
            return (
                settings.ServerBaseUrl,
                await PingAsync(settings.ServerBaseUrl, settings.ApiToken, cancellationToken)
            );
        }

        try
        {
            using var primaryDeadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            primaryDeadline.CancelAfter(TimeSpan.FromSeconds(3));
            return (
                settings.ServerBaseUrl,
                await PingAsync(settings.ServerBaseUrl, settings.ApiToken, primaryDeadline.Token)
            );
        }
        catch (Exception exception) when (
            !cancellationToken.IsCancellationRequested
            && IsConnectionFailure(exception)
        )
        {
            return (
                settings.FallbackServerBaseUrl,
                await PingAsync(settings.FallbackServerBaseUrl, settings.ApiToken, cancellationToken)
            );
        }
    }

    private static bool IsConnectionFailure(Exception exception) =>
        exception is HttpRequestException or IOException or OperationCanceledException;

    private async Task<SyncTokenStatusResponse> PingAsync(
        string serverBaseUrl,
        string apiToken,
        CancellationToken cancellationToken
    )
    {
        using var request = new HttpRequestMessage(HttpMethod.Post, BuildUri(serverBaseUrl, "/auth/ping"));
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiToken);
        using var response = await _httpClient.SendAsync(request, cancellationToken);
        if (!response.IsSuccessStatusCode) throw new InvalidOperationException(await BuildErrorMessageAsync(response, cancellationToken));
        return await DeserializeAsync<SyncTokenStatusResponse>(response, cancellationToken);
    }

    private async Task<(SyncPushResponse? Payload, SyncRunResult? Result)> PushAsync(
        SyncConfiguration settings,
        string serverBaseUrl,
        SyncPushRequest pushRequest,
        CancellationToken cancellationToken
    )
    {
        var json = JsonSerializer.Serialize(pushRequest, _jsonOptions);
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            BuildUri(serverBaseUrl, "/sync/push")
        );
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", settings.ApiToken);
        request.Content = new StringContent(json, Encoding.UTF8, "application/json");

        using var response = await _httpClient.SendAsync(request, cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            return (
                null,
                SyncRunResult.Failure(await BuildErrorMessageAsync(response, cancellationToken))
            );
        }

        return (await DeserializeAsync<SyncPushResponse>(response, cancellationToken), null);
    }

    private async Task<(SyncPullResponse? Payload, SyncRunResult? Result)> PullAsync(
        SyncConfiguration settings,
        string serverBaseUrl,
        long? cursor,
        CancellationToken cancellationToken
    )
    {
        var path = cursor is null
            ? "/sync/pull"
            : $"/sync/pull?cursor={cursor.Value}";

        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            BuildUri(serverBaseUrl, path)
        );
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", settings.ApiToken);

        using var response = await _httpClient.SendAsync(request, cancellationToken);
        if (!response.IsSuccessStatusCode)
        {
            return (
                null,
                SyncRunResult.Failure(await BuildErrorMessageAsync(response, cancellationToken))
            );
        }

        var payload = await DeserializeAsync<SyncPullResponse>(response, cancellationToken);
        if (payload.InventoryProtocol != 1)
            return (null, SyncRunResult.Failure("服务端拉取响应缺少 inventory_protocol=1；本地待同步内容已保留。"));
        if (payload.SyncCursor is < 0)
        {
            return (null, SyncRunResult.Failure("同步服务端返回了无效的同步游标。"));
        }

        return (payload, null);
    }

    private async Task<T> DeserializeAsync<T>(
        HttpResponseMessage response,
        CancellationToken cancellationToken
    )
    {
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        var payload = await JsonSerializer.DeserializeAsync<T>(
            stream,
            _jsonOptions,
            cancellationToken
        );
        return payload ?? throw new InvalidOperationException("服务端返回了空数据。");
    }

    private async Task<string> BuildErrorMessageAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken
    )
    {
        var body = await response.Content.ReadAsStringAsync(cancellationToken);
        try
        {
            var payload = JsonSerializer.Deserialize<ApiErrorResponse>(body, _jsonOptions);
            if (!string.IsNullOrWhiteSpace(payload?.Detail))
            {
                return $"同步服务端错误（{(int)response.StatusCode}）：{payload.Detail}";
            }
        }
        catch
        {
        }

        return $"同步服务端错误（{(int)response.StatusCode}）：{body}";
    }

    private static Uri BuildUri(string serverBaseUrl, string path) =>
        new($"{serverBaseUrl.TrimEnd('/')}{path}", UriKind.Absolute);
}
