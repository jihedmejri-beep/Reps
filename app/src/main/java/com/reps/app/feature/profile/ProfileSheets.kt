package com.reps.app.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.InputLimits
import com.reps.app.core.util.NumberInput
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.ActivityLevel
import com.reps.app.domain.model.AppLanguage
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.ThemeMode
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EditValueSheet(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    /** Null for anything that is not a plausible value; Save stays disabled until it is. */
    parse: (String) -> T?,
    invalidMessage: String,
    onSave: (T) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    var value by remember { mutableStateOf(initialValue) }
    val parsed = parse(value)
    RepsBottomSheet(onDismissRequest = onDismiss, title = title) {
        RepsTextField(
            value = value,
            onValueChange = { value = it },
            label = label,
            keyboardType = keyboardType,
            error = invalidMessage.takeIf { value.isNotBlank() && parsed == null },
        )
        RepsButton(
            text = stringResource(R.string.profile_save),
            onClick = { parsed?.let(onSave) },
            enabled = parsed != null,
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
        label = stringResource(R.string.profile_height_with_unit, stringResource(if (metric) R.string.profile_unit_cm else R.string.profile_unit_in)),
        initialValue = initial,
        onDismiss = onDismiss,
        parse = { text ->
            NumberInput.parseDecimal(text)
                ?.let { if (metric) it else UnitConverter.inchesToCm(it) }
                ?.takeIf { it in InputLimits.HEIGHT_CM }
        },
        invalidMessage = stringResource(R.string.profile_height_invalid),
        onSave = onSave,
    )
}

/**
 * Birth date rather than age: an age typed once goes stale on the next
 * birthday, and BMR with it. The picker is the platform one, so it follows the
 * app's language and layout direction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthDateSheet(user: User, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val earliest = today.minusYears(InputLimits.AGE_YEARS.last.toLong())
    val latest = today.minusYears(InputLimits.AGE_YEARS.first.toLong())
    val initial = user.birthDate ?: user.age?.let { today.minusYears(it.toLong()) } ?: latest.minusYears(12)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        yearRange = earliest.year..latest.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in earliest..latest
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onSave(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.profile_save), color = RepsGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.nutrition_delete_meal_cancel), color = RepsTheme.colors.textSecondary)
            }
        },
    ) {
        DatePicker(
            state = state,
            title = { Text(stringResource(R.string.profile_birth_date), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
internal fun ActivityPickerSheet(user: User, onDismiss: () -> Unit, onPick: (ActivityLevel) -> Unit) {
    PickerSheet(
        title = stringResource(R.string.profile_activity),
        options = ActivityLevel.entries.map { it to stringResource(activityLabelRes(it)) },
        selected = user.activityLevel,
        onDismiss = onDismiss,
        onPick = onPick,
    )
}

internal fun activityLabelRes(level: ActivityLevel) = when (level) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary
    ActivityLevel.LIGHT -> R.string.activity_light
    ActivityLevel.MODERATE -> R.string.activity_moderate
    ActivityLevel.ACTIVE -> R.string.activity_active
    ActivityLevel.VERY_ACTIVE -> R.string.activity_very_active
}

/** How BMI, BMR and TDEE are worked out, and what they are not. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyMetricsInfoSheet(onDismiss: () -> Unit) {
    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.metrics_info_title)) {
        listOf(
            R.string.metrics_info_bmi,
            R.string.metrics_info_bmi_ranges,
            R.string.metrics_info_bmr,
            R.string.metrics_info_tdee,
            R.string.metrics_disclaimer,
        ).forEach {
            Text(
                text = stringResource(it),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = RepsTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
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
internal fun ThemePickerSheet(selected: ThemeMode, onDismiss: () -> Unit, onPick: (ThemeMode) -> Unit) {
    PickerSheet(
        title = stringResource(R.string.profile_theme),
        options = listOf(
            ThemeMode.SYSTEM to stringResource(R.string.profile_theme_system),
            ThemeMode.LIGHT to stringResource(R.string.profile_theme_light),
            ThemeMode.DARK to stringResource(R.string.profile_theme_dark),
        ),
        selected = selected,
        onDismiss = onDismiss,
        onPick = onPick,
    )
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
internal fun AccountSettingsSheet(user: User, onDismiss: () -> Unit) {
    RepsBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.profile_settings)) {
        RepsListRow(label = stringResource(R.string.profile_account_email), value = user.email, showChevron = false)
        Text(
            text = stringResource(R.string.profile_account_local),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textSecondary,
        )
    }
}
