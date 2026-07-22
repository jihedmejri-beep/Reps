package com.reps.app.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.reps.app.core.components.RepsListRow
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditValueSheet(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    var value by remember { mutableStateOf(initialValue) }
    RepsBottomSheet(onDismissRequest = onDismiss, title = title) {
        RepsTextField(value = value, onValueChange = { value = it }, label = label, keyboardType = keyboardType)
        RepsButton(
            text = stringResource(R.string.profile_save),
            onClick = { if (value.isNotBlank()) onSave(value) },
            enabled = value.isNotBlank(),
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

@Composable
internal fun HeightEditSheet(user: User, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val metric = user.units == UnitSystem.METRIC
    val initial = user.heightCm?.let {
        (if (metric) it else UnitConverter.cmToInches(it)).roundToInt().toString()
    } ?: ""
    EditValueSheet(
        title = stringResource(R.string.profile_height),
        label = "${stringResource(R.string.profile_height)} (${if (metric) "cm" else "in"})",
        initialValue = initial,
        onDismiss = onDismiss,
        onSave = { text -> text.toDoubleOrNull()?.let { onSave(if (metric) it else UnitConverter.inchesToCm(it)) } },
    )
}

@Composable
internal fun AgeEditSheet(user: User, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    EditValueSheet(
        title = stringResource(R.string.profile_age),
        label = stringResource(R.string.profile_age),
        initialValue = user.age?.toString().orEmpty(),
        onDismiss = onDismiss,
        onSave = { text -> text.toIntOrNull()?.let(onSave) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    RepsBottomSheet(onDismissRequest = onDismiss, title = title) {
        Column {
            options.forEachIndexed { index, (value, label) ->
                RepsListRow(
                    label = label,
                    showDivider = index > 0,
                    showChevron = false,
                    onClick = { onPick(value) },
                    trailing = if (value == selected) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = RepsGreen) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
internal fun SexPickerSheet(user: User, onDismiss: () -> Unit, onPick: (Sex) -> Unit) {
    PickerSheet(
        title = stringResource(R.string.profile_sex),
        options = listOf(
            Sex.MALE to stringResource(R.string.profile_sex_male),
            Sex.FEMALE to stringResource(R.string.profile_sex_female),
        ),
        selected = user.sex,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

@Composable
internal fun GoalPickerSheet(user: User, onDismiss: () -> Unit, onPick: (Goal) -> Unit) {
    PickerSheet(
        title = stringResource(R.string.profile_goal),
        options = listOf(
            Goal.CUT to stringResource(R.string.profile_goal_cut),
            Goal.BULK to stringResource(R.string.profile_goal_bulk),
            Goal.MAINTAIN to stringResource(R.string.profile_goal_maintain),
        ),
        selected = user.goal,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

@Composable
internal fun LanguagePickerSheet(user: User, onDismiss: () -> Unit, onPick: (AppLanguage) -> Unit) {
    PickerSheet(
        title = stringResource(R.string.profile_language),
        options = listOf(
            AppLanguage.ENGLISH to stringResource(R.string.language_english),
            AppLanguage.ARABIC to stringResource(R.string.language_arabic),
            AppLanguage.FRENCH to stringResource(R.string.language_french),
        ),
        selected = user.language,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountSettingsSheet(onDismiss: () -> Unit) {
    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.profile_settings)) {
        Text(
            text = stringResource(R.string.profile_account_settings_body),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = RepsTextSecondary,
        )
    }
}
