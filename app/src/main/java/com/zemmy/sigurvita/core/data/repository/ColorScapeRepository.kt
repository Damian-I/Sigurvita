package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.data.api.dto.ColorScapeDto
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

enum class ColorScapeKind { SERIES, VOLUME, CHAPTER }

class ColorScapeRepository(
    private val api: KavitaApi,
    preferences: UserPreferencesRepository
) {
    private data class Key(val kind: ColorScapeKind, val id: Int)

    private val cache = ConcurrentHashMap<Key, ColorScapeDto>()
    private val permits = Semaphore(4)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Global ColorScape toggle (default on). Shared so cards don't each collect DataStore. */
    val enabled: StateFlow<Boolean> = preferences.readerSettingsFlow
        .map { it.colorScapeEnabled }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, true)

    fun peek(kind: ColorScapeKind, id: Int): ColorScapeDto? = cache[Key(kind, id)]

    suspend fun get(kind: ColorScapeKind, id: Int): Result<ColorScapeDto> {
        if (id <= 0) return Result.success(ColorScapeDto())
        val key = Key(kind, id)
        cache[key]?.let { return Result.success(it) }
        return withContext(Dispatchers.IO) {
            permits.withPermit {
                cache[key]?.let { Result.success(it) } ?: fetch(key)
            }
        }
    }

    private suspend fun fetch(key: Key): Result<ColorScapeDto> = try {
        val response = when (key.kind) {
            ColorScapeKind.SERIES -> api.getSeriesColorScape(key.id)
            ColorScapeKind.VOLUME -> api.getVolumeColorScape(key.id)
            ColorScapeKind.CHAPTER -> api.getChapterColorScape(key.id)
        }
        val body = response.body()
        if (response.isSuccessful && body != null) {
            cache[key] = body
            Result.success(body)
        } else {
            Result.failure(IllegalStateException("ColorScape HTTP ${response.code()}"))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun clear() = cache.clear()
}