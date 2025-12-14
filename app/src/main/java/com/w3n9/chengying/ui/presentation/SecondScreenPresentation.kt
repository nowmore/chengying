package com.w3n9.chengying.ui.presentation

import android.app.Presentation
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
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
import com.w3n9.chengying.data.repository.TaskRepositoryImpl
import com.w3n9.chengying.domain.repository.AppRepository
import com.w3n9.chengying.domain.repository.CursorRepository
import com.w3n9.chengying.domain.repository.TaskRepository

import com.w3n9.chengying.ui.presentation.components.DesktopBackground
import com.w3n9.chengying.ui.presentation.components.DesktopContent
import com.w3n9.chengying.ui.presentation.components.TaskSwitcherOverlay
import com.w3n9.chengying.ui.theme.ChengyingTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber

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
            (repo as? TaskRepositoryImpl)?.setTargetDisplayId(display.displayId)
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
            Timber.d("[SecondScreenPresentation::onCreate] Window configured")
        }

        savedStateRegistryController.performRestore(savedInstanceState)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondScreenPresentation)
            setViewTreeViewModelStoreOwner(this@SecondScreenPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondScreenPresentation)

            setContent {
                ChengyingTheme {
                    PresentationContent()
                }
            }
        }

        setContentView(composeView)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @Composable
    private fun PresentationContent() {
        val cursorState by cursorRepository.cursorState.collectAsStateWithLifecycle()
        val apps by appRepository.getInstalledApps().collectAsStateWithLifecycle(initialValue = emptyList())
        val isAppLaunched by cursorRepository.isAppLaunched.collectAsStateWithLifecycle()
        val isTaskSwitcherVisible by cursorRepository.isTaskSwitcherVisible.collectAsStateWithLifecycle()
        val isScreenSaverActive by cursorRepository.isScreenSaverActive.collectAsStateWithLifecycle()

        // Initialize screen saver timer
        LaunchedEffect(Unit) {
            cursorRepository.startScreenSaverTimer()
        }

        // Show cursor overlay via repository
        LaunchedEffect(Unit) {
            cursorRepository.showCursorOverlay(display.displayId)
            Timber.i("[SecondScreenPresentation] Cursor overlay shown")
        }

        // Update cursor position via repository
        LaunchedEffect(cursorState.x, cursorState.y, cursorState.isVisible, isScreenSaverActive) {
            cursorRepository.updateCursorOverlay()
        }

        // Toggle Presentation window visibility based on app state
        LaunchedEffect(isAppLaunched) {
            window?.apply {
                if (isAppLaunched) {
                    attributes = attributes?.apply {
                        alpha = 0f
                        flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    }
                    Timber.i("[SecondScreenPresentation] App launched - Presentation hidden")
                } else {
                    attributes = attributes?.apply {
                        alpha = 1f
                        flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    }
                    Timber.i("[SecondScreenPresentation] Desktop mode - Presentation visible")
                }
            }
        }

        // Handle click events
        LaunchedEffect(Unit) {
            cursorRepository.clickEvents.collectLatest {
                handleClick(
                    cursorX = cursorState.x,
                    cursorY = cursorState.y,
                    isTaskSwitcherVisible = isTaskSwitcherVisible,
                    isAppLaunched = isAppLaunched
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    cursorRepository.setBounds(size.width, size.height)
                }
        ) {
            if (!isAppLaunched) {
                DesktopBackground()
                DesktopContent(
                    apps = apps,
                    onAppBoundsChanged = { packageName, bounds ->
                        appIconBounds[packageName] = bounds
                    }
                )
            } else {
                Spacer(Modifier.fillMaxSize().background(Color.Transparent))
            }

            if (isTaskSwitcherVisible) {
                TaskSwitcherOverlay(
                    tasksFlow = taskRepository.getRecentTasks(display.displayId),
                    onTaskBoundsChanged = { taskId, bounds ->
                        taskSwitcherBounds[taskId] = bounds
                    }
                )
            }
        }
    }

    private suspend fun handleClick(
        cursorX: Float,
        cursorY: Float,
        isTaskSwitcherVisible: Boolean,
        isAppLaunched: Boolean
    ) {
        val cursorOffset = Offset(cursorX, cursorY)

        if (isTaskSwitcherVisible) {
            Timber.d("[SecondScreenPresentation::handleClick] TaskSwitcher click at: $cursorOffset")

            val clickedTask = taskSwitcherBounds.entries.find { (_, bounds) ->
                bounds.contains(cursorOffset)
            }?.key

            if (clickedTask != null) {
                Timber.i("[SecondScreenPresentation::handleClick] Switching to task: $clickedTask")
                taskRepository.switchToTask(clickedTask)
                cursorRepository.setAppLaunched(true)
                cursorRepository.toggleTaskSwitcher()
            } else {
                Timber.d("[SecondScreenPresentation::handleClick] No task clicked, closing TaskSwitcher")
                cursorRepository.toggleTaskSwitcher()
            }
        } else if (!isAppLaunched) {
            val clickedApp = appIconBounds.entries.find { (_, bounds) ->
                bounds.contains(cursorOffset)
            }?.key

            if (clickedApp != null) {
                Toast.makeText(context, "Launching $clickedApp", Toast.LENGTH_SHORT).show()
                when (val result = appRepository.launchApp(activityContext, clickedApp, display.displayId)) {
                    is com.w3n9.chengying.core.common.Result.Success -> {
                        Timber.i("[SecondScreenPresentation::handleClick] App launched successfully")
                    }
                    is com.w3n9.chengying.core.common.Result.Error -> {
                        Toast.makeText(
                            context,
                            "Failed to launch app: ${result.exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is com.w3n9.chengying.core.common.Result.Loading -> { /* no-op */ }
                }
            }
        }
    }

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
