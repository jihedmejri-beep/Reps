package com.reps.app.core.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme

/**
 * An exercise demonstration: a photo or GIF, fetched from the catalogue's
 * remote media and never packaged in the APK.
 *
 * All four outcomes are real and all four are handled, because the data makes
 * them so: 564 of the catalogue's 828 exercises have no image at all, and the
 * ones that do depend on a network that may be absent. A failed image is never
 * allowed to imply a failed exercise - the rest of the screen is local and has
 * already rendered by the time this resolves.
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
            .background(RepsTheme.colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaUrl.isBlank()) {
            MediaPlaceholder(
                icon = Icons.Outlined.FitnessCenter,
                message = stringResource(R.string.exercise_media_none),
            )
            return@Box
        }

        var state by remember(mediaUrl) {
            mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
        }

        AsyncImage(
            model = mediaUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            onState = { state = it },
            modifier = Modifier.fillMaxSize(),
        )

        Crossfade(targetState = state, label = "exerciseMediaState") { current ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (current) {
                    is AsyncImagePainter.State.Loading ->
                        CircularProgressIndicator(
                            color = RepsGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    // Offline, a timeout, a 404, or an unsupported format all
                    // land here and all mean the same thing to the user.
                    is AsyncImagePainter.State.Error ->
                        MediaPlaceholder(
                            icon = Icons.Outlined.CloudOff,
                            message = stringResource(R.string.exercise_media_unavailable),
                        )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun MediaPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 24.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RepsTheme.colors.textTertiary,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
