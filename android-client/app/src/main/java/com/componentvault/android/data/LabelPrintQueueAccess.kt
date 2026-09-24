package com.componentvault.android.data

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes file operations across screen instances and discards superseded cleanup writes. */
internal class LabelPrintQueueAccess {
    private val generation = AtomicLong()
    private val mutex = Mutex()
    fun claim(): Long = generation.incrementAndGet()
    suspend fun <T> access(owner: Long, operation: () -> T): T = mutex.withLock {
        if (owner != generation.get()) throw CancellationException("Print screen was replaced")
        operation()
    }
}
