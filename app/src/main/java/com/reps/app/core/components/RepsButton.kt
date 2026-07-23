package com.reps.app.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme

/**
 * Primary call to action: green, pill-shaped, full width by default, sitting on
 * a soft green glow as in the reference designs.
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
    trailingIcon: Painter? = null,
) {
    val height = RepsTheme.dimens.buttonHeight
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
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier
            .fillMaxWidth()
            // Coloured shadows need API 28; below that this degrades to the
            // platform's neutral shadow, which is an acceptable fallback.
            .shadow(
                elevation = if (enabled && !loading) 16.dp else 0.dp,
                shape = PillShape,
                ambientColor = RepsGreen,
                spotColor = RepsGreen,
            )
            // heightIn rather than height: with a large system font scale the
            // label must be able to push the button taller instead of clipping.
            .heightIn(min = height),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = RepsOnGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            ButtonContent(text, leadingIcon, trailingIcon)
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
    /** Brand marks such as the Google G must keep their own colours. */
    tintLeadingIcon: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(1.dp, RepsTheme.colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = RepsTheme.colors.textPrimary,
            disabledContentColor = RepsTheme.colors.textTertiary,
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = RepsTheme.dimens.buttonHeight),
    ) {
        ButtonContent(
            text = text,
            leadingIcon = leadingIcon,
            trailingIcon = null,
            tintLeadingIcon = tintLeadingIcon,
        )
    }
}

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: Painter?,
    trailingIcon: Painter?,
    tintLeadingIcon: Boolean = true,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon?.let {
            Icon(
                painter = it,
                contentDescription = null,
                tint = if (tintLeadingIcon) androidx.compose.material3.LocalContentColor.current else Color.Unspecified,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            style = RepsTheme.textStyles.buttonLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingIcon?.let {
            Icon(painter = it, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
