package com.reps.app.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTheme

/**
 * The two-line header every non-home tab opens with: a small green uppercase
 * eyebrow over a large condensed title, e.g. WORKOUT LIBRARY / EXERCISES.
 */
@Composable
fun SectionHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) { heading() }) {
        Text(
            text = eyebrow.uppercase(),
            style = RepsTheme.textStyles.eyebrow,
            color = RepsGreen,
        )
        Text(
            text = title.uppercase(),
            style = RepsTheme.textStyles.sectionTitle,
            color = RepsTextPrimary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
