package com.componentvault.android.data

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class BatchJlcDraftStore(context: Context) {
    private val file = File(context.filesDir, "batch-jlc-draft.json")

    fun load(): BatchJlcDraft = runCatching(::loadOrThrow).getOrNull() ?: BatchJlcDraft()

    fun loadOrThrow(): BatchJlcDraft =
        file.takeIf(File::isFile)?.readText()?.let(BatchJlcDraftCodec::decode) ?: BatchJlcDraft()

    fun save(draft: BatchJlcDraft) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(BatchJlcDraftCodec.encode(draft))
        try {
            Files.move(
                temp.toPath(), file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun clear() {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(File(file.parentFile, "${file.name}.tmp").toPath())
    }
}
