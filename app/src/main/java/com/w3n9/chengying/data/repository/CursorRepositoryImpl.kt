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

    private var boundsWidth = 1920
    private var boundsHeight = 1080
    
    private var targetDisplayId: Int = 0

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
        _clickEvents.emit(Unit)
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
