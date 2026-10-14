package com.zemmy.sigurvita.ui.screens.markedpages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity
import com.zemmy.sigurvita.core.ml.PageBitmapLoader
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.MagnifiedBubble
import com.zemmy.sigurvita.core.model.PageBubbles
import com.zemmy.sigurvita.core.model.ReaderPage
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.util.PanelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ReviewTagFilter(val displayName: String) {
    ALL("All Marks"),
    DEBUG_ONLY("Debug"),
    REMOVE_ONLY("To Remove")
}

enum class MarkedPageGrouping(val displayName: String) {
    NONE("Flat List"),
    BY_SERIES("By Series"),
    BY_TAG("By Review Tag")
}

enum class MarkedPageSorting(val displayName: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    SERIES_NAME("Series Name"),
    PAGE_NUMBER("Page Number")
}

data class MarkedPageGroup(
    val title: String,
    val pages: List<MarkedPageEntity>
)

data class MarkedPagesUiState(
    val markedPages: List<MarkedPageEntity> = emptyList(),
    val searchQuery: String = "",
    val selectedTagFilter: ReviewTagFilter = ReviewTagFilter.ALL,
    val selectedGrouping: MarkedPageGrouping = MarkedPageGrouping.NONE,
    val selectedSorting: MarkedPageSorting = MarkedPageSorting.NEWEST,
    val previewingPage: MarkedPageEntity? = null,
    val readerPages: List<ReaderPage> = emptyList(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    val isTestingMl: Boolean = false,
    val testedPageBubbles: PageBubbles? = null,
    val testedPanelMap: PanelMap? = null,
    val magnifiedBubble: MagnifiedBubble? = null,
    val showBubblesOverlay: Boolean = true,
    val showPanelsOverlay: Boolean = true
) {
    val filteredPages: List<MarkedPageEntity>
        get() {
            var result = markedPages

            // 1. Tag filter
            result = when (selectedTagFilter) {
                ReviewTagFilter.ALL -> result
                ReviewTagFilter.DEBUG_ONLY -> result.filter { it.reviewTag == "DEBUG" }
                ReviewTagFilter.REMOVE_ONLY -> result.filter { it.reviewTag == "REMOVE" }
            }

            // 2. Search query
            if (searchQuery.isNotBlank()) {
                result = result.filter {
                    it.chapterTitle.contains(searchQuery, ignoreCase = true) ||
                    it.seriesName.contains(searchQuery, ignoreCase = true)
                }
            }

            // 3. Sorting
            result = when (selectedSorting) {
                MarkedPageSorting.NEWEST -> result.sortedByDescending { it.createdAt }
                MarkedPageSorting.OLDEST -> result.sortedBy { it.createdAt }
                MarkedPageSorting.SERIES_NAME -> result.sortedBy { it.seriesName.ifBlank { it.chapterTitle } }
                MarkedPageSorting.PAGE_NUMBER -> result.sortedBy { it.pageIndex }
            }

            return result
        }

    val groupedPages: List<MarkedPageGroup>
        get() {
            val pages = filteredPages
            return when (selectedGrouping) {
                MarkedPageGrouping.NONE -> listOf(MarkedPageGroup("All Marked Pages", pages))
                MarkedPageGrouping.BY_SERIES -> {
                    pages.groupBy { it.seriesName.ifBlank { it.chapterTitle } }
                        .map { (series, items) -> MarkedPageGroup(series, items) }
                }
                MarkedPageGrouping.BY_TAG -> {
                    val debugs = pages.filter { it.reviewTag == "DEBUG" }
                    val removes = pages.filter { it.reviewTag == "REMOVE" }
                    buildList {
                        if (debugs.isNotEmpty()) add(MarkedPageGroup("Debug Items (${debugs.size})", debugs))
                        if (removes.isNotEmpty()) add(MarkedPageGroup("To Remove Items (${removes.size})", removes))
                    }
                }
            }
        }
}

class MarkedPagesViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val repository = app.markedPageRepository
    private val preferencesRepository = app.userPreferencesRepository

    private val _uiState = MutableStateFlow(MarkedPagesUiState())
    val uiState: StateFlow<MarkedPagesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.markedPagesFlow.collect { pages ->
                val config = preferencesRepository.serverConfigFlow.first()
                val serverUrl = config.serverUrl
                val apiKey = config.apiKey
                val resolvedPages = pages.map { page ->
                    val cleanPath = page.imageUrl.removePrefix("file://")
                    val file = File(cleanPath)
                    if ((page.imageUrl.startsWith("/") || page.imageUrl.startsWith("file://")) && !file.exists()) {
                        if (serverUrl.isNotBlank()) {
                            val remoteUrl = "$serverUrl/api/Reader/image?chapterId=${page.chapterId}&page=${page.pageIndex}&apiKey=$apiKey"
                            page.copy(imageUrl = remoteUrl)
                        } else {
                            page
                        }
                    } else {
                        page
                    }
                }
                _uiState.value = _uiState.value.copy(markedPages = resolvedPages)
            }
        }
        viewModelScope.launch {
            preferencesRepository.readerSettingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(readerSettings = settings)
                val currentPreview = _uiState.value.previewingPage
                if (currentPreview != null) {
                    testMlOnPage(currentPreview)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun setTagFilter(filter: ReviewTagFilter) {
        _uiState.value = _uiState.value.copy(selectedTagFilter = filter)
    }

    fun setGrouping(grouping: MarkedPageGrouping) {
        _uiState.value = _uiState.value.copy(selectedGrouping = grouping)
    }

    fun setSorting(sorting: MarkedPageSorting) {
        _uiState.value = _uiState.value.copy(selectedSorting = sorting)
    }

    fun toggleReviewTag(id: Int, currentTag: String) {
        viewModelScope.launch {
            repository.toggleReviewTag(id, currentTag)
        }
    }

    fun deleteMarkedPage(id: Int) {
        viewModelScope.launch {
            repository.deleteMarkedPageById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun setPreviewPage(page: MarkedPageEntity?) {
        val pageList = if (page != null) {
            listOf(
                ReaderPage(
                    index = 0,
                    pageNumber = page.pageIndex + 1,
                    imageUrl = page.imageUrl
                )
            )
        } else emptyList()

        _uiState.value = _uiState.value.copy(
            previewingPage = page,
            readerPages = pageList,
            testedPageBubbles = null,
            testedPanelMap = null,
            magnifiedBubble = null
        )
        if (page != null) {
            testMlOnPage(page)
        }
    }

    fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleDetectionEnabled(enabled)
        }
    }

    fun updateSpeechBubbleBoxesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleBoxesEnabled(enabled)
        }
    }

    fun updateSpeechBubbleConfidence(confidence: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleConfidence(confidence)
        }
    }

    fun updateFitMode(mode: FitMode) {
        viewModelScope.launch {
            preferencesRepository.updateFitMode(mode)
        }
    }

    fun updatePanelDebugOverlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePanelDebugOverlay(enabled)
        }
    }

    fun updatePanelAwareOrder(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePanelAwareOrder(enabled)
        }
    }

    fun onBubbleLongPress(pageIndex: Int, bubbleId: Int, @Suppress("UNUSED_PARAMETER") centerPx: Offset, @Suppress("UNUSED_PARAMETER") bubbleSizePx: Size) {
        val page = _uiState.value.previewingPage ?: return
        val pageBubbles = _uiState.value.testedPageBubbles ?: return
        val bubble = pageBubbles.bubbles.find { it.id == bubbleId } ?: return
        val dummyPage = ReaderPage(
            index = 0,
            pageNumber = page.pageIndex + 1,
            imageUrl = page.imageUrl
        )

        val metrics = app.resources.displayMetrics
        val boxW = metrics.widthPixels.toFloat()
        val boxH = metrics.heightPixels.toFloat()
        val pageW = if (pageBubbles.pageWidth > 0) pageBubbles.pageWidth.toFloat() else boxW
        val pageH = if (pageBubbles.pageHeight > 0) pageBubbles.pageHeight.toFloat() else boxH
        val s = minOf(boxW / pageW, boxH / pageH)
        val dispW = pageW * s
        val dispH = pageH * s
        val originX = (boxW - dispW) / 2f
        val originY = (boxH - dispH) / 2f

        viewModelScope.launch(Dispatchers.IO) {
            val cropResult = app.bubbleRepository.cropBubble(
                context = app,
                page = dummyPage,
                bubble = bubble,
                letterbox = pageBubbles.letterbox,
                speechBubbleBoxesEnabled = _uiState.value.readerSettings.speechBubbleBoxesEnabled,
                chapterId = page.chapterId
            )
            if (cropResult != null) {
                val anchorX = originX + cropResult.anchorNormX * dispW
                val anchorY = originY + cropResult.anchorNormY * dispH
                val cropWOnScreen = (cropResult.cropRightNorm - cropResult.cropLeftNorm) * dispW
                val cropHOnScreen = (cropResult.cropBottomNorm - cropResult.cropTopNorm) * dispH

                _uiState.value = _uiState.value.copy(
                    magnifiedBubble = MagnifiedBubble(
                        pageIndex = pageIndex,
                        bubbleId = bubbleId,
                        bitmap = cropResult.bitmap,
                        anchorScreenPx = Offset(anchorX, anchorY),
                        cropSizeOnScreen = Size(cropWOnScreen, cropHOnScreen),
                        tipRelOffset = Offset(cropResult.relTipX, cropResult.relTipY),
                        isLiteralBox = cropResult.isLiteralBox
                    )
                )
            }
        }
    }

    fun dismissBubble() {
        _uiState.value = _uiState.value.copy(magnifiedBubble = null)
    }

    fun testMlOnPage(page: MarkedPageEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isTestingMl = true)
            try {
                val dummyPage = ReaderPage(
                    index = 0,
                    pageNumber = page.pageIndex + 1,
                    imageUrl = page.imageUrl
                )
                val bitmap = PageBitmapLoader.decodeForInference(app, dummyPage, targetSize = 640)
                if (bitmap != null) {
                    val settings = _uiState.value.readerSettings
                    val bubbles = if (settings.speechBubbleDetectionEnabled) {
                        app.bubbleRepository.getPageBubbles(page.chapterId, dummyPage, confidence = settings.speechBubbleConfidence)
                    } else null

                    val panelMap = app.panelRepository.getPanelMap(page.chapterId, dummyPage, bitmap)
                    _uiState.value = _uiState.value.copy(
                        isTestingMl = false,
                        testedPageBubbles = bubbles,
                        testedPanelMap = panelMap
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isTestingMl = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isTestingMl = false)
            }
        }
    }
}
