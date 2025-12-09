package com.w3n9.chengying.domain.repository

import com.w3n9.chengying.domain.model.DisplayMode
import kotlinx.coroutines.flow.Flow

interface DisplaySettingsRepository {
    val displayMode: Flow<DisplayMode>
    suspend fun setDisplayMode(mode: DisplayMode)
}
