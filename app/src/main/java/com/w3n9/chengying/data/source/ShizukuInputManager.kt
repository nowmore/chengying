package com.w3n9.chengying.data.source

import android.content.Context
import android.os.IBinder
import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuInputManager @Inject constructor() {

    private var iInputManager: Any? = null
    private var injectInputEventMethod: Method? = null
    
    // INJECT_INPUT_EVENT_MODE_ASYNC = 0
    private val INJECT_MODE_ASYNC = 0

    // Listener reference to prevent GC
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        setupInputManager()
    }

    init {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        
        try {
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                setupInputManager()
            }
        } catch (e: Exception) {
            // Shizuku might not be available
            Timber.e(e, "Shizuku checkSelfPermission failed")
        }
    }

    private fun setupInputManager() {
        try {
            val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
            val inputManagerStub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = inputManagerStub.getMethod("asInterface", IBinder::class.java)
            iInputManager = asInterfaceMethod.invoke(null, binder)

            // injectInputEvent(InputEvent event, int mode)
            val inputManagerClass = iInputManager?.javaClass
            injectInputEventMethod = inputManagerClass?.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            
            Timber.d("ShizukuInputManager: Setup successful")
        } catch (e: Exception) {
            Timber.e(e, "ShizukuInputManager: Failed to setup")
        }
    }

    fun injectMotionEvent(action: Int, x: Float, y: Float, displayId: Int) {
        if (iInputManager == null) {
            Timber.w("ShizukuInputManager: IInputManager not initialized")
            return
        }

        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            now, now, action, x, y, 0
        ).apply {
            source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        }
        
        // Set displayId via reflection
        try {
             val setDisplayIdMethod = MotionEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
             setDisplayIdMethod.invoke(event, displayId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set displayId on MotionEvent")
        }

        try {
            injectInputEventMethod?.invoke(iInputManager, event, INJECT_MODE_ASYNC)
        } catch (e: Exception) {
            Timber.e(e, "Failed to inject MotionEvent")
        } finally {
            event.recycle()
        }
    }
    
    fun injectClick(x: Float, y: Float, displayId: Int) {
        injectMotionEvent(MotionEvent.ACTION_DOWN, x, y, displayId)
        injectMotionEvent(MotionEvent.ACTION_UP, x, y, displayId)
    }
}
