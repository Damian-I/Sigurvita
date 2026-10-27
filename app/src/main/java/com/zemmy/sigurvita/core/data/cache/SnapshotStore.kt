package com.zemmy.sigurvita.core.data.cache

import android.content.Context
import com.zemmy.sigurvita.core.model.Library
import com.zemmy.sigurvita.core.model.ReadingList
import com.zemmy.sigurvita.core.model.Series
import com.zemmy.sigurvita.core.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

object SnapshotNames {
    const val HOME = "home.json"
    const val ALL_SERIES = "all_series.json"
    const val DRAWER = "drawer.json"
}

/**
 * Small JSON files in noBackupFilesDir/snapshots (not Room, not DataStore, not cloud backup).
 * They hold what the app showed last time so the next launch can draw it immediately while fresh data
 * loads in the background. Every snapshot carries an [accountKey] (hash of server URL + API key), and a
 * snapshot that does not match the current account is ignored.
 */
class SnapshotStore(private val context: Context) {

    private val dir: File get() = File(context.noBackupFilesDir, "snapshots")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val writeLock = Mutex()

    fun accountKey(config: ServerConfig): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${config.serverUrl}|${config.apiKey}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    suspend fun <T> read(name: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val file = File(dir, name)
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    suspend fun <T> write(name: String, serializer: KSerializer<T>, value: T) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                try {
                    dir.mkdirs()
                    val tmp = File(dir, "$name.tmp")
                    tmp.writeText(json.encodeToString(serializer, value))
                    val target = File(dir, name)
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A snapshot is an optimisation; failing to save it must never affect the app
                }
            }
        }
    }

    /** Milliseconds since the snapshot was last written, or [Long.MAX_VALUE] if it does not exist. */
    fun ageMillis(name: String): Long {
        val file = File(dir, name)
        return if (file.exists()) System.currentTimeMillis() - file.lastModified() else Long.MAX_VALUE
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }
}

// ---------------------------------------------------------------- snapshot shapes

@Serializable
data class LibrarySnap(
    val id: Int,
    val name: String,
    val type: Int,
    val coverUrl: String? = null
)

@Serializable
data class SeriesSnap(
    val id: Int,
    val libraryId: Int,
    val name: String,
    val localizedName: String? = null,
    val coverUrl: String? = null,
    val pagesRead: Int = 0,
    val totalPages: Int = 0,
    val totalIssues: Int = 0,
    val issuesRead: Int = 0,
    val isCompleted: Boolean = false,
    val userRating: Float = 0f
)

@Serializable
data class ReadingListSnap(
    val id: Int,
    val title: String,
    val summary: String? = null,
    val itemCount: Int = 0,
    val coverUrl: String? = null,
    val promoted: Boolean = false,
    val totalPages: Int = 0,
    val pagesRead: Int = 0,
    val issuesRead: Int = 0
)

@Serializable
data class HomeSnapshot(
    val accountKey: String,
    val savedAt: Long,
    val libraries: List<LibrarySnap>,
    val onDeck: List<SeriesSnap>,
    val recentlyUpdated: List<SeriesSnap>,
    val newlyAdded: List<SeriesSnap>
)

@Serializable
data class AllSeriesSnapshot(
    val accountKey: String,
    val savedAt: Long,
    val series: List<SeriesSnap>
)

@Serializable
data class DrawerSnapshot(
    val accountKey: String,
    val savedAt: Long,
    val libraries: List<LibrarySnap>,
    val readingLists: List<ReadingListSnap>
)

// ---------------------------------------------------------------- mappers
// Series summaries and reading-list items are intentionally not stored: these lists only need what the
// cards and the drawer show, and the full data arrives with the background refresh.

fun Library.toSnap() = LibrarySnap(id = id, name = name, type = type, coverUrl = coverUrl)

fun LibrarySnap.toModel() = Library(id = id, name = name, type = type, coverUrl = coverUrl)

fun Series.toSnap() = SeriesSnap(
    id = id,
    libraryId = libraryId,
    name = name,
    localizedName = localizedName,
    coverUrl = coverUrl,
    pagesRead = pagesRead,
    totalPages = totalPages,
    totalIssues = totalIssues,
    issuesRead = issuesRead,
    isCompleted = isCompleted,
    userRating = userRating
)

fun SeriesSnap.toModel() = Series(
    id = id,
    libraryId = libraryId,
    name = name,
    localizedName = localizedName,
    coverUrl = coverUrl,
    pagesRead = pagesRead,
    totalPages = totalPages,
    totalIssues = totalIssues,
    issuesRead = issuesRead,
    isCompleted = isCompleted,
    userRating = userRating
)

fun ReadingList.toSnap() = ReadingListSnap(
    id = id,
    title = title,
    summary = summary,
    itemCount = itemCount,
    coverUrl = coverUrl,
    promoted = promoted,
    totalPages = totalPages,
    pagesRead = pagesRead,
    issuesRead = issuesRead
)

fun ReadingListSnap.toModel() = ReadingList(
    id = id,
    title = title,
    summary = summary,
    itemCount = itemCount,
    coverUrl = coverUrl,
    promoted = promoted,
    totalPages = totalPages,
    pagesRead = pagesRead,
    issuesRead = issuesRead
)
