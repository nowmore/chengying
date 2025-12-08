package com.w3n9.chengying.domain.repository

import com.w3n9.chengying.core.model.DisplayMode
import com.w3n9.chengying.domain.model.ExternalDisplay
import kotlinx.coroutines.flow.Flow

interface DisplayRepository {
    val connectedDisplays: Flow<List<ExternalDisplay>>
    val currentDisplayMode: Flow<DisplayMode>

    suspend fun setDisplayMode(mode: DisplayMode)
}
