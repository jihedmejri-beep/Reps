package com.reps.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.theme.RepsWeightDown
import com.reps.app.core.theme.RepsWeightUp

/**
 * Which way a [StatCard]'s delta points. Direction only, same as
 * [com.reps.app.core.components.WeightWidget] - up and down are not
 * inherently good or bad, so callers pick the label, this only picks the tint.
 */
enum class DeltaDirection { UP, DOWN, FLAT }

/**
 * The small metric tile used across Progress and Profile: an eyebrow-style
 * icon+label, a big value with an optional unit, and an optional trend line.
 * Two side by side fill a [RepsTheme]-spaced grid row.
 */
@Composable
fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    deltaText: String? = null,
    deltaDirection: DeltaDirection = DeltaDirection.FLAT,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.medium)
            .padding(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = RepsGreen, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = RepsTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge, color = RepsTheme.colors.textPrimary)
            unit?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
        deltaText?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                val tint = when (deltaDirection) {
                    DeltaDirection.UP -> RepsWeightUp
                    DeltaDirection.DOWN -> RepsWeightDown
                    DeltaDirection.FLAT -> RepsTheme.colors.textTertiary
                }
                if (deltaDirection != DeltaDirection.FLAT) {
                    Icon(
                        imageVector = if (deltaDirection == DeltaDirection.UP) {
                            Icons.Filled.ArrowUpward
                        } else {
                            Icons.Filled.ArrowDownward
                        },
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(10.dp),
                    )
                }
                Text(text = it, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    }
}
