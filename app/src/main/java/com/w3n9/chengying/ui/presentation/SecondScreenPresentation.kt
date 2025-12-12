package com.w3n9.chengying.ui.presentation

import android.app.Presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
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
import com.w3n9.chengying.domain.model.TaskInfo
import com.w3n9.chengying.domain.repository.AppRepository
import com.w3n9.chengying.domain.repository.CursorRepository
import com.w3n9.chengying.domain.repository.TaskRepository
import com.w3n9.chengying.ui.theme.ChengyingTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import kotlin.math.roundToInt

class SecondScreenPresentation(
    private val activityContext: Context,
    private val display: Display
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
        fun taskRepository(): TaskRepository
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication(
            activityContext.applicationContext,
            PresentationEntryPoint::class.java
        )
    }

    private val cursorRepository by lazy { entryPoint.cursorRepository() }
    private val appRepository by lazy { entryPoint.appRepository() }
    private val taskRepository by lazy { 
        entryPoint.taskRepository().also { repo ->
            (repo as? com.w3n9.chengying.data.repository.TaskRepositoryImpl)?.setTargetDisplayId(display.displayId)
        }
    }

    private val appIconBounds = mutableMapOf<String, Rect>()
    private val taskSwitcherBounds = mutableMapOf<Int, Rect>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setFormat(PixelFormat.TRANSLUCENT)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            addFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
            
            Timber.d("[SecondScreenPresentation::onCreate] Window configured: NOT_FOCUSABLE | NOT_TOUCH_MODAL")
        }



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
                    val isTaskSwitcherVisible by cursorRepository.isTaskSwitcherVisible.collectAsStateWithLifecycle()
                    val isScreenSaverActive by cursorRepository.isScreenSaverActive.collectAsStateWithLifecycle()

                    // Start screen saver timer
                    LaunchedEffect(Unit) {
                        cursorRepository.startScreenSaverTimer()
                    }

                    // Show cursor overlay immediately
                    LaunchedEffect(Unit) {
                        val accessibilityService = com.w3n9.chengying.service.ChengyingAccessibilityService.getInstance()
                        accessibilityService?.showCursorOverlay(display.displayId)
                        Timber.i("[SecondScreenPresentation] Cursor overlay shown")
                    }
                    
                    // Update cursor position and visibility in accessibility overlay
                    LaunchedEffect(cursorState.x, cursorState.y, cursorState.isVisible, isScreenSaverActive) {
                        val accessibilityService = com.w3n9.chengying.service.ChengyingAccessibilityService.getInstance()
                        // Hide cursor when screen saver is active
                        val shouldShowCursor = cursorState.isVisible && !isScreenSaverActive
                        accessibilityService?.updateCursor(cursorState.x, cursorState.y, shouldShowCursor)
                    }
                    
                    // Hide/Show Presentation window based on app state
                    LaunchedEffect(isAppLaunched) {
                        window?.apply {
                            if (isAppLaunched) {
                                // App is running - hide Presentation window completely
                                attributes = attributes?.apply {
                                    alpha = 0f
                                    flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                }
                                Timber.i("[SecondScreenPresentation] App launched - Presentation hidden")
                            } else {
                                // Desktop mode - show Presentation window
                                attributes = attributes?.apply {
                                    alpha = 1f
                                    flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                                }
                                Timber.i("[SecondScreenPresentation] Desktop mode - Presentation visible")
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        cursorRepository.clickEvents.collectLatest {
                            if (isTaskSwitcherVisible) {
                                val cursorOffset = Offset(cursorState.x, cursorState.y)
                                Timber.d("[ClickEvent] TaskSwitcher click at: x=${cursorOffset.x}, y=${cursorOffset.y}")
                                Timber.d("[ClickEvent] Available task bounds: ${taskSwitcherBounds.size} tasks")
                                
                                val clickedTask = taskSwitcherBounds.entries.find { (taskId, bounds) ->
                                    val contains = bounds.contains(cursorOffset)
                                    Timber.v("[ClickEvent] Checking task $taskId: bounds=$bounds, contains=$contains")
                                    contains
                                }?.key
                                
                                if (clickedTask != null) {
                                    Timber.i("[ClickEvent] Switching to task: $clickedTask")
                                    taskRepository.switchToTask(clickedTask)
                                    cursorRepository.setAppLaunched(true)
                                    cursorRepository.toggleTaskSwitcher()
                                } else {
                                    Timber.d("[ClickEvent] No task clicked, closing TaskSwitcher")
                                    cursorRepository.toggleTaskSwitcher()
                                }
                            } else if (!isAppLaunched) {
                                val cursorOffset = Offset(cursorState.x, cursorState.y)
                                val clickedApp = appIconBounds.entries.find { (_, bounds) ->
                                    bounds.contains(cursorOffset)
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
                            .onSizeChanged { size ->
                                cursorRepository.setBounds(size.width, size.height)
                            }
                    ) {
                        // Layer 1: Content (Desktop or Transparent for App)
                        if (!isAppLaunched) {
                            // Show Desktop with gradient background
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
                                        )
                                    )
                            )
                            DesktopContent(apps)
                        } else {
                            // App is running, so our background is transparent
                            Spacer(Modifier.fillMaxSize().background(Color.Transparent))
                        }
                        
                        // Layer 2: Task Switcher (Overlay)
                        if (isTaskSwitcherVisible) {
                            TaskSwitcher(display.displayId)
                        }
                        
                        // Cursor is now rendered in accessibility overlay, not here
                    }
                }
            }
        }

        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @Composable
    private fun DesktopContent(apps: List<AppInfo>) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            if (apps.isEmpty()) {
                Text(
                    text = "Loading apps...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
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
                // App icon using rememberAsyncImagePainter or BitmapPainter
                app.icon?.let { drawable ->
                    androidx.compose.foundation.Image(
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = app.label,
                        modifier = Modifier.size(48.dp)
                    )
                } ?: Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )
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
    
    @Composable
    private fun TaskSwitcher(displayId: Int) {
        var tasks by remember { mutableStateOf<List<TaskInfo>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        
        LaunchedEffect(Unit) {
            taskRepository.getRecentTasks(displayId).collect { taskList ->
                tasks = taskList
                isLoading = false
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                tasks.isEmpty() -> {
                    Text(
                        text = "No recent tasks",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyRow(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(tasks) { task ->
                            TaskCard(task)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskCard(task: TaskInfo) {
        Card(
            modifier = Modifier
                .size(200.dp, 300.dp)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    taskSwitcherBounds[task.taskId] = bounds
                },
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                task.appIcon?.let { drawable ->
                    androidx.compose.foundation.Image(
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = task.appName,
                        modifier = Modifier.size(64.dp)
                    )
                } ?: Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = task.appName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
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
        Timber.i("[SecondScreenPresentation] onDetachedFromWindow - cleaning up")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        super.onDetachedFromWindow()
    }
    
    override fun dismiss() {
        Timber.i("[SecondScreenPresentation] dismiss called")
        super.dismiss()
    }
}


@Composable
private fun rememberDrawablePainter(drawable: Drawable): Painter {
    return remember(drawable) {
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            is AdaptiveIconDrawable -> {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 100
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 100
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
            else -> {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 100
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 100
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        }
        BitmapPainter(bitmap.asImageBitmap())
    }
}
