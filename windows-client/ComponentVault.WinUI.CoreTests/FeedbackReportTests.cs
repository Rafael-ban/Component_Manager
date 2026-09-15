using ComponentVault.WinUI.Services;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class FeedbackReportTests
{
    [Fact]
    public void DefaultReport_DoesNotContainDiagnosticsOrDeviceData()
    {
        AppDiagnostics.Clear();AppDiagnostics.Record(DiagnosticEvent.Network,DiagnosticOutcome.Dns,exceptionType:typeof(HttpRequestException));
        var report=FeedbackReportBuilder.Build(Input(false,AppDiagnostics.Snapshot()));
        Assert.DoesNotContain("诊断信息",report.FullText);Assert.DoesNotContain("HttpRequestException",report.FullText);Assert.DoesNotContain("secret-device",report.FullText);
        Assert.Contains("title=%5Bspecial%5D%20%26%20space",report.GitHubUri.AbsoluteUri,StringComparison.OrdinalIgnoreCase);
        Assert.False(report.RequiresCopy);Assert.True(System.Text.Encoding.UTF8.GetByteCount(report.GitHubUri.AbsoluteUri)<=7000);
    }

    [Fact]
    public void OversizedReport_RequiresCopyAndOpensTitleOnly()
    {
        var input=Input(true,new string('诊',4000));var report=FeedbackReportBuilder.Build(input);
        Assert.True(report.RequiresCopy);Assert.Contains(new string('诊',100),report.FullText);Assert.DoesNotContain("body=",report.GitHubUri.Query,StringComparison.OrdinalIgnoreCase);
        Assert.True(System.Text.Encoding.UTF8.GetByteCount(report.GitHubUri.AbsoluteUri)<=7000);
    }

    [Fact]
    public void EncodedUrlBoundary_NeverExceedsSevenThousandBytes()
    {
        FeedbackReport Build(int length)=>FeedbackReportBuilder.Build(Input(false,"") with {Actual=new string('a',length)});
        var low=0;while(!Build(low+1).RequiresCopy)low++;
        var fitting=Build(low);var overflow=Build(low+1);
        Assert.False(fitting.RequiresCopy);Assert.True(overflow.RequiresCopy);
        Assert.True(System.Text.Encoding.UTF8.GetByteCount(fitting.GitHubUri.AbsoluteUri)<=FeedbackReportBuilder.MaxEncodedUrlBytes);
        Assert.True(System.Text.Encoding.UTF8.GetByteCount(overflow.GitHubUri.AbsoluteUri)<=FeedbackReportBuilder.MaxEncodedUrlBytes);
    }

    [Fact]
    public void Diagnostics_AreBoundedAndCannotAcceptSensitivePayloadStrings()
    {
        AppDiagnostics.Clear();for(var i=0;i<250;i++)AppDiagnostics.Record(DiagnosticEvent.CatalogDomestic,DiagnosticOutcome.Http,500,typeof(InvalidOperationException));
        var snapshot=AppDiagnostics.Snapshot();Assert.True(snapshot.Split('\n').Length<=100);Assert.True(System.Text.Encoding.UTF8.GetByteCount(snapshot)<=16*1024);
        Assert.DoesNotContain("token",snapshot,StringComparison.OrdinalIgnoreCase);Assert.DoesNotContain("http://",snapshot,StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void OversizedTitle_OpensBlankIssueWithoutSilentTruncation()
    {
        var input=Input(false,"") with { Title=new string('题',4000) };var report=FeedbackReportBuilder.Build(input);
        Assert.True(report.RequiresCopy);Assert.Equal("",report.GitHubUri.Query);Assert.Contains(new string('题',100),report.FullText);
    }

    [Fact]
    public void HundredThousandChineseCharacters_NeverConstructAnOversizedUri()
    {
        var huge=new string('故',100_000);
        var body=FeedbackReportBuilder.Build(Input(false,"") with {Actual=huge});
        var title=FeedbackReportBuilder.Build(Input(false,"") with {Title=huge});
        Assert.True(body.RequiresCopy);Assert.Contains(huge,body.FullText);Assert.True(System.Text.Encoding.UTF8.GetByteCount(body.GitHubUri.AbsoluteUri)<=7000);
        Assert.True(title.RequiresCopy);Assert.Equal("",title.GitHubUri.Query);Assert.Contains(huge,title.FullText);
    }

    private static FeedbackInput Input(bool diagnostics,string text)=>new("[special] & space","step","expected","actual",diagnostics,text,"1.0","Windows","secret-device");
}
