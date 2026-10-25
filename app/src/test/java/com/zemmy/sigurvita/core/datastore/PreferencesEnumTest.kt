package com.zemmy.sigurvita.core.datastore

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zemmy.sigurvita.core.model.AppThemeMode
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderBackground
import com.zemmy.sigurvita.core.model.ReaderOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesEnumTest {

    private val key = stringPreferencesKey("value")

    private fun prefsWith(value: String) = mutablePreferencesOf().apply { this[key] = value }

    @Test
    fun storedNameIsParsed() {
        assertEquals(
            ReaderOrientation.LANDSCAPE,
            prefsWith("LANDSCAPE").enumOrDefault(key, ReaderOrientation.FREE)
        )
    }

    @Test
    fun missingKeyGivesTheDefault() {
        assertEquals(ReaderOrientation.FREE, emptyPreferences().enumOrDefault(key, ReaderOrientation.FREE))
    }

    @Test
    fun unknownNameGivesTheDefaultInsteadOfFailing() {
        assertEquals(ReaderOrientation.FREE, prefsWith("SIDEWAYS").enumOrDefault(key, ReaderOrientation.FREE))
        assertEquals(ReaderOrientation.FREE, prefsWith("").enumOrDefault(key, ReaderOrientation.FREE))
    }

    @Test
    fun namesAreCaseSensitive() {
        assertEquals(ReaderOrientation.FREE, prefsWith("landscape").enumOrDefault(key, ReaderOrientation.FREE))
    }

    @Test
    fun documentedDefaultsAreWhatTheFlowsUse() {
        val empty = emptyPreferences()
        assertEquals(FitMode.FIT_WIDTH, empty.enumOrDefault(key, FitMode.FIT_WIDTH))
        assertEquals(AutoDeleteOption.NEVER, empty.enumOrDefault(key, AutoDeleteOption.NEVER))
        assertEquals(ReaderBackground.BLACK, empty.enumOrDefault(key, ReaderBackground.BLACK))
        assertEquals(AppThemeMode.SYSTEM, empty.enumOrDefault(key, AppThemeMode.SYSTEM))
    }
}
