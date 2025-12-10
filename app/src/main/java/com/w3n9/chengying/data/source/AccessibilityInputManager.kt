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

    fun injectMotionEvent(action: Int, x: Float, y: Float, displayId: Int) {
        // Not implemented for accessibility service
        Timber.d("[AccessibilityInputManager::injectMotionEvent] Not implemented")
    }

    fun isEnabled(): Boolean {
        return ChengyingAccessibilityService.isEnabled()
    }
}
