package com.reps.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTheme

/**
 * The big condensed headline plus its supporting line, shared by login and
 * sign up. The headline wraps to two lines exactly as in the reference designs.
 */
@Composable
fun AuthHeader(
    headline: String,
    subtext: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = headline.uppercase(),
            style = RepsTheme.textStyles.sectionTitle,
            color = RepsTextPrimary,
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTextSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Horizontal rule with a centred "OR", separating password from Google auth. */
@Composable
fun AuthOrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(RepsOutline))
        Text(
            text = stringResource(R.string.auth_or),
            style = MaterialTheme.typography.labelSmall,
            color = RepsTextSecondary,
        )
        Box(Modifier.weight(1f).height(1.dp).background(RepsOutline))
    }
}

/**
 * "New to REPS? Create account" - a muted question with a green action.
 *
 * Built as one annotated string so the two halves stay on the same baseline and
 * wrap together, which a Row of two Texts would not do reliably in Arabic.
 */
@Composable
fun AuthFooterLink(
    question: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = RepsTextSecondary)) { append(question) }
            append(" ")
            withStyle(SpanStyle(color = RepsGreen)) { append(action) }
        },
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/**
 * Caps how wide the form gets. On a tablet or unfolded foldable a full-width
 * login form looks broken; this keeps it a comfortable column.
 */
@Composable
fun Modifier.authContentWidth(): Modifier {
    val max = RepsTheme.dimens.maxContentWidth
    return if (max == Dp.Infinity) this else this.widthIn(max = max)
}
