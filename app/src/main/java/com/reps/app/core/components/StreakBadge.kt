package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.domain.model.Streak

private const val METER_BARS = 7

/**
 * Streak badge shown under the user's name once a streak exists.
 *
 * The meter reads as a rolling window of the last [METER_BARS] days rather than
 * the whole streak, so it stays legible whether the count is 3 or 300.
 */
@Composable
fun StreakBadge(
    streak: Streak,
    modifier: Modifier = Modifier,
) {
    val countText = stringResource(R.string.home_streak_count, streak.count)
    val nudge = stringResource(R.string.home_streak_nudge, streak.nextMilestone)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(RepsGreen.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            // Read as one sentence, not icon then number then seven bars.
            .clearAndSetSemantics { contentDescription = "$countText. $nudge" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalFireDepartment,
            contentDescription = null,
            tint = RepsGreen,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = countText,
                style = MaterialTheme.typography.titleMedium,
                color = RepsTheme.colors.textPrimary,
            )
            Text(
                text = nudge,
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        StreakMeter(streak.count)
    }
}

@Composable
private fun StreakMeter(count: Int, modifier: Modifier = Modifier) {
    val filled = count.coerceIn(0, METER_BARS)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(METER_BARS) { index ->
            // Bars step up in height so the meter ascends, echoing the REPS mark.
            MeterBar(filled = index < filled, height = 10.dp + (index * 2).dp)
        }
    }
}

@Composable
private fun MeterBar(filled: Boolean, height: Dp) {
    Box(
        Modifier
            .width(4.dp)
            .height(height)
            .background(
                color = if (filled) RepsGreen else RepsGreen.copy(alpha = 0.18f),
                shape = RoundedCornerShape(1.dp),
            ),
    )
}
