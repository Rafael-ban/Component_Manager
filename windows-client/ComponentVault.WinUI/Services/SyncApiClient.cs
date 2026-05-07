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
    {
        _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(15),
        };
    }

    public async Task<OperationResult> TestConnectionAsync(
        SyncConfiguration settings,
        CancellationToken cancellationToken = default
    )
    {
        if (string.IsNullOrWhiteSpace(settings.ServerBaseUrl))
        {
            return OperationResult.Failure("Enter a server URL first.");
        }

        if (string.IsNullOrWhiteSpace(settings.ApiToken))
        {
            return OperationResult.Failure("Enter an API token first.");
        }

        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            BuildUri(settings.ServerBaseUrl, "/auth/ping")
        );
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", settings.ApiToken);

        try
        {
            using var response = await _httpClient.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return OperationResult.Failure(await BuildErrorMessageAsync(response, cancellationToken));
            }

            var payload = await DeserializeAsync<SyncTokenStatusResponse>(response, cancellationToken);
            return OperationResult.Success($"Connection ok. Server time: {payload.ServerTime}");
        }
        catch (Exception exception)
        {
            return OperationResult.Failure($"Connection failed: {exception.Message}");
        }
    }

    public async Task<SyncRunResult> RunSyncAsync(
        SyncConfiguration settings,
        SyncPushRequest pushRequest,
        string? since,
        CancellationToken cancellationToken = default
    )
    {
        if (string.IsNullOrWhiteSpace(settings.ServerBaseUrl))
        {
            return SyncRunResult.Failure("Enter a server URL before syncing.");
        }

        if (string.IsNullOrWhiteSpace(settings.ApiToken))
        {
            return SyncRunResult.Failure("Enter an API token before syncing.");
        }

        try
        {
            var pushResponse = await PushAsync(settings, pushRequest, cancellationToken);
            if (pushResponse.Result is not null)
            {
                return pushResponse.Result;
            }

            var pullResponse = await PullAsync(settings, since, cancellationToken);
            if (pullResponse.Result is not null)
            {
                return pullResponse.Result;
            }

            return new SyncRunResult
            {
                IsSuccess = true,
                Message = "Sync completed successfully.",
                AcceptedComponents = pushResponse.Payload!.AcceptedComponents,
                AcceptedStockMovements = pushResponse.Payload.AcceptedStockMovements,
                PullResponse = pullResponse.Payload!,
            };
        }
        catch (Exception exception)
        {
            return SyncRunResult.Failure($"Sync failed: {exception.Message}");
        }
    }

    private async Task<(SyncPushResponse? Payload, SyncRunResult? Result)> PushAsync(
        SyncConfiguration settings,
        SyncPushRequest pushRequest,
        CancellationToken cancellationToken
    )
    {
        var json = JsonSerializer.Serialize(pushRequest, _jsonOptions);
        using var request = new HttpRequestMessage(
            HttpMethod.Post,
            BuildUri(settings.ServerBaseUrl, "/sync/push")
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
        string? since,
        CancellationToken cancellationToken
    )
    {
        var path = string.IsNullOrWhiteSpace(since)
            ? "/sync/pull"
            : $"/sync/pull?since={Uri.EscapeDataString(since)}";

        using var request = new HttpRequestMessage(
            HttpMethod.Get,
            BuildUri(settings.ServerBaseUrl, path)
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

        return (await DeserializeAsync<SyncPullResponse>(response, cancellationToken), null);
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
        return payload ?? throw new InvalidOperationException("Server returned an empty payload.");
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
                return $"Sync server error ({(int)response.StatusCode}): {payload.Detail}";
            }
        }
        catch
        {
        }

        return $"Sync server error ({(int)response.StatusCode}): {body}";
    }

    private static Uri BuildUri(string serverBaseUrl, string path) =>
        new($"{serverBaseUrl.TrimEnd('/')}{path}", UriKind.Absolute);
}
