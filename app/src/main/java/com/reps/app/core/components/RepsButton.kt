package com.reps.app.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme

private val ButtonHeight = 54.dp

/**
 * Primary call to action: green, pill-shaped, full width by default.
 *
 * While [loading] the label is swapped for a spinner and the button is
 * disabled, so a double tap cannot fire the action twice.
 */
@Composable
fun RepsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: Painter? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = RepsGreen,
            contentColor = RepsOnGreen,
            disabledContainerColor = RepsGreen.copy(alpha = 0.3f),
            disabledContentColor = RepsOnGreen.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = RepsOnGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text(text, style = RepsTheme.textStyles.buttonLabel)
            }
        }
    }
}

/** Secondary action: outlined, same footprint as [RepsButton]. */
@Composable
fun RepsOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, RepsOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = RepsTextPrimary,
            disabledContentColor = RepsTextTertiary,
        ),
        modifier = modifier.fillMaxWidth().height(ButtonHeight),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(text, style = RepsTheme.textStyles.buttonLabel)
        }
    }
}
