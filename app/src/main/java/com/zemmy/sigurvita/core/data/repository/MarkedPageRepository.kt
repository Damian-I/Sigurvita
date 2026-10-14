package com.zemmy.sigurvita.core.data.repository

import android.content.Context
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MarkedPageRepository(
    private val database: SigurvitaDatabase,
    private val context: Context
) {

    val markedPagesFlow: Flow<List<MarkedPageEntity>> =
        database.markedPageDao().getAllMarkedPages()

    fun getMarkedPageIndicesForChapter(chapterId: Int): Flow<List<Int>> =
        database.markedPageDao().getMarkedPageIndicesForChapter(chapterId)

    private fun deleteMarkedImageFile(imageUrl: String) {
        try {
            if (imageUrl.contains("marked_images")) {
                val file = File(imageUrl.removePrefix("file://"))
                if (file.exists() && file.name.startsWith("mark_")) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

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
            deleteMarkedImageFile(existing.imageUrl)
            dao.deleteMarkedPage(chapterId, pageIndex)
            false
        } else {
            var persistentImageUrl = imageUrl
            val cleanPath = imageUrl.removePrefix("file://")
            val sourceFile = File(cleanPath)
            if ((imageUrl.startsWith("/") || imageUrl.startsWith("file://")) && sourceFile.exists()) {
                try {
                    val permanentDir = File(context.filesDir, "marked_images").apply { mkdirs() }
                    val targetFile = File(permanentDir, "mark_${System.currentTimeMillis()}_${chapterId}_$pageIndex.jpg")
                    sourceFile.copyTo(targetFile, overwrite = true)
                    if (targetFile.exists() && targetFile.length() > 0L) {
                        persistentImageUrl = targetFile.absolutePath
                    }
                } catch (_: Exception) {}
            }

            val entity = MarkedPageEntity(
                chapterId = chapterId,
                seriesId = seriesId,
                volumeId = volumeId,
                pageIndex = pageIndex,
                totalPages = totalPages,
                seriesName = seriesName,
                chapterTitle = chapterTitle,
                imageUrl = persistentImageUrl
            )
            dao.insertMarkedPage(entity)
            true
        }
    }

    suspend fun deleteMarkedPageById(id: Int) = withContext(Dispatchers.IO) {
        val dao = database.markedPageDao()
        val entity = dao.getMarkedPageById(id)
        if (entity != null) {
            deleteMarkedImageFile(entity.imageUrl)
        }
        dao.deleteMarkedPageById(id)
    }

    suspend fun updateReviewTag(id: Int, reviewTag: String) = withContext(Dispatchers.IO) {
        database.markedPageDao().updateReviewTag(id, reviewTag)
    }

    suspend fun toggleReviewTag(id: Int, currentTag: String) = withContext(Dispatchers.IO) {
        val newTag = if (currentTag == "REMOVE") "DEBUG" else "REMOVE"
        database.markedPageDao().updateReviewTag(id, newTag)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            val permanentDir = File(context.filesDir, "marked_images")
            if (permanentDir.exists()) {
                permanentDir.deleteRecursively()
            }
        } catch (_: Exception) {}
        database.markedPageDao().clearAllMarkedPages()
    }
}
