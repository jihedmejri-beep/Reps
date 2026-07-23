package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.theme.RepsWeightDown
import com.reps.app.core.theme.RepsWeightUp
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.UnitSystem
import kotlin.math.abs

/**
 * Compact current-weight card for Home: the latest reading plus the change
 * against last week.
 *
 * @param deltaKg change vs. last week, or null when there is no earlier
 *   reading to compare against.
 */
@Composable
fun WeightWidget(
    weightKg: Double?,
    deltaKg: Double?,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(RepsTheme.dimens.cardPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.MonitorWeight,
                contentDescription = null,
                tint = RepsTheme.colors.textSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(R.string.weight_label),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
            )
        }

        if (weightKg == null) {
            Text(
                text = stringResource(R.string.weight_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = RepsTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
            return@Column
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            Text(
                text = UnitConverter.formatWeight(weightKg, units),
                style = RepsTheme.textStyles.statValue,
                color = RepsTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(
                    if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        deltaKg?.let { WeightDelta(it, units) }
    }
}

@Composable
private fun WeightDelta(deltaKg: Double, units: UnitSystem) {
    // Direction is neutral information: down is not "good" unless the user is
    // cutting, so this only conveys which way it moved.
    val gained = deltaKg > 0
    val tint = if (gained) RepsWeightUp else RepsWeightDown
    val magnitude = UnitConverter.formatWeight(abs(deltaKg), units)
    val unitLabel = stringResource(
        if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        Icon(
            imageVector = if (gained) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(
                if (gained) R.string.weight_delta_up else R.string.weight_delta_down,
                "$magnitude $unitLabel",
            ),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}
