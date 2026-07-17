package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme

/**
 * The green quote card pinned near the bottom of Home. The quote rotates daily
 * from a stored list; picking which one is the caller's job.
 */
@Composable
fun MotivationQuoteCard(
    quote: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(RepsGreen),
    ) {
        // matchParentSize keeps the oversized glyph from driving the card's
        // height - it is decoration, so the text column alone sets the size.
        Box(Modifier.matchParentSize().clipToBounds()) {
            Text(
                text = "”",
                fontSize = 150.sp,
                color = RepsOnGreen.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp, y = (-52).dp),
            )
        }
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.home_daily_motivation).uppercase(),
                style = RepsTheme.textStyles.eyebrow,
                color = RepsOnGreen.copy(alpha = 0.65f),
            )
            Text(
                text = quote,
                style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                color = RepsOnGreen,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
