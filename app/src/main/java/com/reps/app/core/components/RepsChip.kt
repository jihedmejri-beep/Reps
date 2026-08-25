package com.reps.app.core.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color

/**
 * The filter/selector pill used for muscle groups, lift pickers and similar
 * single-choice rows - a plain outline that fills green when active.
 */
@Composable
fun RepsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = RepsGreen,
) {
    val background by animateColorAsState(
        targetValue = if (selected) tintColor else RepsTheme.colors.surface,
        animationSpec = tween(180),
        label = "chipBackground",
    )
    val border by animateColorAsState(
        targetValue = if (selected) tintColor else RepsTheme.colors.outline,
        animationSpec = tween(180),
        label = "chipBorder",
    )
    val content by animateColorAsState(
        targetValue = if (selected) RepsOnGreen else RepsTheme.colors.textSecondary,
        animationSpec = tween(180),
        label = "chipContent",
    )

    Box(
        modifier
            .height(34.dp)
            .background(background, PillShape)
            .border(1.dp, border, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}
