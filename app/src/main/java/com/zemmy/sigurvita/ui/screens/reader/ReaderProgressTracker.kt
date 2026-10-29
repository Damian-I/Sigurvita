package com.zemmy.sigurvita.ui.screens.reader

import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.ReaderRepository
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Saves reading progress to the server (debounced while paging, immediately on the last page and on exit)
 * and applies the auto-delete policy for downloaded chapters once a chapter is finished.
 *
 * Moved out of [ReaderViewModel] without behaviour changes. [readerRepository] already ignores progress writes
 * in Incognito mode.
 */
class ReaderProgressTracker(
    private val scope: CoroutineScope,
    private val state: StateFlow<ReaderUiState>,
    private val readerRepository: ReaderRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    /** Library of the chapter being read; the ViewModel sets it once it has resolved the series. */
    @Volatile
    var libraryId: Int = 0

    private val progressSaveFlow = MutableSharedFlow<ProgressEvent>(extraBufferCapacity = 1)

    private data class ProgressEvent(
        val seriesId: Int,
        val volumeId: Int,
        val chapterId: Int,
        val pageNum: Int,
        val totalPages: Int
    )

    init {
        startDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun startDebounce() {
        scope.launch {
            progressSaveFlow
                .debounce(3.seconds)
                .collect { event ->
                    if (state.value.sessionIncognito) return@collect
                    readerRepository.saveProgress(
                        seriesId = event.seriesId,
                        volumeId = event.volumeId,
                        chapterId = event.chapterId,
                        pageNum = event.pageNum,
                        totalPages = event.totalPages,
                        resolvedLibraryId = libraryId
                    )
                }
        }
    }

    /** Saves the current position now (called on chapter exit). */
    suspend fun flushSync() {
        val current = state.value
        if (current.sessionIncognito) return
        if (current.pages.isNotEmpty() && current.chapterId > 0) {
            readerRepository.saveProgress(
                seriesId = current.seriesId,
                volumeId = current.volumeId,
                chapterId = current.chapterId,
                pageNum = current.currentPageIndex + 1,
                totalPages = current.pages.size,
                resolvedLibraryId = libraryId
            )
        }
    }

    fun flush() {
        scope.launch {
            flushSync()
        }
    }

    /** Call after every page change: schedules the debounced save and handles the last-page rules. */
    fun onPageShown(index: Int) {
        val current = state.value
        val totalPages = current.pages.size
        progressSaveFlow.tryEmit(
            ProgressEvent(
                seriesId = current.seriesId,
                volumeId = current.volumeId,
                chapterId = current.chapterId,
                pageNum = index + 1,
                totalPages = totalPages
            )
        )

        // Auto-delete logic still runs immediately on last page
        scope.launch {
            if (state.value.sessionIncognito) return@launch
            if (index >= totalPages - 1 && totalPages > 0) {
                // Flush progress immediately for the last page
                readerRepository.saveProgress(
                    seriesId = state.value.seriesId,
                    volumeId = state.value.volumeId,
                    chapterId = state.value.chapterId,
                    pageNum = index + 1,
                    totalPages = totalPages,
                    resolvedLibraryId = libraryId
                )

                val settings = state.value.settings
                if (settings.isIncognitoMode) return@launch
                val currentChapterId = state.value.chapterId
                when (settings.autoDeleteOption) {
                    AutoDeleteOption.IMMEDIATELY -> {
                        downloadRepository.deleteLocalChapter(currentChapterId)
                    }
                    AutoDeleteOption.AFTER_1_ISSUE,
                    AutoDeleteOption.AFTER_2_ISSUES,
                    AutoDeleteOption.AFTER_5_ISSUES -> {
                        preferencesRepository.addCompletedChapter(currentChapterId)
                        val history = preferencesRepository.completedChapterHistoryFlow.first()
                        val keepOffset = when (settings.autoDeleteOption) {
                            AutoDeleteOption.AFTER_1_ISSUE -> 1
                            AutoDeleteOption.AFTER_2_ISSUES -> 2
                            AutoDeleteOption.AFTER_5_ISSUES -> 5
                            else -> 1
                        }
                        val cutoffIndex = history.size - 1 - keepOffset
                        if (cutoffIndex >= 0) {
                            val toDelete = history.subList(0, cutoffIndex + 1)
                            toDelete.forEach { chapterIdToDelete ->
                                downloadRepository.deleteLocalChapter(chapterIdToDelete)
                            }
                            preferencesRepository.removeCompletedChapters(toDelete)
                        }
                    }
                    AutoDeleteOption.NEVER -> {}
                }
            }
        }
    }
}
