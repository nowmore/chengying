package com.w3n9.chengying.data.repository

import com.w3n9.chengying.core.config.ScreenSaverConfig
import com.w3n9.chengying.data.source.AccessibilityInputManager
import com.w3n9.chengying.di.ApplicationScope
import com.w3n9.chengying.domain.model.CursorState
import com.w3n9.chengying.domain.repository.CursorRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_BOUNDS_WIDTH = 1920
private const val DEFAULT_BOUNDS_HEIGHT = 1080
private const val SCREEN_SAVER_CHECK_INTERVAL_MS = 1000L

@Singleton
class CursorRepositoryImpl @Inject constructor(
    private val accessibilityInputManager: AccessibilityInputManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) : CursorRepository {

    private val _cursorState = MutableStateFlow(CursorState())
    override val cursorState: StateFlow<CursorState> = _cursorState.asStateFlow()

    private val _clickEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val clickEvents: SharedFlow<Unit> = _clickEvents.asSharedFlow()

    private val _isAppLaunched = MutableStateFlow(false)
    override val isAppLaunched: StateFlow<Boolean> = _isAppLaunched.asStateFlow()

    private val _isTaskSwitcherVisible = MutableStateFlow(false)
    override val isTaskSwitcherVisible: StateFlow<Boolean> = _isTaskSwitcherVisible.asStateFlow()

    private val _isScreenSaverActive = MutableStateFlow(false)
    override val isScreenSaverActive: StateFlow<Boolean> = _isScreenSaverActive.asStateFlow()

    private var boundsWidth = DEFAULT_BOUNDS_WIDTH
    private var boundsHeight = DEFAULT_BOUNDS_HEIGHT
    private var targetDisplayId: Int = 0
    private var lastInteractionTime = System.currentTimeMillis()
    private var screenSaverJob: Job? = null

    override fun setBounds(width: Int, height: Int) {
        boundsWidth = width
        boundsHeight = height
        if (_cursorState.value.x == 0f && _cursorState.value.y == 0f) {
            _cursorState.update {
                it.copy(x = width / 2f, y = height / 2f)
            }
        }
    }

    override fun setTargetDisplayId(displayId: Int) {
        this.targetDisplayId = displayId
    }

    override fun setAppLaunched(launched: Boolean) {
        _isAppLaunched.value = launched
    }

    override fun toggleTaskSwitcher() {
        _isTaskSwitcherVisible.update { !it }
    }

    override fun updatePosition(deltaX: Float, deltaY: Float) {
        resetInteraction()
        _cursorState.update { current ->
            val newX = (current.x + deltaX).coerceIn(0f, boundsWidth.toFloat())
            val newY = (current.y + deltaY).coerceIn(0f, boundsHeight.toFloat())
            current.copy(x = newX, y = newY)
        }
    }

    override fun reset() {
        _cursorState.update { CursorState(x = boundsWidth / 2f, y = boundsHeight / 2f) }
    }

    override suspend fun emitClick() {
        resetInteraction()
        _clickEvents.emit(Unit)
    }

    override fun startScreenSaverTimer() {
        stopScreenSaverTimer()
        screenSaverJob = applicationScope.launch {
            while (true) {
                delay(SCREEN_SAVER_CHECK_INTERVAL_MS)
                val idleTime = System.currentTimeMillis() - lastInteractionTime

                if (idleTime >= ScreenSaverConfig.TIMEOUT_MS && !_isScreenSaverActive.value) {
                    _isScreenSaverActive.value = true
                    Timber.i("[CursorRepositoryImpl::startScreenSaverTimer] Screen saver activated after ${idleTime}ms idle")
                }
            }
        }
    }

    override fun stopScreenSaverTimer() {
        screenSaverJob?.cancel()
        screenSaverJob = null
        _isScreenSaverActive.value = false
    }

    private fun resetInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (_isScreenSaverActive.value) {
            _isScreenSaverActive.value = false
        }
    }

    override fun updatePositionWithShizuku(deltaX: Float, deltaY: Float) {
        updatePosition(deltaX, deltaY)
    }

    override suspend fun emitClickWithShizuku() {
        val currentState = _cursorState.value

        val success = if (accessibilityInputManager.isEnabled()) {
            accessibilityInputManager.injectClick(currentState.x, currentState.y, targetDisplayId)
        } else {
            Timber.w("[CursorRepositoryImpl::emitClickWithShizuku] AccessibilityInputManager not enabled")
            false
        }

        if (success) {
            emitClick()
        }
    }

    // Cursor overlay control - delegates to AccessibilityInputManager
    override fun showCursorOverlay(displayId: Int) {
        accessibilityInputManager.showCursorOverlay(displayId)
        Timber.i("[CursorRepositoryImpl::showCursorOverlay] Requested overlay for display $displayId")
    }

    override fun hideCursorOverlay() {
        accessibilityInputManager.hideCursorOverlay()
        Timber.i("[CursorRepositoryImpl::hideCursorOverlay] Requested overlay hide")
    }

    override fun updateCursorOverlay() {
        val state = _cursorState.value
        val shouldShow = state.isVisible && !_isScreenSaverActive.value
        accessibilityInputManager.updateCursor(state.x, state.y, shouldShow)
    }
}
