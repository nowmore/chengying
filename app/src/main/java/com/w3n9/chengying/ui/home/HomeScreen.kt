package com.w3n9.chengying.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.w3n9.chengying.MainActivity
import com.w3n9.chengying.core.common.UiState
import com.w3n9.chengying.domain.model.ExternalDisplay
import timber.log.Timber

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val touchpadModeActive by viewModel.touchpadModeActive.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle(initialValue = null)

    val context = LocalContext.current
    
    // Handle events from ViewModel
    LaunchedEffect(events) {
        val event = events
        if (event != null) {
            when (event) {
                is HomeEvent.StartDesktopMode -> {
                    Timber.d("Handling StartDesktopMode event for display ${event.display.id}")
                    val activity = context.findActivity() as? MainActivity
                    if (activity != null) {
                        val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                        val targetDisplay = displayManager.getDisplay(event.display.id)
                        if (targetDisplay != null) {
                            activity.presentationRepository.showPresentation(targetDisplay)
                            activity.enterTouchpadMode()
                        } else {
                            Timber.e("Target display ${event.display.id} not found")
                        }
                    } else {
                        Timber.e("MainActivity not found in context")
                    }
                }
                is HomeEvent.StopDesktopMode -> {
                    Timber.d("Handling StopDesktopMode event")
                    val activity = context.findActivity() as? MainActivity
                    if (activity != null) {
                        activity.presentationRepository.dismissPresentation()
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
            TouchpadScreen(viewModel)
        } else {
            MainControlScreen(uiState, onStartDesktop = {
                viewModel.startDesktopMode(it)
            })
        }
    }
}

@Composable
fun TouchpadScreen(viewModel: HomeViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    viewModel.onTouchpadPan(pan.x, pan.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.onTouchpadClick() }
                )
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(uiState: UiState<List<ExternalDisplay>>, onStartDesktop: (ExternalDisplay) -> Unit) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator()
                }
                is UiState.Success -> {
                    if (uiState.data.isEmpty()) {
                        InfoMessageCard(
                            icon = Icons.Default.Warning,
                            title = "No Display Found",
                            message = "Please connect an external display via USB-C or Miracast."
                        )
                    } else {
                        DisplayList(displays = uiState.data, onStartDesktop = onStartDesktop)
                    }
                }
                is UiState.Error -> {
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

@Composable
fun DisplayList(displays: List<ExternalDisplay>, onStartDesktop: (ExternalDisplay) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(displays) { display ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
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
                        onClick = { onStartDesktop(display) },
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text("Launch")
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
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
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
