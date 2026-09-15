package com.componentvault.android.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal sealed interface IssueUrlResult {
    data class Full(val url:String):IssueUrlResult
    data class TooLong(val titleOnlyUrl:String):IssueUrlResult
}

internal object IssueReportBuilder {
    const val MaxUrlBytes=7_000
    private const val Base="https://github.com/Rafael-ban/Component_Manager/issues/new"
    fun report(title:String,description:String,diagnostics:String?,device:String?):String=buildString{
        appendLine("# ${title.trim()}");appendLine();appendLine("## Description");appendLine(description.trim())
        device?.let{appendLine();appendLine("## App and device");appendLine(it)}
        diagnostics?.let{appendLine();appendLine("## Redacted diagnostics");appendLine("```");appendLine(it);appendLine("```")}
        appendLine();append("This report is intended for a public GitHub issue. No order numbers, URLs, QR contents, or user files are collected automatically.")
    }
    fun issueUrl(title:String,body:String):IssueUrlResult{
        fun enc(v:String)=URLEncoder.encode(v,StandardCharsets.UTF_8.toString()).replace("+","%20")
        val titleOnly="$Base?title=${enc(title)}"
        val full="$titleOnly&body=${enc(body)}"
        return if(full.toByteArray(StandardCharsets.UTF_8).size<=MaxUrlBytes)IssueUrlResult.Full(full)
        else IssueUrlResult.TooLong(titleOnly.takeIf{it.toByteArray(StandardCharsets.UTF_8).size<=MaxUrlBytes}?:Base)
    }
}
