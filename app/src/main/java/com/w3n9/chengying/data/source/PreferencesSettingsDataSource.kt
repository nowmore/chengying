package com.w3n9.chengying.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.w3n9.chengying.core.model.DisplayMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesSettingsDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsDataSource {

    private val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")

    override val displayMode: Flow<DisplayMode> = context.dataStore.data
        .map { preferences ->
            val modeString = preferences[DISPLAY_MODE_KEY] ?: DisplayMode.INDEPENDENT.name
            try {
                DisplayMode.valueOf(modeString)
            } catch (e: IllegalArgumentException) {
                DisplayMode.INDEPENDENT
            }
        }

    override suspend fun setDisplayMode(mode: DisplayMode) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_MODE_KEY] = mode.name
        }
    }
}
