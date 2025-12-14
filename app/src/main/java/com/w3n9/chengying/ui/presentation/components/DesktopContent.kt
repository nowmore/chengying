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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.w3n9.chengying.domain.model.AppInfo

@Composable
fun DesktopContent(
    apps: List<AppInfo>,
    onAppBoundsChanged: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
                    DesktopAppIcon(
                        app = app,
                        onBoundsChanged = { bounds -> onAppBoundsChanged(app.packageName, bounds) }
                    )
                }
            }
        }
    }
}

@Composable
fun DesktopBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
                )
            )
    )
}

@Composable
private fun DesktopAppIcon(
    app: AppInfo,
    onBoundsChanged: (Rect) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(8.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
            }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let { drawable ->
                Image(
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
            overflow = TextOverflow.Ellipsis
        )
    }
}
