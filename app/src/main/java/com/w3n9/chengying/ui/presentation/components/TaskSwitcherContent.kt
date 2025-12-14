package com.w3n9.chengying.ui.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.w3n9.chengying.domain.model.TaskInfo
import com.w3n9.chengying.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

@Composable
fun TaskSwitcherOverlay(
    tasksFlow: Flow<List<TaskInfo>>,
    onTaskBoundsChanged: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    var tasks by remember { mutableStateOf<List<TaskInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        tasksFlow.collect { taskList ->
            tasks = taskList
            isLoading = false
        }
    }

    Box(
        modifier = modifier
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
                        TaskCard(
                            task = task,
                            onBoundsChanged = { bounds -> onTaskBoundsChanged(task.taskId, bounds) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskInfo,
    onBoundsChanged: (Rect) -> Unit
) {
    Card(
        modifier = Modifier
            .size(200.dp, 300.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
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
                Image(
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
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
