package com.w3n9.chengying.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.w3n9.chengying.core.common.Result
import com.w3n9.chengying.core.common.UiState
import com.w3n9.chengying.core.common.asResult
import com.w3n9.chengying.domain.model.ExternalDisplay
import com.w3n9.chengying.domain.repository.CursorRepository
import com.w3n9.chengying.domain.repository.DisplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface HomeEvent {
    data class StartDesktopMode(val display: ExternalDisplay) : HomeEvent
    object StopDesktopMode : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val displayRepository: DisplayRepository,
    private val cursorRepository: CursorRepository
) : ViewModel() {

    private val _touchpadModeActive = MutableStateFlow(false)
    val touchpadModeActive: StateFlow<Boolean> = _touchpadModeActive.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    val uiState: StateFlow<UiState<List<ExternalDisplay>>> = displayRepository.connectedDisplays
        .asResult()
        .map { result ->
            when (result) {
                is Result.Success -> {
                    // If display is disconnected while in touchpad mode, exit mode.
                    if (touchpadModeActive.value && result.data.isEmpty()) {
                        stopDesktopMode()
                    }
                    UiState.Success(result.data)
                }
                is Result.Loading -> UiState.Loading
                is Result.Error -> {
                    Timber.e(result.exception, "[HomeViewModel::uiState] Error loading displays")
                    UiState.Error(result.exception.message ?: "Unknown error")
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading
        )

    fun startDesktopMode(display: ExternalDisplay) {
        viewModelScope.launch {
            // Need to set the target display ID for the cursor repository so Shizuku knows where to inject events
            cursorRepository.setTargetDisplayId(display.id)
            
            _touchpadModeActive.value = true
            _events.emit(HomeEvent.StartDesktopMode(display))
        }
    }

    private fun stopDesktopMode() {
        viewModelScope.launch {
            _touchpadModeActive.value = false
            _events.emit(HomeEvent.StopDesktopMode)
        }
    }

    fun consumeEvent() {
        // No-op for shared flow, but kept if we switch to state-based event handling later or for clarity
    }

    fun onTouchpadPan(deltaX: Float, deltaY: Float) {
        // Using Shizuku for input injection
        cursorRepository.updatePositionWithShizuku(deltaX, deltaY)
    }

    fun onTouchpadClick() {
        viewModelScope.launch {
            cursorRepository.emitClickWithShizuku()
        }
    }
}
