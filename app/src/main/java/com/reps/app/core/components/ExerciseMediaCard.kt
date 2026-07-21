package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextTertiary

/**
 * Reserved space for an exercise demonstration: a GIF today via Coil (which
 * decodes it like any other image), a short video later. Callers never touch
 * the media format - only this one component would change to add video, e.g.
 * by branching on the URL's extension into an ExoPlayer surface instead.
 */
@Composable
fun ExerciseMediaCard(
    mediaUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(MaterialTheme.shapes.large)
            .background(RepsSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaUrl.isNotBlank()) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = RepsTextTertiary,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}
