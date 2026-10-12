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
    version = 6,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_series ADD COLUMN totalIssues INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_series ADD COLUMN issuesRead INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE marked_pages ADD COLUMN reviewTag TEXT NOT NULL DEFAULT 'DEBUG'")
            }
        }

        fun getDatabase(context: Context): SigurvitaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SigurvitaDatabase::class.java,
                    "sigurvita_db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
