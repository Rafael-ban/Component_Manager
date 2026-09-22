using System.Net;

namespace ComponentVault.WinUI.Services.Updates;

public sealed class GitHubReleaseClient
{
    private const int MaxResponseBytes = 1024 * 1024;
    private static readonly Uri LatestReleaseApi = new("https://api.github.com/repos/Rafael-ban/Component_Manager/releases/latest");
    private static readonly Uri ReleasesApi = new("https://api.github.com/repos/Rafael-ban/Component_Manager/releases?per_page=30");
    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        var client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false })
        {
            Timeout = System.Threading.Timeout.InfiniteTimeSpan,
        };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("ComponentVault-Windows/0.3");
        client.DefaultRequestHeaders.Accept.ParseAdd("application/vnd.github+json");
        client.DefaultRequestHeaders.Add("X-GitHub-Api-Version", "2022-11-28");
        return client;
    }

    public async Task<UpdateCheckResult> CheckAsync(string localVersion, UpdateChannel channel = UpdateChannel.Stable, CancellationToken cancellationToken = default)
    {
        try
        {
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            deadline.CancelAfter(TimeSpan.FromSeconds(12));
            using var response = await Client.GetAsync(channel == UpdateChannel.Stable ? LatestReleaseApi : ReleasesApi, HttpCompletionOption.ResponseHeadersRead, deadline.Token);
            if (response.StatusCode == HttpStatusCode.NotFound)
                return Failure("项目暂时没有公开 Release。", UpdateComparison.UnknownVersion);
            if (response.StatusCode == HttpStatusCode.Forbidden)
                return Failure("GitHub API 请求受限，请稍后再试或直接打开项目发布页。", UpdateComparison.UnknownVersion);
            if (!response.IsSuccessStatusCode)
                return Failure($"GitHub 返回 HTTP {(int)response.StatusCode}。", UpdateComparison.UnknownVersion);
            if (response.Content.Headers.ContentLength is > MaxResponseBytes)
                return Failure("GitHub Release 响应超过大小限制。", UpdateComparison.UnknownVersion);
            await using var stream = await response.Content.ReadAsStreamAsync(deadline.Token);
            using var memory = new MemoryStream();
            var buffer = new byte[8192];
            while (true)
            {
                var count = await stream.ReadAsync(buffer, deadline.Token);
                if (count == 0) break;
                if (memory.Length + count > MaxResponseBytes) return Failure("GitHub Release 响应超过大小限制。", UpdateComparison.UnknownVersion);
                memory.Write(buffer, 0, count);
            }
            var release = GitHubReleaseParser.ParseLatest(System.Text.Encoding.UTF8.GetString(memory.ToArray()), channel);
            if (release is null) return Failure("没有找到当前更新通道可识别的 Release。", UpdateComparison.UnknownVersion);
            var comparison = GitHubReleaseParser.Compare(localVersion, release.Tag);
            var message = comparison switch
            {
                UpdateComparison.UpdateAvailable => $"发现新版本 {release.Tag}。",
                UpdateComparison.UpToDate => "当前已是最新版本。",
                UpdateComparison.LocalNewer => "本地版本高于当前公开 Release。",
                _ => "无法比较本地版本与 Release 版本。",
            };
            return new(true, message, comparison, release);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { throw; }
        catch (OperationCanceledException) { return Failure("检查更新超时，请检查网络后重试。", UpdateComparison.UnknownVersion); }
        catch (Exception exception) { return Failure($"检查更新失败：{exception.Message}", UpdateComparison.UnknownVersion); }
    }

    private static UpdateCheckResult Failure(string message, UpdateComparison comparison) => new(false, message, comparison, null);
}
