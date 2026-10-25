package com.zemmy.sigurvita.core.ml

import com.zemmy.sigurvita.core.model.BubbleMask
import com.zemmy.sigurvita.core.model.DetectedBubble
import com.zemmy.sigurvita.core.model.LetterboxParams
import com.zemmy.sigurvita.core.model.NormRect
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.util.PanelInfo
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream

class DetectionStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    private val letterbox = LetterboxParams(1024, 2000, 1000, 0.512f, 0f, 256f)

    @Before
    fun setUp() {
        root = tmp.newFolder("detections")
    }

    private fun bubbles(page: Int) = PageBubbles(
        pageIndex = page,
        pageWidth = 1000,
        pageHeight = 1500,
        letterbox = letterbox,
        bubbles = listOf(
            DetectedBubble(
                id = 1,
                box = NormRect(0.1f, 0.2f, 0.3f, 0.4f),
                score = 0.9f,
                classId = 0,
                mask = BubbleMask(size = 2, probs = byteArrayOf(1, 2, 3, 4))
            )
        )
    )

    private fun panelMap() = PanelMap(
        gridW = 2,
        gridH = 2,
        labels = shortArrayOf(0, 1, 1, 2),
        letterbox = letterbox,
        panels = listOf(
            PanelInfo(1, NormRect(0f, 0f, 0.5f, 1f), 0.25f, 0.5f, 0.5f),
            PanelInfo(2, NormRect(0.5f, 0f, 1f, 1f), 0.75f, 0.5f, 0.5f)
        )
    )

    /** Writes an entry the way the store does, but with a model version of the test's choosing. */
    private fun writeEntry(file: File, cache: PageDetectionsCache) {
        file.parentFile!!.mkdirs()
        val text = Json { encodeDefaults = true }.encodeToString(PageDetectionsCache.serializer(), cache)
        GZIPOutputStream(FileOutputStream(file)).use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    // ---------------------------------------------------------------- bubbles

    @Test
    fun bubblesRoundTrip() {
        DetectionStore.saveBubbles(root, chapterId = 7, pageIndex = 3, confidence = 0.10f, bubbles = bubbles(3))

        assertTrue(File(root, "7/p3_b100.json.gz").exists())
        val loaded = DetectionStore.loadBubbles(root, 7, 3, 0.10f)

        assertNotNull(loaded)
        assertEquals(3, loaded!!.pageIndex)
        assertEquals(1, loaded.bubbles.size)
        assertEquals(0.9f, loaded.bubbles[0].score, 0f)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), loaded.bubbles[0].mask.probs)
    }

    @Test
    fun eachConfidenceValueIsItsOwnEntry() {
        DetectionStore.saveBubbles(root, 7, 3, 0.10f, bubbles(3))

        assertNotNull(DetectionStore.loadBubbles(root, 7, 3, 0.10f))
        assertNull(DetectionStore.loadBubbles(root, 7, 3, 0.25f))
    }

    @Test
    fun missingEntryIsAMiss() {
        assertNull(DetectionStore.loadBubbles(root, 99, 0, 0.10f))
        assertNull(DetectionStore.loadPanelMap(root, 99, 0))
    }

    @Test
    fun bubbleEntryFromAnOtherModelVersionIsDeletedOnRead() {
        val file = File(root, "7/p3_b100.json.gz")
        writeEntry(
            file,
            PageDetectionsCache(
                chapterId = 7,
                modelVersion = "some_old_version",
                confidence = 0.10f,
                pages = listOf(DetectionsCacheHelper.fromPageData(3, bubbles(3), null))
            )
        )

        assertNull(DetectionStore.loadBubbles(root, 7, 3, 0.10f))
        assertFalse(file.exists())
    }

    @Test
    fun handWrittenEntryWithTheCurrentVersionIsAccepted() {
        // Positive control for the version tests: the same file with the current version must load.
        val file = File(root, "7/p3_b100.json.gz")
        writeEntry(
            file,
            PageDetectionsCache(
                chapterId = 7,
                modelVersion = CURRENT_DETECTION_VERSION,
                confidence = 0.10f,
                pages = listOf(DetectionsCacheHelper.fromPageData(3, bubbles(3), null))
            )
        )

        assertNotNull(DetectionStore.loadBubbles(root, 7, 3, 0.10f))
        assertTrue(file.exists())
    }

    @Test
    fun entryHoldingADifferentPageIsAMiss() {
        writeEntry(
            File(root, "7/p3_b100.json.gz"),
            PageDetectionsCache(
                chapterId = 7,
                modelVersion = CURRENT_DETECTION_VERSION,
                confidence = 0.10f,
                pages = listOf(DetectionsCacheHelper.fromPageData(4, bubbles(4), null))
            )
        )

        assertNull(DetectionStore.loadBubbles(root, 7, 3, 0.10f))
    }

    // ----------------------------------------------------------------- panels

    @Test
    fun panelMapRoundTrip() {
        DetectionStore.savePanelMap(root, chapterId = 7, pageIndex = 2, panelMap = panelMap())

        val loaded = DetectionStore.loadPanelMap(root, 7, 2)

        assertNotNull(loaded)
        assertEquals(2, loaded!!.panels.size)
        assertArrayEquals(shortArrayOf(0, 1, 1, 2), loaded.labels)
    }

    @Test
    fun pageWithoutPanelsIsCachedAndComesBackAsAnEmptyMap() {
        val empty = PanelMap(gridW = 2, gridH = 2, labels = ShortArray(0), letterbox = letterbox, panels = emptyList())
        DetectionStore.savePanelMap(root, 7, 5, empty)

        val loaded = DetectionStore.loadPanelMap(root, 7, 5)

        assertNotNull(loaded)
        assertTrue(loaded!!.panels.isEmpty())
    }

    @Test
    fun panelEntryFromAnOtherModelVersionIsDeletedOnRead() {
        val file = File(root, "7/p2_panel.json.gz")
        writeEntry(
            file,
            PageDetectionsCache(
                chapterId = 7,
                modelVersion = "deeppanel_old",
                pages = listOf(DetectionsCacheHelper.fromPageData(2, null, panelMap()))
            )
        )

        assertNull(DetectionStore.loadPanelMap(root, 7, 2))
        assertFalse(file.exists())
    }

    @Test
    fun corruptFileIsDeletedAndTreatedAsAMiss() {
        val file = File(root, "7/p2_panel.json.gz")
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        assertNull(DetectionStore.loadPanelMap(root, 7, 2))
        assertFalse(file.exists())
    }

    // ------------------------------------------------------------ housekeeping

    @Test
    fun deleteChapterRemovesItsFolderOnly() {
        DetectionStore.saveBubbles(root, 7, 0, 0.10f, bubbles(0))
        DetectionStore.saveBubbles(root, 8, 0, 0.10f, bubbles(0))

        DetectionStore.deleteChapter(root, 7)

        assertFalse(File(root, "7").exists())
        assertTrue(File(root, "8").exists())
    }

    private fun chapterFolder(id: Int, bytes: Int, lastModified: Long): File {
        val dir = File(root, id.toString())
        dir.mkdirs()
        File(dir, "p0.bin").writeBytes(ByteArray(bytes))
        dir.setLastModified(lastModified)
        return dir
    }

    @Test
    fun evictionRemovesLeastRecentlyUsedFoldersFirstAndNeverTheKeptOne() {
        chapterFolder(1, 100, 1_000_000L)
        chapterFolder(2, 100, 2_000_000L)
        chapterFolder(3, 100, 500_000L) // the oldest, but it is the chapter being written
        chapterFolder(4, 100, 3_000_000L)

        DetectionStore.evictOverBudget(root, keepChapterId = 3, maxBytes = 250L)

        assertFalse(File(root, "1").exists())
        assertFalse(File(root, "2").exists())
        assertTrue(File(root, "3").exists())
        assertTrue(File(root, "4").exists())
    }

    @Test
    fun evictionDoesNothingWhileTheStoreFitsItsBudget() {
        chapterFolder(1, 100, 1_000_000L)
        chapterFolder(2, 100, 2_000_000L)

        DetectionStore.evictOverBudget(root, keepChapterId = 2, maxBytes = 1_000L)

        assertTrue(File(root, "1").exists())
        assertTrue(File(root, "2").exists())
    }
}
