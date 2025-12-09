package com.w3n9.chengying.data.repository

import android.view.Display
import com.w3n9.chengying.data.source.DisplayDataSource
import com.w3n9.chengying.data.source.SettingsDataSource
import com.w3n9.chengying.di.IODispatcher
import com.w3n9.chengying.domain.model.DisplayMode
import com.w3n9.chengying.domain.model.DisplayState
import com.w3n9.chengying.domain.model.ExternalDisplay
import com.w3n9.chengying.domain.repository.DisplayRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DisplayRepositoryImpl @Inject constructor(
    private val displayDataSource: DisplayDataSource,
    private val settingsDataSource: SettingsDataSource,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : DisplayRepository {

    override val connectedDisplays: Flow<List<ExternalDisplay>> = displayDataSource.displayEvents
        .onStart { emit(Unit) } // Trigger initial load
        .map {
            displayDataSource.getDisplays()
                .filter { it.displayId != Display.DEFAULT_DISPLAY }
                .map { display ->
                    ExternalDisplay(
                        id = display.displayId,
                        name = display.name,
                        width = display.mode.physicalWidth,
                        height = display.mode.physicalHeight,
                        state = if (display.isValid) DisplayState.CONNECTED else DisplayState.DISCONNECTED
                    )
                }
        }
        .flowOn(ioDispatcher)

    override val currentDisplayMode: Flow<DisplayMode> = settingsDataSource.displayMode
        .flowOn(ioDispatcher)

    override suspend fun setDisplayMode(mode: DisplayMode) = withContext(ioDispatcher) {
        settingsDataSource.setDisplayMode(mode)
    }
}
