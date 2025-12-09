package com.w3n9.chengying.data.source

import com.w3n9.chengying.domain.model.DisplayMode
import kotlinx.coroutines.flow.Flow

interface SettingsDataSource {
    val displayMode: Flow<DisplayMode>
    suspend fun setDisplayMode(mode: DisplayMode)
}
