package com.w3n9.chengying.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Path
import android.os.Build
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.w3n9.chengying.ui.overlay.CursorOverlayView
import timber.log.Timber

@SuppressLint("AccessibilityPolicy")
class ChengyingAccessibilityService : AccessibilityService() {

    private var cursorOverlay: CursorOverlayView? = null
    private var windowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var currentDisplayId: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Timber.i("[ChengyingAccessibilityService] Service connected")
        Timber.i("[ChengyingAccessibilityService] Android version: ${Build.VERSION.SDK_INT}")

        val canPerform = serviceInfo?.flags?.and(android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0
        Timber.i("[ChengyingAccessibilityService] Touch exploration enabled: $canPerform")

        instance = this
    }
    
    fun showCursorOverlay(displayId: Int) {
        if (cursorOverlay != null) {
            Timber.d("[ChengyingAccessibilityService] Cursor overlay already shown")
            return
        }
        
        currentDisplayId = displayId
        
        // Get WindowManager for the specific display
        windowManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val displayManager = getSystemService(DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val targetDisplay = displayManager.getDisplay(displayId)
                if (targetDisplay != null) {
                    val displayContext = createDisplayContext(targetDisplay)
                    displayContext.getSystemService(WINDOW_SERVICE) as WindowManager
                } else {
                    Timber.w("[ChengyingAccessibilityService] Display $displayId not found, using default")
                    getSystemService(WINDOW_SERVICE) as WindowManager
                }
            } catch (e: Exception) {
                Timber.e(e, "[ChengyingAccessibilityService] Failed to get display-specific WindowManager")
                getSystemService(WINDOW_SERVICE) as WindowManager
            }
        } else {
            getSystemService(WINDOW_SERVICE) as WindowManager
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        overlayParams = params
        
        cursorOverlay = CursorOverlayView(this).also { view ->
            try {
                windowManager?.addView(view, params)
                Timber.i("[ChengyingAccessibilityService] Cursor overlay created on display $displayId")
            } catch (e: Exception) {
                Timber.e(e, "[ChengyingAccessibilityService] Failed to create cursor overlay")
                cursorOverlay = null
            }
        }
    }
    
    fun hideCursorOverlay() {
        cursorOverlay?.let { view ->
            try {
                windowManager?.removeView(view)
                Timber.i("[ChengyingAccessibilityService] Cursor overlay removed")
            } catch (e: Exception) {
                Timber.e(e, "[ChengyingAccessibilityService] Failed to remove cursor overlay")
            }
        }
        cursorOverlay = null
        windowManager = null
        overlayParams = null
    }
    
    fun updateCursor(x: Float, y: Float, visible: Boolean) {
        cursorOverlay?.updateCursor(x, y, visible)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events
    }

    override fun onInterrupt() {
        Timber.w("[ChengyingAccessibilityService] Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        hideCursorOverlay()
        Timber.i("[ChengyingAccessibilityService] Service destroyed")
        instance = null
    }

    fun performClick(x: Float, y: Float, displayId: Int, callback: (Boolean) -> Unit) {
        Timber.d("[ChengyingAccessibilityService] Attempting click at ($x, $y) on display $displayId")
        
        val path = Path().apply {
            moveTo(x, y)
        }

        val gestureBuilder = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
        
        // Try to set display ID if supported (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                gestureBuilder.setDisplayId(displayId)
                Timber.d("[ChengyingAccessibilityService] Display ID set to $displayId")
            } catch (e: Exception) {
                Timber.w(e, "[ChengyingAccessibilityService] Failed to set display ID")
            }
        }
        
        val gesture = gestureBuilder.build()

        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Timber.i("[ChengyingAccessibilityService] ✓ Click gesture completed at ($x, $y) on display $displayId")
                callback(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Timber.w("[ChengyingAccessibilityService] ✗ Click gesture cancelled at ($x, $y)")
                callback(false)
            }
        }, null)

        if (!result) {
            Timber.e("[ChengyingAccessibilityService] ✗ Failed to dispatch gesture")
            callback(false)
        } else {
            Timber.d("[ChengyingAccessibilityService] Gesture dispatched successfully")
        }
    }

    companion object {
        private var instance: ChengyingAccessibilityService? = null

        fun getInstance(): ChengyingAccessibilityService? = instance

        fun isEnabled(): Boolean = instance != null
    }
}
