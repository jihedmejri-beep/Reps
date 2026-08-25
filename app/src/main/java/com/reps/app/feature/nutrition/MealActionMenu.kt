package com.reps.app.feature.nutrition

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsTheme

/** How dark the backdrop gets behind the menu - dim enough to read as "de-emphasized", not opaque. */
private const val ScrimAlpha = 0.5f
private const val EnterMs = 220
private const val ExitMs = 160

/**
 * The Instagram-style contextual menu that opens on a meal tap: a small rounded
 * surface floating over a dimmed, blurred background rather than a full-width
 * sheet. An outside tap or back dismisses it.
 *
 * The same menu serves both levels of the meal card - the caller supplies
 * [editLabel]/[deleteLabel] so an ingredient tap names the ingredient's actions
 * rather than the meal's, and wires [onEdit]/[onDelete] to the matching target.
 *
 * The blur itself is applied by the caller to the screen content behind this
 * composable - this only owns the scrim, the card, and the two actions.
 */
@Composable
fun MealActionMenu(
    visible: Boolean,
    editLabel: String,
    deleteLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(EnterMs)),
        exit = fadeOut(tween(ExitMs)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = ScrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.cd_close),
                    role = Role.Button,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .animateEnterExit(
                        enter = fadeIn(tween(EnterMs)) + scaleIn(initialScale = 0.85f, animationSpec = tween(EnterMs)),
                        exit = fadeOut(tween(ExitMs)) + scaleOut(targetScale = 0.85f, animationSpec = tween(ExitMs)),
                    )
                    .widthIn(min = 230.dp, max = 280.dp)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(RepsTheme.colors.surfaceElevated)
                    // Swallows taps landing on the card itself so they don't fall
                    // through to the scrim's dismiss handler.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                MealActionRow(
                    icon = Icons.Outlined.Edit,
                    label = editLabel,
                    onClick = onEdit,
                )
                HorizontalDivider(color = RepsTheme.colors.outline)
                MealActionRow(
                    icon = Icons.Outlined.DeleteOutline,
                    label = deleteLabel,
                    tint = RepsError,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun MealActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = RepsTheme.colors.textPrimary,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}
