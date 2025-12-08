package com.w3n9.chengying.data.repository

import com.w3n9.chengying.data.source.ShizukuInputManager
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
    private val shizukuInputManager: ShizukuInputManager
) : CursorRepository {

    private val _cursorState = MutableStateFlow(CursorState())
    override val cursorState: StateFlow<CursorState> = _cursorState.asStateFlow()

    private val _clickEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val clickEvents: SharedFlow<Unit> = _clickEvents.asSharedFlow()

    private var boundsWidth = 1920
    private var boundsHeight = 1080
    
    // We need to know the target display ID for Shizuku injection.
    // This can be set when presentation starts.
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
    
    // Helper to set target display from Presentation
    override fun setTargetDisplayId(displayId: Int) {
        this.targetDisplayId = displayId
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
        // Update internal state first for UI feedback if needed
        updatePosition(deltaX, deltaY)
        
        // Inject into system
        val currentState = _cursorState.value
        shizukuInputManager.injectMotionEvent(
            android.view.MotionEvent.ACTION_HOVER_MOVE, 
            currentState.x, 
            currentState.y, 
            targetDisplayId
        )
    }

    override suspend fun emitClickWithShizuku() {
        val currentState = _cursorState.value
        shizukuInputManager.injectClick(currentState.x, currentState.y, targetDisplayId)
        // Also emit local event for UI reaction
        emitClick()
    }
}
