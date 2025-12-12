package com.w3n9.chengying.data.repository

import com.w3n9.chengying.domain.model.CursorState
import com.w3n9.chengying.domain.repository.CursorRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CursorRepositoryImpl @Inject constructor(
    private val accessibilityInputManager: com.w3n9.chengying.data.source.AccessibilityInputManager
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

    private var boundsWidth = 1920
    private var boundsHeight = 1080
    
    private var targetDisplayId: Int = 0
    
    private var lastInteractionTime = System.currentTimeMillis()
    private var screenSaverJob: kotlinx.coroutines.Job? = null
    private val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default + kotlinx.coroutines.SupervisorJob())

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
        screenSaverJob = coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val currentTime = System.currentTimeMillis()
                val idleTime = currentTime - lastInteractionTime
                
                if (idleTime >= com.w3n9.chengying.core.config.ScreenSaverConfig.TIMEOUT_MS && !_isScreenSaverActive.value) {
                    _isScreenSaverActive.value = true
                    timber.log.Timber.i("[CursorRepository] Screen saver activated after ${idleTime}ms idle")
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
            false
        }
        
        if (success) {
            emitClick()
        }
    }
}
