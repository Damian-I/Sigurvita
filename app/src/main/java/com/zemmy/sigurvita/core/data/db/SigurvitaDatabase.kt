package com.zemmy.sigurvita.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zemmy.sigurvita.core.data.db.dao.ChapterDao
import com.zemmy.sigurvita.core.data.db.dao.DownloadDao
import com.zemmy.sigurvita.core.data.db.dao.MarkedPageDao
import com.zemmy.sigurvita.core.data.db.dao.ReadingListDao
import com.zemmy.sigurvita.core.data.db.dao.SeriesDao
import com.zemmy.sigurvita.core.data.db.entity.CachedChapterEntity
import com.zemmy.sigurvita.core.data.db.entity.CachedReadingListEntity
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity

@Database(
    entities = [
        CachedSeriesEntity::class,
        CachedChapterEntity::class,
        CachedReadingListEntity::class,
        DownloadTaskEntity::class,
        MarkedPageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SigurvitaDatabase : RoomDatabase() {

    abstract fun seriesDao(): SeriesDao
    abstract fun chapterDao(): ChapterDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun downloadDao(): DownloadDao
    abstract fun markedPageDao(): MarkedPageDao

    companion object {
        @Volatile
        private var INSTANCE: SigurvitaDatabase? = null

        fun getDatabase(context: Context): SigurvitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SigurvitaDatabase::class.java,
                    "sigurvita_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
