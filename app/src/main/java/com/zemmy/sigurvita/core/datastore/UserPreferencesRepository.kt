package com.zemmy.sigurvita.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import com.zemmy.sigurvita.core.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sigurvita_settings")

class UserPreferencesRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val API_KEY = stringPreferencesKey("api_key")
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
        val USER_ID = intPreferencesKey("user_id")

        val READING_MODE = stringPreferencesKey("reading_mode")
        val FIT_MODE = stringPreferencesKey("fit_mode")
        val VOLUME_KEY_NAV = booleanPreferencesKey("volume_key_nav")
        val VOLUME_KEY_INVERT = booleanPreferencesKey("volume_key_invert")
        val TOUCH_EDGE_NAV = booleanPreferencesKey("touch_edge_nav")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val AUTO_DELETE_OPTION = stringPreferencesKey("auto_delete_option")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val SPEECH_BUBBLE_DETECTION_ENABLED = booleanPreferencesKey("speech_bubble_detection_enabled")
        val SPEECH_BUBBLES_ENABLED = booleanPreferencesKey("speech_bubbles_enabled")
        val SPEECH_BUBBLE_BOXES_ENABLED = booleanPreferencesKey("speech_bubble_boxes_enabled")
        val SPEECH_BUBBLE_CONFIDENCE = floatPreferencesKey("speech_bubble_confidence")
        val COMPLETED_CHAPTER_HISTORY = stringPreferencesKey("completed_chapter_history")
        val LIBRARY_ORDER = stringPreferencesKey("library_order")
        val CHOSEN_READING_LISTS = stringPreferencesKey("chosen_reading_lists")
    }

    @Volatile
    var currentServerConfig: ServerConfig = ServerConfig()
        private set

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val url = preferences[Keys.SERVER_URL] ?: ""
            val apiKey = preferences[Keys.API_KEY] ?: ""
            val token = preferences[Keys.JWT_TOKEN] ?: ""
            val userId = preferences[Keys.USER_ID] ?: 0
            ServerConfig(
                serverUrl = url,
                apiKey = apiKey,
                jwtToken = token,
                userId = userId,
                isLoggedIn = url.isNotBlank() && (apiKey.isNotBlank() || token.isNotBlank())
            )
        }

    val readerSettingsFlow: Flow<ReaderSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val readingModeStr = preferences[Keys.READING_MODE]
            val fitModeStr = preferences[Keys.FIT_MODE]

            val readingMode = try {
                if (readingModeStr != null) ReadingMode.valueOf(readingModeStr) else ReadingMode.RTL
            } catch (_: Exception) {
                ReadingMode.RTL
            }

            val fitMode = try {
                if (fitModeStr != null) FitMode.valueOf(fitModeStr) else FitMode.FIT_WIDTH
            } catch (_: Exception) {
                FitMode.FIT_WIDTH
            }

            val autoDeleteStr = preferences[Keys.AUTO_DELETE_OPTION]
            val autoDeleteOption = try {
                if (autoDeleteStr != null) AutoDeleteOption.valueOf(autoDeleteStr) else AutoDeleteOption.NEVER
            } catch (_: Exception) {
                AutoDeleteOption.NEVER
            }

            val incognito = preferences[Keys.INCOGNITO_MODE] ?: false
            val detectionEnabled = preferences[Keys.SPEECH_BUBBLE_DETECTION_ENABLED]
                ?: preferences[Keys.SPEECH_BUBBLES_ENABLED]
                ?: true
            val boxesEnabled = preferences[Keys.SPEECH_BUBBLE_BOXES_ENABLED] ?: true
            val bubbleConfidence = preferences[Keys.SPEECH_BUBBLE_CONFIDENCE] ?: 0.10f

            ReaderSettings(
                readingMode = readingMode,
                fitMode = fitMode,
                volumeKeyNavigationEnabled = preferences[Keys.VOLUME_KEY_NAV] ?: true,
                volumeKeyInvert = preferences[Keys.VOLUME_KEY_INVERT] ?: false,
                touchEdgeNavigationEnabled = preferences[Keys.TOUCH_EDGE_NAV] ?: true,
                keepScreenOn = preferences[Keys.KEEP_SCREEN_ON] ?: true,
                autoDeleteOption = autoDeleteOption,
                isIncognitoMode = incognito,
                speechBubbleDetectionEnabled = detectionEnabled,
                speechBubbleBoxesEnabled = boxesEnabled,
                speechBubbleConfidence = bubbleConfidence
            )
        }

    init {
        scope.launch {
            serverConfigFlow.collect { config ->
                currentServerConfig = config
            }
        }
    }

    suspend fun saveServerConfig(
        serverUrl: String,
        apiKey: String,
        jwtToken: String = "",
        userId: Int = 0
    ) {
        val formattedUrl = serverUrl.trim().removeSuffix("/")
        val config = ServerConfig(
            serverUrl = formattedUrl,
            apiKey = apiKey.trim(),
            jwtToken = jwtToken.trim(),
            userId = userId,
            isLoggedIn = formattedUrl.isNotBlank() && (apiKey.isNotBlank() || jwtToken.isNotBlank())
        )
        currentServerConfig = config

        context.dataStore.edit { preferences ->
            preferences[Keys.SERVER_URL] = formattedUrl
            preferences[Keys.API_KEY] = apiKey.trim()
            preferences[Keys.JWT_TOKEN] = jwtToken.trim()
            preferences[Keys.USER_ID] = userId
        }
    }

    suspend fun clearServerConfig() {
        currentServerConfig = ServerConfig()
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.SERVER_URL)
            preferences.remove(Keys.API_KEY)
            preferences.remove(Keys.JWT_TOKEN)
            preferences.remove(Keys.USER_ID)
        }
    }

    suspend fun updateReadingMode(mode: ReadingMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.READING_MODE] = mode.name
        }
    }

    suspend fun updateFitMode(mode: FitMode) {
        context.dataStore.edit { preferences ->
            preferences[Keys.FIT_MODE] = mode.name
        }
    }

    suspend fun updateVolumeKeyNavigation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.VOLUME_KEY_NAV] = enabled
        }
    }

    suspend fun updateVolumeKeyInvert(invert: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.VOLUME_KEY_INVERT] = invert
        }
    }

    suspend fun updateTouchEdgeNavigation(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.TOUCH_EDGE_NAV] = enabled
        }
    }

    suspend fun updateKeepScreenOn(keep: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.KEEP_SCREEN_ON] = keep
        }
    }



    suspend fun updateAutoDeleteOption(option: AutoDeleteOption) {
        context.dataStore.edit { preferences ->
            preferences[Keys.AUTO_DELETE_OPTION] = option.name
        }
    }

    suspend fun updateIncognitoMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.INCOGNITO_MODE] = enabled
        }
    }

    suspend fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SPEECH_BUBBLE_DETECTION_ENABLED] = enabled
            preferences[Keys.SPEECH_BUBBLES_ENABLED] = enabled
        }
    }

    suspend fun updateSpeechBubbleBoxesEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SPEECH_BUBBLE_BOXES_ENABLED] = enabled
        }
    }

    suspend fun updateSpeechBubbleConfidence(confidence: Float) {
        context.dataStore.edit { preferences ->
            preferences[Keys.SPEECH_BUBBLE_CONFIDENCE] = confidence
        }
    }

    val completedChapterHistoryFlow: Flow<List<Int>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val raw = preferences[Keys.COMPLETED_CHAPTER_HISTORY] ?: ""
            if (raw.isBlank()) emptyList()
            else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

    suspend fun addCompletedChapter(chapterId: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.COMPLETED_CHAPTER_HISTORY] ?: ""
            val list = if (current.isBlank()) mutableListOf()
            else current.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()

            list.remove(chapterId)
            list.add(chapterId)

            preferences[Keys.COMPLETED_CHAPTER_HISTORY] = list.joinToString(",")
        }
    }

    suspend fun removeCompletedChapters(chapterIds: List<Int>) {
        if (chapterIds.isEmpty()) return
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.COMPLETED_CHAPTER_HISTORY] ?: ""
            if (current.isNotBlank()) {
                val list = current.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
                list.removeAll(chapterIds)
                preferences[Keys.COMPLETED_CHAPTER_HISTORY] = list.joinToString(",")
            }
        }
    }

    val libraryOrderFlow: Flow<List<Int>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val raw = preferences[Keys.LIBRARY_ORDER] ?: ""
            if (raw.isBlank()) emptyList()
            else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

    suspend fun updateLibraryOrder(order: List<Int>) {
        context.dataStore.edit { preferences ->
            preferences[Keys.LIBRARY_ORDER] = order.joinToString(",")
        }
    }

    val chosenReadingListsFlow: Flow<List<Int>> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val raw = preferences[Keys.CHOSEN_READING_LISTS] ?: ""
            if (raw.isBlank()) emptyList()
            else raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

    suspend fun addChosenReadingList(listId: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.CHOSEN_READING_LISTS] ?: ""
            val list = if (current.isBlank()) mutableListOf()
            else current.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
            if (!list.contains(listId)) {
                list.add(listId)
                preferences[Keys.CHOSEN_READING_LISTS] = list.joinToString(",")
            }
        }
    }

    suspend fun removeChosenReadingList(listId: Int) {
        context.dataStore.edit { preferences ->
            val current = preferences[Keys.CHOSEN_READING_LISTS] ?: ""
            if (current.isNotBlank()) {
                val list = current.split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableList()
                list.remove(listId)
                preferences[Keys.CHOSEN_READING_LISTS] = list.joinToString(",")
            }
        }
    }
}
