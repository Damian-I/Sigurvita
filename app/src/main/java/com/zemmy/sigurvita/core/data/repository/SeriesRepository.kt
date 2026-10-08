package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.MarkReadDto
import com.zemmy.sigurvita.core.data.api.dto.MarkVolumesReadDto
import com.zemmy.sigurvita.core.data.api.dto.SeriesMetadataDto
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.Chapter
import com.zemmy.sigurvita.core.model.MangaFile
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.Volume
import com.zemmy.sigurvita.core.util.extractChapterNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SeriesRepository(
    private val api: KavitaApi,
    private val database: SigurvitaDatabase,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun getSeriesDetail(seriesId: Int): Result<Series> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            var series: Series? = null

            val metadataRes = api.getSeriesMetadata(seriesId)
            val summary = if (metadataRes.isSuccessful) metadataRes.body()?.summary else null

            val resById = api.getSeriesById(seriesId)
            if (resById.isSuccessful && resById.body() != null) {
                val dto = resById.body()!!
                series = Series(
                    id = dto.id,
                    libraryId = dto.libraryId,
                    name = dto.name,
                    localizedName = dto.localizedName,
                    coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                    summary = summary?.takeIf { it.isNotBlank() } ?: dto.summary,
                    pagesRead = dto.pagesRead,
                    totalPages = dto.pages,
                    userRating = dto.userRating
                )
            } else {
                val resDetail = api.getSeriesDetail(seriesId)
                if (resDetail.isSuccessful && resDetail.body() != null) {
                    val dto = resDetail.body()!!
                    series = Series(
                        id = dto.id,
                        libraryId = dto.libraryId,
                        name = dto.name,
                        coverUrl = "${config.serverUrl}/api/image/series-cover?seriesId=${dto.id}&apiKey=${config.apiKey}",
                        summary = summary?.takeIf { it.isNotBlank() } ?: dto.summary,
                        pagesRead = dto.pagesRead,
                        totalPages = dto.pages,
                        isCompleted = dto.isCompleted
                    )
                }
            }

            if (series != null) {
                database.seriesDao().insertSeries(listOf(
                    CachedSeriesEntity(
                        id = series.id,
                        libraryId = series.libraryId,
                        name = series.name,
                        localizedName = series.localizedName,
                        coverUrl = series.coverUrl,
                        summary = series.summary,
                        pagesRead = series.pagesRead,
                        totalPages = series.totalPages,
                        isCompleted = series.isCompleted,
                        userRating = series.userRating
                    )
                ))
                Result.success(series)
            } else {
                val cached = database.seriesDao().getSeriesById(seriesId)
                if (cached != null) {
                    Result.success(
                        Series(
                            id = cached.id,
                            libraryId = cached.libraryId,
                            name = cached.name,
                            localizedName = cached.localizedName,
                            coverUrl = cached.coverUrl,
                            summary = cached.summary,
                            pagesRead = cached.pagesRead,
                            totalPages = cached.totalPages,
                            isCompleted = cached.isCompleted,
                            userRating = cached.userRating
                        )
                    )
                } else {
                    Result.failure(Exception("Series not found"))
                }
            }
        } catch (e: Exception) {
            val cached = database.seriesDao().getSeriesById(seriesId)
            if (cached != null) {
                Result.success(
                    Series(
                        id = cached.id,
                        libraryId = cached.libraryId,
                        name = cached.name,
                        localizedName = cached.localizedName,
                        coverUrl = cached.coverUrl,
                        summary = cached.summary,
                        pagesRead = cached.pagesRead,
                        totalPages = cached.totalPages,
                        isCompleted = cached.isCompleted,
                        userRating = cached.userRating
                    )
                )
            } else {
                val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
                if (completedTasks.isNotEmpty()) {
                    val first = completedTasks.first()
                    Result.success(
                        Series(
                            id = seriesId,
                            libraryId = 1,
                            name = first.seriesName.ifBlank { first.title },
                            coverUrl = first.coverUrl,
                            totalPages = completedTasks.sumOf { it.totalPages },
                            pagesRead = completedTasks.sumOf { it.downloadedPages }
                        )
                    )
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun buildVolumesFromDownloads(seriesId: Int, tasks: List<DownloadTaskEntity>): List<Volume> {
        return tasks.groupBy { it.volumeId }.map { (volId, taskGroup) ->
            val chapters = taskGroup.map { task ->
                Chapter(
                    id = task.chapterId,
                    volumeId = task.volumeId,
                    seriesId = task.seriesId,
                    chapterNumber = task.chapterNumber.ifBlank { "1" },
                    title = task.title,
                    pages = task.totalPages,
                    pagesRead = task.downloadedPages,
                    isRead = false,
                    isDownloaded = true,
                    localPath = task.localPath,
                    coverUrl = task.coverUrl
                )
            }
            Volume(
                id = if (volId > 0) volId else 1,
                seriesId = seriesId,
                name = if (volId > 0) "Volume $volId" else "Downloaded Issues",
                number = volId.toFloat(),
                pagesRead = chapters.sumOf { it.pagesRead },
                totalPages = chapters.sumOf { it.pages },
                chapters = chapters
            )
        }
    }

    suspend fun getSeriesVolumes(seriesId: Int): Result<List<Volume>> = withContext(Dispatchers.IO) {
        try {
            val config = preferencesRepository.serverConfigFlow.first()
            val res = api.getSeriesVolumes(seriesId)
            if (res.isSuccessful && res.body() != null) {
                val volumes = res.body()!!.map { vDto ->
                    val resolvedVolSeriesId = if (vDto.seriesId > 0) vDto.seriesId else seriesId
                    val volNumber = if (vDto.minNumber > 0f) vDto.minNumber else vDto.number.toFloat()
                    Volume(
                        id = vDto.id,
                        seriesId = resolvedVolSeriesId,
                        name = vDto.name ?: "",
                        number = volNumber,
                        pagesRead = vDto.pagesRead,
                        totalPages = vDto.pages,
                        chapters = vDto.chapters.map { cDto ->
                            val isChapterRead = cDto.pages > 0 && cDto.pagesRead >= cDto.pages
                            val titleNameVal = (cDto.titleName ?: "").trim()
                            val titleVal = (cDto.title ?: "").trim()
                            val numVal = (cDto.number ?: if (cDto.minNumber > 0f) cDto.minNumber.toString() else "").trim()

                            val resolvedNumber = extractChapterNumber(numVal, titleVal.ifBlank { titleNameVal })

                            val resolvedTitle = when {
                                titleNameVal.isNotBlank() && titleNameVal != resolvedNumber -> titleNameVal
                                titleVal.isNotBlank() && titleVal != resolvedNumber -> titleVal
                                titleNameVal.isNotBlank() -> titleNameVal
                                else -> titleVal
                            }
                            val mappedFiles = cDto.files?.map { fDto ->
                                MangaFile(
                                    id = fDto.id,
                                    filePath = fDto.filePath,
                                    pages = fDto.pages,
                                    bytes = fDto.bytes,
                                    format = fDto.format,
                                    extension = fDto.extension
                                )
                            } ?: emptyList()

                            Chapter(
                                id = cDto.id,
                                volumeId = cDto.volumeId,
                                seriesId = resolvedVolSeriesId,
                                chapterNumber = resolvedNumber,
                                title = resolvedTitle,
                                summary = cDto.summary,
                                pages = cDto.pages,
                                pagesRead = cDto.pagesRead,
                                isRead = isChapterRead,
                                coverUrl = "${config.serverUrl}/api/image/chapter-cover?chapterId=${cDto.id}&apiKey=${config.apiKey}",
                                files = mappedFiles
                            )
                        }
                    )
                }
                Result.success(volumes)
            } else {
                val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
                if (completedTasks.isNotEmpty()) {
                    Result.success(buildVolumesFromDownloads(seriesId, completedTasks))
                } else {
                    Result.failure(Exception("Failed to fetch volumes"))
                }
            }
        } catch (e: Exception) {
            val completedTasks = database.downloadDao().getCompletedDownloadsForSeries(seriesId)
            if (completedTasks.isNotEmpty()) {
                Result.success(buildVolumesFromDownloads(seriesId, completedTasks))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getSeriesMetadataDetails(seriesId: Int): Result<SeriesMetadataDto> = withContext(Dispatchers.IO) {
        try {
            val res = api.getSeriesMetadata(seriesId)
            if (res.isSuccessful && res.body() != null) {
                Result.success(res.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch series metadata"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChapterRead(chapterId: Int, seriesId: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markMultipleRead(MarkVolumesReadDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark chapter read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChapterUnread(chapterId: Int, seriesId: Int = 0): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markMultipleUnread(MarkVolumesReadDto(seriesId = seriesId, chapterIds = listOf(chapterId)))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark chapter unread"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeriesRead(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markSeriesRead(MarkReadDto(seriesId = seriesId))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark as read"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markSeriesUnread(seriesId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        if (preferencesRepository.readerSettingsFlow.first().isIncognitoMode) {
            return@withContext Result.success(true)
        }
        try {
            val res = api.markSeriesUnread(MarkReadDto(seriesId = seriesId))
            if (res.isSuccessful) Result.success(true)
            else Result.failure(Exception("Failed to mark as unread"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
