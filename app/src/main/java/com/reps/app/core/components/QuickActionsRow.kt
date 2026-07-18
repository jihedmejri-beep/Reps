package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsSurface
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTheme

/**
 * Start / Weight / Meal / Timer. Each is a shortcut into a destination the user
 * would otherwise have to reach through a tab.
 */
@Composable
fun QuickActionsRow(
    onStart: () -> Unit,
    onWeight: () -> Unit,
    onMeal: () -> Unit,
    onTimer: () -> Unit,
    modifier: Modifier = Modifier,
    startEnabled: Boolean = true,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(RepsSurface, MaterialTheme.shapes.large)
            .padding(RepsTheme.dimens.cardPadding),
    ) {
        Text(
            text = stringResource(R.string.home_quick_actions),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTextPrimary,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAction(
                icon = Icons.Filled.PlayArrow,
                label = stringResource(R.string.home_action_start),
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Outlined.MonitorWeight,
                label = stringResource(R.string.home_action_weight),
                onClick = onWeight,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Outlined.Restaurant,
                label = stringResource(R.string.home_action_meal),
                onClick = onMeal,
                modifier = Modifier.weight(1f),
            )
            QuickAction(
                icon = Icons.Outlined.Timer,
                label = stringResource(R.string.home_action_timer),
                onClick = onTimer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(RepsSurfaceElevated, MaterialTheme.shapes.medium)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    // The label below names the action, so the click target
                    // does not need to repeat it.
                    onClickLabel = label,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) RepsGreen else RepsGreen.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RepsTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
