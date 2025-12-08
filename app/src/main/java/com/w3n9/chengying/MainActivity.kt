package com.w3n9.chengying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.w3n9.chengying.domain.repository.PresentationRepository
import com.w3n9.chengying.ui.home.HomeScreen
import com.w3n9.chengying.ui.theme.ChengyingTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var presentationRepository: PresentationRepository

    private val displayChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.w3n9.chengying.DISPLAY_CONNECTION_CHANGED") {
                val connected = intent.getBooleanExtra("connected", false)
                if (connected) {
                    val displayId = intent.getIntExtra("displayId", -1)
                    if (displayId != -1) {
                        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
                        val presentationDisplay = displayManager.getDisplay(displayId)
                        if (presentationDisplay != null) {
                            presentationRepository.showPresentation(presentationDisplay)
                            enterTouchpadMode()
                        }
                    }
                } else {
                    presentationRepository.dismissPresentation()
                    exitTouchpadMode()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure we control the decor fits system windows manually
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        enableEdgeToEdge()
        setContent {
            ChengyingTheme {
                HomeScreen()
            }
        }
        
        val filter = IntentFilter("com.w3n9.chengying.DISPLAY_CONNECTION_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(displayChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(displayChangeReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(displayChangeReceiver)
    }
    
    // We need to re-apply immersive mode on window focus change because system UI might reappear
    // e.g. when pulling down notification shade and dismissing it
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            hideSystemUI()
        }
    }

    fun enterTouchpadMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()
    }

    fun exitTouchpadMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemUI()
    }

    private fun hideSystemUI() {
        // We need to set this false to allow drawing behind bars (and then hiding them)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
             controller.show(WindowInsetsCompat.Type.systemBars())
             controller.show(WindowInsetsCompat.Type.statusBars())
             controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
