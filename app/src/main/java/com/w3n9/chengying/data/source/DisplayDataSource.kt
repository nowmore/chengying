package com.w3n9.chengying.data.source

import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.flow.Flow

interface DisplayDataSource {
    fun getDisplays(): List<Display>
    val displayEvents: Flow<Unit> // Emits when display added/removed/changed
}
