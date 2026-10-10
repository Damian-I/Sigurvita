package com.zemmy.sigurvita.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zemmy.sigurvita.core.data.db.dao.DownloadDao
import com.zemmy.sigurvita.core.data.db.dao.MarkedPageDao
import com.zemmy.sigurvita.core.data.db.dao.SeriesDao
import com.zemmy.sigurvita.core.data.db.entity.CachedSeriesEntity
import com.zemmy.sigurvita.core.data.db.entity.DownloadTaskEntity
import com.zemmy.sigurvita.core.data.db.entity.MarkedPageEntity

@Database(
    entities = [
        CachedSeriesEntity::class,
        DownloadTaskEntity::class,
        MarkedPageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SigurvitaDatabase : RoomDatabase() {

    abstract fun seriesDao(): SeriesDao
    abstract fun downloadDao(): DownloadDao
    abstract fun markedPageDao(): MarkedPageDao

    companion object {
        @Volatile
        private var INSTANCE: SigurvitaDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS cached_chapters")
                db.execSQL("DROP TABLE IF EXISTS cached_reading_lists")
            }
        }

        fun getDatabase(context: Context): SigurvitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SigurvitaDatabase::class.java,
                    "sigurvita_db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
