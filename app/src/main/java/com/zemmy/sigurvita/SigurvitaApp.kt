package com.zemmy.sigurvita

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository

class SigurvitaApp : Application(), ImageLoaderFactory {

    lateinit var database: SigurvitaDatabase
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = SigurvitaDatabase.getDatabase(this)
        userPreferencesRepository = UserPreferencesRepository(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100 * 1024 * 1024) // 100MB
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: SigurvitaApp
            private set
    }
}
