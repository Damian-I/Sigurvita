package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MarkedPageRepository(private val database: SigurvitaDatabase) {

    val markedPagesFlow: Flow<List<MarkedPageEntity>> =
        database.markedPageDao().getAllMarkedPages()

    fun getMarkedPageIndicesForChapter(chapterId: Int): Flow<List<Int>> =
        database.markedPageDao().getMarkedPageIndicesForChapter(chapterId)

    suspend fun toggleMarkPage(
        chapterId: Int,
        seriesId: Int,
        volumeId: Int,
        pageIndex: Int,
        totalPages: Int,
        seriesName: String,
        chapterTitle: String,
        imageUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val dao = database.markedPageDao()
        val existing = dao.getMarkedPage(chapterId, pageIndex)
        if (existing != null) {
            dao.deleteMarkedPage(chapterId, pageIndex)
            false
        } else {
            val entity = MarkedPageEntity(
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
                pageIndex = pageIndex,
                totalPages = totalPages,
                seriesName = seriesName,
                chapterTitle = chapterTitle,
                imageUrl = imageUrl
            )
            dao.insertMarkedPage(entity)
            true
        }
    }

    suspend fun deleteMarkedPageById(id: Int) = withContext(Dispatchers.IO) {
        database.markedPageDao().deleteMarkedPageById(id)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.markedPageDao().clearAllMarkedPages()
    }
}
