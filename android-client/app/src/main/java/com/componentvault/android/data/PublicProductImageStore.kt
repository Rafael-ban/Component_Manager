package com.componentvault.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class PublicProductImageStore private constructor(context: Context) {
    private val cacheDirectory = File(context.applicationContext.cacheDir, "lcsc-thumbnails")
    private val memoryCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val lookup = LcscPublicLookup()
    private val keyLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun load(sku: String, knownImageUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        val imageUrl = LcscPublicCatalog.trustedImageUrl(knownImageUrl)
            ?: lookup.lookup(sku)?.imageUrl
            ?: return@withContext null
        val lock = keyLocks.getOrPut(imageUrl) { Mutex() }
        try {
            lock.withLock { loadTrustedUrl(imageUrl) }
        } finally {
            keyLocks.remove(imageUrl, lock)
        }
    }

    private fun loadTrustedUrl(imageUrl: String): Bitmap? {
        memoryCache.get(imageUrl)?.let { return it }
        cacheDirectory.mkdirs()
        val cachedFile = File(cacheDirectory, imageUrl.sha256())
        decodeThumbnail(cachedFile)?.let {
            memoryCache.put(imageUrl, it)
            return it
        }

        val bytes = download(imageUrl)
        val bitmap = decodeThumbnail(bytes) ?: return null
        runCatching { cachedFile.writeBytes(bytes) }
        pruneDiskCache()
        memoryCache.put(imageUrl, bitmap)
        return bitmap
    }

    private fun download(imageUrl: String): ByteArray {
        val trustedUrl = requireNotNull(LcscPublicCatalog.trustedImageUrl(imageUrl))
        val connection = URL(trustedUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "ComponentVault-Android/0.3")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Image HTTP ${connection.responseCode}")
            }
            val contentType = connection.contentType.orEmpty().lowercase()
            if (!contentType.startsWith("image/")) throw IOException("Not an image response")
            if (connection.contentLengthLong > MAX_DOWNLOAD_BYTES) {
                throw IOException("Product image too large")
            }
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_DOWNLOAD_BYTES) {
                        throw IOException("Product image too large")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeThumbnail(file: File): Bitmap? {
        if (!file.isFile || file.length() !in 1..MAX_DOWNLOAD_BYTES) return null
        return decodeThumbnail(file.readBytes())
    }

    private fun decodeThumbnail(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_BITMAP_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_BITMAP_DIMENSION
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun pruneDiskCache() {
        cacheDirectory.listFiles()
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_DISK_ENTRIES)
            ?.forEach { runCatching { it.delete() } }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 4L * 1024L * 1024L
        private const val MAX_BITMAP_DIMENSION = 256
        private const val MAX_DISK_ENTRIES = 64

        @Volatile
        private var instance: PublicProductImageStore? = null

        fun get(context: Context): PublicProductImageStore = instance ?: synchronized(this) {
            instance ?: PublicProductImageStore(context).also { instance = it }
        }
    }
}
