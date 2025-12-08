package com.w3n9.chengying.data.source

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class LocalDisplayDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) : DisplayDataSource {

    private val displayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    override fun getDisplays(): List<Display> {
        return displayManager.displays.toList()
    }

    override val displayEvents: Flow<Unit> = callbackFlow {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                trySend(Unit)
            }

            override fun onDisplayRemoved(displayId: Int) {
                trySend(Unit)
            }

            override fun onDisplayChanged(displayId: Int) {
                trySend(Unit)
            }
        }
        displayManager.registerDisplayListener(listener, null)
        awaitClose {
            displayManager.unregisterDisplayListener(listener)
        }
    }
}
