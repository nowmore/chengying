package com.w3n9.chengying.ui.presentation

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.w3n9.chengying.domain.model.AppInfo
import com.w3n9.chengying.domain.repository.AppRepository
import com.w3n9.chengying.domain.repository.CursorRepository
import com.w3n9.chengying.ui.theme.ChengyingTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

class SecondScreenPresentation(
    private val activityContext: Context,
    display: Display
) : Presentation(activityContext, display), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PresentationEntryPoint {
        fun cursorRepository(): CursorRepository
        fun appRepository(): AppRepository
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            activityContext.applicationContext,
            PresentationEntryPoint::class.java
        )
    }

    private val cursorRepository by lazy { entryPoint.cursorRepository() }
    private val appRepository by lazy { entryPoint.appRepository() }

    private val appIconBounds = mutableMapOf<String, Rect>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        window?.setFormat(android.graphics.PixelFormat.TRANSLUCENT)

        savedStateRegistryController.performRestore(savedInstanceState)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondScreenPresentation)
            setViewTreeViewModelStoreOwner(this@SecondScreenPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondScreenPresentation)

            setContent {
                ChengyingTheme {
                    val cursorState by cursorRepository.cursorState.collectAsStateWithLifecycle()
                    val apps by appRepository.getInstalledApps().collectAsStateWithLifecycle(initialValue = emptyList())
                    val isAppLaunched by cursorRepository.isAppLaunched.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        cursorRepository.clickEvents.collectLatest {
                            if (!isAppLaunched) {
                                val clickedApp = appIconBounds.entries.find { (_, bounds) ->
                                    bounds.contains(Offset(cursorState.x, cursorState.y))
                                }?.key

                                if (clickedApp != null) {
                                    Toast.makeText(context, "Launching $clickedApp", Toast.LENGTH_SHORT).show()
                                    appRepository.launchApp(activityContext, clickedApp, display.displayId)
                                }
                            }
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isAppLaunched) {
                                    Modifier.background(Color.Transparent)
                                } else {
                                    Modifier.background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
                                        )
                                    )
                                }
                            )
                            .onSizeChanged { size ->
                                cursorRepository.setBounds(size.width, size.height)
                            }
                    ) {
                        if (!isAppLaunched) {
                            DesktopContent(apps)
                        } 
                        
                        if (cursorState.isVisible) {
                            Cursor(x = cursorState.x, y = cursorState.y)
                        }
                    }
                }
            }
        }

        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @Composable
    private fun DesktopContent(apps: List<AppInfo>) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .padding(bottom = 80.dp)
            ) {
                Text(
                    text = "Chengying OS",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                if (apps.isEmpty()) {
                    Text(
                        text = "Loading apps...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        items(apps) { app ->
                            DesktopAppIcon(app)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp),
                color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Start",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(24.dp))
                }
            }
        }
    }

    @Composable
    private fun DesktopAppIcon(app: AppInfo) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(8.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    appIconBounds[app.packageName] = bounds
                }
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = drawableToBitmap(app.icon)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.label,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Box(modifier = Modifier.size(48.dp).background(Color.Gray))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.label,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        if (drawable is AdaptiveIconDrawable) {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }
        try {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.takeIf { it > 0 } ?: 100,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 100,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    @Composable
    private fun Cursor(x: Float, y: Float) {
        Canvas(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(24.dp)
        ) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width * 0.4f, size.height * 0.7f)
                lineTo(size.width, size.height * 0.7f)
                close()
            }
            drawPath(
                path = path,
                color = Color.White
            )
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(width = 2f)
            )
        }
    }

    // Lifecycle event forwarding
    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        super.onDetachedFromWindow()
    }
}
