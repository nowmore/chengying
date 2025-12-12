package com.w3n9.chengying

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.w3n9.chengying.domain.repository.PresentationRepository
import com.w3n9.chengying.ui.home.HomeScreen
import com.w3n9.chengying.ui.theme.ChengyingTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var presentationRepository: PresentationRepository

    private var originalBrightness: Float = -1f

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Timber.i("Wallpaper permission granted.")
            // Optional: Can trigger a refresh of the presentation if it's already visible
        } else {
            Timber.w("Wallpaper permission denied.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Timber.i("MainActivity onCreate, savedInstanceState=${if (savedInstanceState == null) "null" else "not null"}")
        
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

        // Request wallpaper permission on create
        requestWallpaperPermission()
    }

    private fun requestWallpaperPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
                Timber.d("Wallpaper permission already granted.")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                // Optional: Show a rationale to the user
                Timber.i("Showing rationale for wallpaper permission.")
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            else -> {
                // Directly request the permission
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("MainActivity onDestroy, isFinishing=$isFinishing")
        // Dismiss presentation to restore mirror mode when app is killed
        presentationRepository.dismissPresentation()
        unregisterReceiver(displayChangeReceiver)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.i("MainActivity onConfigurationChanged: orientation=${newConfig.orientation}")
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            hideSystemUI()
        }
    }

    fun enterTouchpadMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()
        dimScreen()
    }

    fun exitTouchpadMode() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showSystemUI()
        restoreScreenBrightness()
    }

    private fun dimScreen() {
        window.attributes = window.attributes.apply {
            originalBrightness = screenBrightness
            screenBrightness = 0.01f
        }
    }

    private fun restoreScreenBrightness() {
        window.attributes = window.attributes.apply {
            screenBrightness = originalBrightness
        }
    }

    private fun hideSystemUI() {
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
