package com.example.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thread-safe wrapper around Android's native PdfRenderer with LRU bitmap caching.
 * Ensures only one page is opened at a time to strictly prevent IllegalStateException.
 */
class PdfRendererCore(val file: File) {

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val mutex = Mutex()

    // 40MB memory cache for full pages
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(40 * 1024 * 1024)
    private val pageCache = object : LruCache<String, Bitmap>(maxCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    // Cache for mini thumbnail strip
    private val thumbnailCache = object : LruCache<Int, Bitmap>(10 * 1024 * 1024) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    var pageCount: Int = 0
        private set

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!file.exists() || file.length() == 0L) return@withLock false
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd!!)
                pageCount = renderer?.pageCount ?: 0
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getPageDimensions(pageIndex: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val rend = renderer ?: return@withLock Pair(595, 842)
                if (pageIndex < 0 || pageIndex >= rend.pageCount) return@withLock Pair(595, 842)
                val page = rend.openPage(pageIndex)
                val dims = Pair(page.width, page.height)
                page.close()
                dims
            } catch (e: Exception) {
                Pair(595, 842)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1200, rotation: Int = 0): Bitmap? =
        withContext(Dispatchers.IO) {
            val cacheKey = "$pageIndex-$targetWidth-$rotation"
            pageCache.get(cacheKey)?.let { return@withContext it }

            mutex.withLock {
                // Double check cache inside lock
                pageCache.get(cacheKey)?.let { return@withLock it }

                try {
                    val rend = renderer ?: return@withLock null
                    if (pageIndex < 0 || pageIndex >= rend.pageCount) return@withLock null

                    val page = rend.openPage(pageIndex)
                    val originalWidth = page.width
                    val originalHeight = page.height

                    val scale = targetWidth.toFloat() / originalWidth.toFloat()
                    val targetHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val finalBitmap = if (rotation % 360 != 0) {
                        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                        val rotated = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                        )
                        bitmap.recycle()
                        rotated
                    } else {
                        bitmap
                    }

                    pageCache.put(cacheKey, finalBitmap)
                    finalBitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

    suspend fun renderThumbnail(pageIndex: Int, targetWidth: Int = 160): Bitmap? =
        withContext(Dispatchers.IO) {
            thumbnailCache.get(pageIndex)?.let { return@withContext it }

            mutex.withLock {
                thumbnailCache.get(pageIndex)?.let { return@withLock it }
                try {
                    val rend = renderer ?: return@withLock null
                    if (pageIndex < 0 || pageIndex >= rend.pageCount) return@withLock null

                    val page = rend.openPage(pageIndex)
                    val scale = targetWidth.toFloat() / page.width.toFloat()
                    val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    thumbnailCache.put(pageIndex, bitmap)
                    bitmap
                } catch (e: Exception) {
                    null
                }
            }
        }

    fun close() {
        try {
            renderer?.close()
            pfd?.close()
            pageCache.evictAll()
            thumbnailCache.evictAll()
        } catch (e: Exception) {
            // Ignored
        } finally {
            renderer = null
            pfd = null
        }
    }
}
