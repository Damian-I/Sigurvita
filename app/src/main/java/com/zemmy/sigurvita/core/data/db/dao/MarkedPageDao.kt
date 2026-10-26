package com.zemmy.sigurvita.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkedPageDao {
    @Query("SELECT * FROM marked_pages ORDER BY createdAt DESC")
    fun getAllMarkedPages(): Flow<List<com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity>>

    @Query("SELECT pageIndex FROM marked_pages WHERE chapterId = :chapterId")
    fun getMarkedPageIndicesForChapter(chapterId: Int): Flow<List<Int>>

    @Query("SELECT * FROM marked_pages WHERE chapterId = :chapterId AND pageIndex = :pageIndex LIMIT 1")
    fun getMarkedPage(chapterId: Int, pageIndex: Int): com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity?

    @Query("SELECT * FROM marked_pages WHERE id = :id LIMIT 1")
    fun getMarkedPageById(id: Int): com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMarkedPage(page: com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity)

    @Query("DELETE FROM marked_pages WHERE chapterId = :chapterId AND pageIndex = :pageIndex")
    fun deleteMarkedPage(chapterId: Int, pageIndex: Int): Int

    @Query("DELETE FROM marked_pages WHERE id = :id")
    fun deleteMarkedPageById(id: Int): Int

    @Query("UPDATE marked_pages SET reviewTag = :reviewTag WHERE id = :id")
    fun updateReviewTag(id: Int, reviewTag: String): Int

    @Query("DELETE FROM marked_pages")
    fun clearAllMarkedPages(): Int
}
