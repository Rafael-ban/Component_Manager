package com.componentvault.android.data

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import java.net.URLDecoder

class IssueReportBuilderTest {
    @Test fun optionalDiagnosticsAreExcludedAndUnicodeQueryRoundTrips() {
        val report = IssueReportBuilder.report("扫码 & 问题", "复现：对准二维码", null, null)
        assertFalse(report.contains("## App and device"))
        assertFalse(report.contains("## Redacted diagnostics"))
        val url = assertIs<IssueUrlResult.Full>(IssueReportBuilder.issueUrl("扫码 & 问题", report)).url
        val fields = url.substringAfter('?').split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        kotlin.test.assertEquals("扫码 & 问题", fields["title"])
        kotlin.test.assertEquals(report, fields["body"])
    }
    @Test fun longReportUsesTitleOnlyUrlWithoutSilentTruncation(){
        val body="x".repeat(8_000);val result=assertIs<IssueUrlResult.TooLong>(IssueReportBuilder.issueUrl("scan",body))
        assertTrue(result.titleOnlyUrl.contains("title=scan"));assertTrue(result.titleOnlyUrl.toByteArray().size<IssueReportBuilder.MaxUrlBytes)
    }
    @Test fun shortReportPrefillsTitleAndBody(){assertIs<IssueUrlResult.Full>(IssueReportBuilder.issueUrl("scan","steps"))}
    @Test fun oversizedTitleFallsBackToBlankNewIssue(){val result=assertIs<IssueUrlResult.TooLong>(IssueReportBuilder.issueUrl("题".repeat(8_000),"steps"));assertTrue(result.titleOnlyUrl.endsWith("/issues/new"))}
}
