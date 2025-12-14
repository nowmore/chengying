package com.w3n9.chengying.data.source

import com.w3n9.chengying.service.ChengyingAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AccessibilityInputManager @Inject constructor() {

    suspend fun injectClick(x: Float, y: Float, displayId: Int): Boolean = withContext(Dispatchers.Main) {
        val service = ChengyingAccessibilityService.getInstance()

        if (service == null) {
            Timber.w("[AccessibilityInputManager::injectClick] Accessibility service not enabled")
            return@withContext false
        }

        suspendCancellableCoroutine { continuation ->
            service.performClick(x, y, displayId) { success ->
                if (continuation.isActive) {
                    continuation.resume(success)
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        return ChengyingAccessibilityService.isEnabled()
    }

    fun showCursorOverlay(displayId: Int) {
        val service = ChengyingAccessibilityService.getInstance()
        if (service == null) {
            Timber.w("[AccessibilityInputManager::showCursorOverlay] Accessibility service not enabled")
            return
        }
        service.showCursorOverlay(displayId)
    }

    fun hideCursorOverlay() {
        val service = ChengyingAccessibilityService.getInstance()
        if (service == null) {
            Timber.w("[AccessibilityInputManager::hideCursorOverlay] Accessibility service not enabled")
            return
        }
        service.hideCursorOverlay()
    }

    fun updateCursor(x: Float, y: Float, visible: Boolean) {
        val service = ChengyingAccessibilityService.getInstance()
        if (service == null) {
            return // Silent fail - cursor updates are frequent
        }
        service.updateCursor(x, y, visible)
    }
}
