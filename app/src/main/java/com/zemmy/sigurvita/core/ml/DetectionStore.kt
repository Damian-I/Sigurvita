package com.zemmy.sigurvita.core.ml

import android.content.Context
import android.util.Base64
import android.util.Log
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt

private const val STORE_TAG = "DetectionStore"

/**
 * Persistent per-page detection cache. Works for streamed, CBZ and image-folder chapters alike,
 * because it is keyed only by (chapterId, pageIndex) and lives in app-private storage:
 *
 *   noBackupFilesDir/detections/<chapterId>/p<page>_b<confidence*1000>.json.gz   (bubbles)
 *   noBackupFilesDir/detections/<chapterId>/p<page>_panel.json.gz               (panel map)
 *
 * Bubbles are stored per exact confidence value. They are NOT filtered at read time, because the
 * decoder merges overlapping bubbles, so a result decoded at a low threshold is not the same as a
 * result decoded at a higher threshold and then filtered.
 *
 * Invalidation: bump [CURRENT_DETECTION_VERSION] (bubbles; also bump it when YoloSegDecoder logic
 * changes) or [PANEL_DETECTION_VERSION] (panels; bump when DeepPanelDetector logic/constants change).
 */
object DetectionStore {

    const val PANEL_DETECTION_VERSION = "deeppanel_gutters_v1"

    private const val MAX_TOTAL_BYTES = 300L * 1024L * 1024L
    private const val EVICT_CHECK_EVERY_WRITES = 40

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val writes = AtomicInteger(0)
    private val touchedChapters = ConcurrentHashMap.newKeySet<Int>()
    private val evictLock = Any()

    private fun rootDir(context: Context) = File(context.noBackupFilesDir, "detections")

    private fun chapterDir(context: Context, chapterId: Int) = File(rootDir(context), chapterId.toString())

    private fun confKey(confidence: Float) = (confidence * 1000f).roundToInt()

    private fun bubbleFile(context: Context, chapterId: Int, pageIndex: Int, confidence: Float) =
        File(chapterDir(context, chapterId), "p${pageIndex}_b${confKey(confidence)}.json.gz")

    private fun panelFile(context: Context, chapterId: Int, pageIndex: Int) =
        File(chapterDir(context, chapterId), "p${pageIndex}_panel.json.gz")

    // ---------------------------------------------------------------- bubbles

    fun loadBubbles(context: Context, chapterId: Int, pageIndex: Int, confidence: Float): PageBubbles? {
        val file = bubbleFile(context, chapterId, pageIndex, confidence)
        val cache = read(file) ?: return null
        if (cache.modelVersion != CURRENT_DETECTION_VERSION) {
            file.delete()
            return null
        }
        val data = cache.pages.firstOrNull { it.pageIndex == pageIndex } ?: return null
        return try {
            DetectionsCacheHelper.toPageBubbles(data, pageIndex).also { touch(context, chapterId) }
        } catch (e: Exception) {
            Log.w(STORE_TAG, "Corrupt bubble entry ${file.name}", e)
            file.delete()
            null
        }
    }

    fun saveBubbles(context: Context, chapterId: Int, pageIndex: Int, confidence: Float, bubbles: PageBubbles) {
        val data = DetectionsCacheHelper.fromPageData(pageIndex, bubbles, null)
        val cache = PageDetectionsCache(
            chapterId = chapterId,
            modelVersion = CURRENT_DETECTION_VERSION,
            confidence = confidence,
            pages = listOf(data)
        )
        write(context, chapterId, bubbleFile(context, chapterId, pageIndex, confidence), cache)
    }

    // ----------------------------------------------------------------- panels

    fun loadPanelMap(context: Context, chapterId: Int, pageIndex: Int): PanelMap? {
        val file = panelFile(context, chapterId, pageIndex)
        val cache = read(file) ?: return null
        if (cache.modelVersion != PANEL_DETECTION_VERSION) {
            file.delete()
            return null
        }
        val data = cache.pages.firstOrNull { it.pageIndex == pageIndex } ?: return null
        return try {
            decodePanelMap(data)?.also { touch(context, chapterId) }
        } catch (e: Exception) {
            Log.w(STORE_TAG, "Corrupt panel entry ${file.name}", e)
            file.delete()
            null
        }
    }

    fun savePanelMap(context: Context, chapterId: Int, pageIndex: Int, panelMap: PanelMap) {
        val data = DetectionsCacheHelper.fromPageData(pageIndex, null, panelMap)
        val cache = PageDetectionsCache(
            chapterId = chapterId,
            modelVersion = PANEL_DETECTION_VERSION,
            pages = listOf(data)
        )
        write(context, chapterId, panelFile(context, chapterId, pageIndex), cache)
    }

    /** DetectionsCacheHelper.toPanelMap returns null for pages with zero panels; those are valid results too. */
    private fun decodePanelMap(data: PageDetectionData): PanelMap? {
        DetectionsCacheHelper.toPanelMap(data)?.let { return it }
        if (data.panels.isNotEmpty()) return null

        val lb = LetterboxParams(
            inputSize = data.letterbox.inputSize,
            originalWidth = data.letterbox.originalWidth,
            originalHeight = data.letterbox.originalHeight,
            gain = data.letterbox.gain,
            padX = data.letterbox.padX,
            padY = data.letterbox.padY
        )
        val labels = if (data.panelLabelsBase64.isNotBlank()) {
            val bytes = Base64.decode(data.panelLabelsBase64, Base64.DEFAULT)
            val shorts = ShortArray(bytes.size / 2)
            for (i in shorts.indices) {
                shorts[i] = ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
            }
            shorts
        } else ShortArray(data.panelGridW * data.panelGridH)

        return PanelMap(
            gridW = data.panelGridW,
            gridH = data.panelGridH,
            labels = labels,
            letterbox = lb,
            panels = emptyList()
        )
    }

    // --------------------------------------------------------------- plumbing

    fun deleteChapter(context: Context, chapterId: Int) {
        chapterDir(context, chapterId).deleteRecursively()
        touchedChapters.remove(chapterId)
    }

    private fun read(file: File): PageDetectionsCache? {
        if (!file.exists()) return null
        return try {
            GZIPInputStream(file.inputStream().buffered()).use { gz ->
                json.decodeFromString(PageDetectionsCache.serializer(), gz.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.w(STORE_TAG, "Unreadable cache file ${file.name}, deleting", e)
            file.delete()
            null
        }
    }

    private fun write(context: Context, chapterId: Int, file: File, cache: PageDetectionsCache) {
        try {
            val dir = file.parentFile ?: return
            dir.mkdirs()
            val tmp = File(dir, "${file.name}.${System.nanoTime()}.tmp")
            val text = json.encodeToString(PageDetectionsCache.serializer(), cache)
            GZIPOutputStream(FileOutputStream(tmp)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            maybeEvict(context, chapterId)
        } catch (e: Exception) {
            Log.w(STORE_TAG, "Failed to save ${file.name}", e)
        }
    }

    /** Marks a chapter folder as recently used (once per process) so eviction is least-recently-used. */
    private fun touch(context: Context, chapterId: Int) {
        if (touchedChapters.add(chapterId)) {
            chapterDir(context, chapterId).setLastModified(System.currentTimeMillis())
        }
    }

    private fun maybeEvict(context: Context, keepChapterId: Int) {
        if (writes.incrementAndGet() % EVICT_CHECK_EVERY_WRITES != 0) return
        synchronized(evictLock) {
            val dirs = rootDir(context).listFiles { f -> f.isDirectory } ?: return
            var total = 0L
            for (d in dirs) total += dirSize(d)
            if (total <= MAX_TOTAL_BYTES) return

            val candidates = dirs
                .filter { it.name != keepChapterId.toString() }
                .sortedBy { it.lastModified() }
            for (dir in candidates) {
                if (total <= MAX_TOTAL_BYTES) break
                val size = dirSize(dir)
                if (dir.deleteRecursively()) total -= size
            }
        }
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { size += it.length() }
        return size
    }
}
