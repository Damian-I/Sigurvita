package com.zemmy.sigurvita

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.db.SigurvitaDatabase
import com.zemmy.sigurvita.core.data.repository.AuthRepository
import com.zemmy.sigurvita.core.data.repository.DownloadRepository
import com.zemmy.sigurvita.core.data.repository.LibraryRepository
import com.zemmy.sigurvita.core.data.repository.ReaderRepository
import com.zemmy.sigurvita.core.data.repository.ReadingListRepository
import com.zemmy.sigurvita.core.data.repository.SeriesRepository
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.zemmy.sigurvita.core.data.repository.DetectionRepository
import com.zemmy.sigurvita.core.data.cache.SnapshotStore

class SigurvitaApp : Application(), ImageLoaderFactory {

    lateinit var database: SigurvitaDatabase
        private set

    lateinit var userPreferencesRepository: UserPreferencesRepository
        private set

    // Singleton API client — shared across all ViewModels
    val kavitaApi: KavitaApi by lazy {
        NetworkClient.getKavitaApi(userPreferencesRepository)
    }

    // Singleton repositories — eliminates redundant OkHttpClient/connection pool creation
    val authRepository: AuthRepository by lazy {
        AuthRepository(kavitaApi, userPreferencesRepository)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(kavitaApi, database, userPreferencesRepository)
    }

    val seriesRepository: SeriesRepository by lazy {
        SeriesRepository(kavitaApi, database, userPreferencesRepository)
    }

    val readingListRepository: ReadingListRepository by lazy {
        ReadingListRepository(kavitaApi, database, userPreferencesRepository)
    }

    val readerRepository: ReaderRepository by lazy {
        ReaderRepository(kavitaApi, this, userPreferencesRepository, database)
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(database, this)
    }

    val markedPageRepository: com.zemmy.sigurvita.core.data.repository.MarkedPageRepository by lazy {
        com.zemmy.sigurvita.core.data.repository.MarkedPageRepository(database, this)
    }

    val bubbleRepository: com.zemmy.sigurvita.core.data.repository.BubbleRepository by lazy {
        com.zemmy.sigurvita.core.data.repository.BubbleRepository(this)
    }

    val panelRepository: com.zemmy.sigurvita.core.data.repository.PanelRepository by lazy {
        com.zemmy.sigurvita.core.data.repository.PanelRepository(this)
    }

    val detectionRepository: DetectionRepository by lazy {
        DetectionRepository(this, bubbleRepository, panelRepository)
    }

    val snapshotStore: SnapshotStore by lazy { 
        SnapshotStore(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = SigurvitaDatabase.getDatabase(this)
        userPreferencesRepository = UserPreferencesRepository(this)
        CoroutineScope(Dispatchers.IO).launch {
            downloadRepository.validateExternalDeletions()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // Increased from 0.25 for smoother page rendering
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(500L * 1024 * 1024)
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
