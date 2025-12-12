package com.w3n9.chengying.domain.repository

import com.w3n9.chengying.domain.model.CursorState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface CursorRepository {
    val cursorState: StateFlow<CursorState>
    val clickEvents: SharedFlow<Unit>
    val isAppLaunched: StateFlow<Boolean>
    val isTaskSwitcherVisible: StateFlow<Boolean>
    val isScreenSaverActive: StateFlow<Boolean>

    fun setAppLaunched(launched: Boolean)
    fun toggleTaskSwitcher()
    fun setBounds(width: Int, height: Int)
    fun updatePosition(deltaX: Float, deltaY: Float)
    fun reset()
    suspend fun emitClick()
    fun setTargetDisplayId(displayId: Int)
    fun updatePositionWithShizuku(deltaX: Float, deltaY: Float)
    suspend fun emitClickWithShizuku()
    fun startScreenSaverTimer()
    fun stopScreenSaverTimer()
}
