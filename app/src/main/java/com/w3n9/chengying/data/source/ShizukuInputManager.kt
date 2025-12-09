package com.w3n9.chengying.data.source

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShizukuInputManager @Inject constructor() {

    private val SHIZUKU_REQUEST_CODE = 1001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkPermission()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Timber.i("Shizuku Permission Granted")
            } else {
                Timber.e("Shizuku Permission Denied")
            }
        }
    }

    init {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        if (Shizuku.pingBinder()) {
            checkPermission()
        }
    }

    private fun checkPermission() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    @SuppressLint("DiscouragedPrivateApi", "BlockedPrivateApi")
    fun injectMotionEvent(action: Int, x: Float, y: Float, displayId: Int) {
        // No-op for direct shell implementation
    }

    suspend fun injectClick(x: Float, y: Float, displayId: Int) = withContext(Dispatchers.IO) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val command = "input -d $displayId tap $x $y"
            Timber.d("Executing shell command: $command")
            
            try {
                // Use reflection to call Shizuku.newProcess since it might be private in 13.1.5
                val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                    "newProcess", 
                    Array<String>::class.java, 
                    Array<String>::class.java, 
                    String::class.java
                )
                newProcessMethod.isAccessible = true
                
                val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                process.waitFor()
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute shell command via reflection")
            }
        } else {
            Timber.w("Shizuku permission not granted for shell injection")
        }
    }
}
