package com.reps.app.feature.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.components.RepsBottomSheet
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.UnitSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddWeightSheet(
    units: UnitSystem,
    currentWeightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val unitLabel = stringResource(if (units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)

    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.progress_add_weight)) {
        RepsTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = currentWeightKg?.let { UnitConverter.formatWeight(it, units) } ?: "0.0",
            label = "${stringResource(R.string.weight_label)} ($unitLabel)",
            keyboardType = KeyboardType.Decimal,
        )
        RepsButton(
            text = stringResource(R.string.progress_save),
            onClick = {
                val typed = text.toDoubleOrNull() ?: return@RepsButton
                onSave(UnitConverter.weightToKg(typed, units))
            },
            enabled = text.toDoubleOrNull() != null && (text.toDoubleOrNull() ?: 0.0) > 0.0,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}
