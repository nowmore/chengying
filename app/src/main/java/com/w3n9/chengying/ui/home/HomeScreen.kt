package com.w3n9.chengying.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.w3n9.chengying.MainActivity
import com.w3n9.chengying.core.common.UiState
import com.w3n9.chengying.domain.model.DisplayMode
import com.w3n9.chengying.domain.model.ExternalDisplay

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val touchpadModeActive by viewModel.touchpadModeActive.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle(initialValue = null)
    val appIsLaunched by viewModel.appIsLaunched.collectAsStateWithLifecycle()

    val context = LocalContext.current
    
    LaunchedEffect(events) {
        val event = events
        if (event != null) {
            when (event) {
                is HomeEvent.StartDesktopMode -> {
                    val activity = context.findActivity() as? MainActivity
                    if (activity != null) {
                        if (event.mode == DisplayMode.DESKTOP) {
                            val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                            val targetDisplay = displayManager.getDisplay(event.display.id)
                            if (targetDisplay != null) {
                                activity.presentationRepository.showPresentation(targetDisplay)
                                activity.enterTouchpadMode()
                            } 
                        } else {
                            activity.presentationRepository.dismissPresentation()
                            activity.exitTouchpadMode()
                        }
                    } 
                }
                is HomeEvent.StopDesktopMode -> {
                    val activity = context.findActivity() as? MainActivity
                    if (activity != null) {
                        // Cursor overlay is hidden by ViewModel via CursorRepository
                        // Dismiss presentation
                        activity.presentationRepository.dismissPresentation()
                        
                        // Exit touchpad mode
                        activity.exitTouchpadMode()
                    }
                }
            }
            viewModel.consumeEvent()
        }
    }

    AnimatedContent(
        targetState = touchpadModeActive,
        transitionSpec = {
            if (targetState) {
                slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + fadeOut()
            } else {
                slideInVertically { height -> -height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
            }.using(
                SizeTransform(clip = false)
            )
        },
        label = "ScreenModeAnimation"
    ) { targetState ->
        if (targetState) {
            TouchpadScreen(
                viewModel = viewModel,
                appIsLaunched = appIsLaunched
            )
        } else {
            MainControlScreen(
                uiState = uiState,
                onStartSession = viewModel::startSession
            )
        }
    }
}

@Composable
fun TouchpadScreen(
    viewModel: HomeViewModel,
    appIsLaunched: Boolean
) {
    var isScreenSaverActive by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Screen saver timer - check every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            val currentTime = System.currentTimeMillis()
            val idleTime = currentTime - lastInteractionTime
            
            // Activate screen saver using shared timeout configuration
            if (idleTime >= com.w3n9.chengying.core.config.ScreenSaverConfig.TIMEOUT_MS && !isScreenSaverActive) {
                isScreenSaverActive = true
            }
        }
    }
    
    val resetScreenSaver = {
        lastInteractionTime = System.currentTimeMillis()
        if (isScreenSaverActive) {
            isScreenSaverActive = false
        }
    }
    
    // Cursor position for two-finger swipe start point
    val cursorState by viewModel.cursorState.collectAsStateWithLifecycle()
    
    // Track touch state
    var primaryPointerId by remember { mutableIntStateOf(-1) }
    var primaryTouchDownTime by remember { mutableLongStateOf(0L) }
    var isTwoFingerMode by remember { mutableStateOf(false) }
    var twoFingerSwipeStartX by remember { mutableFloatStateOf(0f) }
    var twoFingerSwipeStartY by remember { mutableFloatStateOf(0f) }
    var lastSecondFingerX by remember { mutableFloatStateOf(0f) }
    var lastSecondFingerY by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.filter { it.pressed }
                        
                        resetScreenSaver()
                        
                        when (pointers.size) {
                            0 -> {
                                // All fingers lifted
                                if (!isTwoFingerMode && primaryPointerId != -1) {
                                    // Single finger lifted - check for tap
                                    val touchDuration = System.currentTimeMillis() - primaryTouchDownTime
                                    if (touchDuration < 200) {
                                        viewModel.onTouchpadClick()
                                    }
                                }
                                primaryPointerId = -1
                                isTwoFingerMode = false
                            }
                            1 -> {
                                val pointer = pointers.first()
                                if (primaryPointerId == -1) {
                                    // First touch
                                    primaryPointerId = pointer.id.value.toInt()
                                    primaryTouchDownTime = System.currentTimeMillis()
                                } else if (!isTwoFingerMode) {
                                    // Single finger movement - pan cursor
                                    val change = pointer.positionChange()
                                    if (change.x != 0f || change.y != 0f) {
                                        viewModel.onTouchpadPan(change.x, change.y)
                                    }
                                }
                                pointer.consume()
                            }
                            else -> {
                                // Two or more fingers
                                if (!isTwoFingerMode) {
                                    // Entering two-finger mode
                                    isTwoFingerMode = true
                                    twoFingerSwipeStartX = pointers[1].position.x
                                    twoFingerSwipeStartY = pointers[1].position.y
                                    lastSecondFingerX = twoFingerSwipeStartX
                                    lastSecondFingerY = twoFingerSwipeStartY
                                } else {
                                    // Track second finger movement and inject swipe continuously
                                    val secondFinger = pointers.getOrNull(1)
                                    if (secondFinger != null) {
                                        val newX = secondFinger.position.x
                                        val newY = secondFinger.position.y
                                        val deltaX = newX - lastSecondFingerX
                                        val deltaY = newY - lastSecondFingerY
                                        
                                        // Trigger swipe if there's significant movement
                                        if (kotlin.math.abs(deltaX) > 5 || kotlin.math.abs(deltaY) > 5) {
                                            viewModel.onTwoFingerSwipe(
                                                cursorState.x, cursorState.y,
                                                deltaX * 3, deltaY * 3  // Scale for more responsive swipe
                                            )
                                            lastSecondFingerX = newX
                                            lastSecondFingerY = newY
                                        }
                                    }
                                }
                                pointers.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
    ) {
        // Show controls only when screen saver is not active
        if (!isScreenSaverActive) {
            // Back button at top-left
            FloatingActionButton(
                onClick = { 
                    resetScreenSaver()
                    viewModel.stopDesktopMode() 
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 32.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            
            // Bottom controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Home button (always visible)
                FloatingActionButton(
                    onClick = { 
                        resetScreenSaver()
                        viewModel.onHomeClicked() 
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
                
                // Task Switcher button (always visible)
                FloatingActionButton(
                    onClick = { 
                        resetScreenSaver()
                        viewModel.onToggleTaskSwitcher() 
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Task Switcher")
                }

                // Close button (visible only when an app is launched)
                if (appIsLaunched) {
                    // Back button
                    FloatingActionButton(
                        onClick = { 
                            resetScreenSaver()
                            viewModel.onBackClicked() 
                        },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    
                    // Close button
                    FloatingActionButton(
                        onClick = { 
                            resetScreenSaver()
                            viewModel.onCloseAppClicked() 
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close App", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(
    uiState: UiState<List<ExternalDisplay>>,
    onStartSession: (ExternalDisplay) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chengying", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Success -> {
                    if (uiState.data.isEmpty()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            InfoMessageCard(
                                icon = Icons.Default.Warning,
                                title = "No Display Found",
                                message = "Please connect an external display via USB-C or Miracast."
                            )
                        }
                    } else {
                        DisplayList(displays = uiState.data, onStartSession = onStartSession)
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        AccessibilityServiceStatus()
                        
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        InfoMessageCard(
                            icon = Icons.Default.Warning,
                            title = "Error",
                            message = uiState.message
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityServiceStatus() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State to track accessibility service status
    var isEnabled by remember { mutableStateOf(com.w3n9.chengying.service.ChengyingAccessibilityService.isEnabled()) }
    
    // Update status when screen resumes
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isEnabled = com.w3n9.chengying.service.ChengyingAccessibilityService.isEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Accessibility Service",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isEnabled) "Enabled" else "Disabled - Required for touch input",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (!isEnabled) {
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text("Settings")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Enabled",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun DisplayList(displays: List<ExternalDisplay>, onStartSession: (ExternalDisplay) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(displays) { display ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Tv, contentDescription = "Display", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = display.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${display.width} x ${display.height}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onStartSession(display) },
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

@Composable
fun InfoMessageCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
