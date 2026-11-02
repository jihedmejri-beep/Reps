package com.reps.app.feature.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Height
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsListRow
import com.reps.app.core.components.SectionHeader
import com.reps.app.core.components.StatCard
import com.reps.app.core.locale.AppLocale
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.util.BmiCalculator
import com.reps.app.core.util.BmrCalculator
import com.reps.app.core.util.UnitConverter
import com.reps.app.domain.model.Goal
import com.reps.app.domain.model.Sex
import com.reps.app.domain.model.ThemeMode
import com.reps.app.domain.model.UnitSystem
import com.reps.app.domain.model.User
import com.reps.app.feature.progress.AddWeightSheet
import com.reps.app.navigation.OnTabReselected
import com.reps.app.navigation.Routes
import com.reps.app.navigation.navBarClearance
import java.util.Locale
import kotlin.math.roundToInt

internal enum class ProfileField { HEIGHT, BIRTH_DATE, ACTIVITY, SEX, GOAL, LANGUAGE }

@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    OnTabReselected(Routes.PROFILE) { listState.animateScrollToItem(0) }

    var editingField by remember { mutableStateOf<ProfileField?>(null) }
    var showAddWeight by remember { mutableStateOf(false) }
    var showAccountInfo by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showMetricsInfo by remember { mutableStateOf(false) }

    // Android 13+ gates notifications behind a runtime permission. The toggle
    // still flips either way - the reminder worker stays quiet if it was denied.
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val requestNotificationPermissionIfNeeded: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val user = state.user
    val dimens = RepsTheme.dimens

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(RepsTheme.colors.background).statusBarsPadding(),
        contentPadding = PaddingValues(
            start = dimens.screenPadding,
            end = dimens.screenPadding,
            bottom = navBarClearance(),
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.sectionGap),
    ) {
        item {
            SectionHeader(
                eyebrow = stringResource(R.string.profile_eyebrow),
                title = stringResource(R.string.profile_title),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (user != null) {
            item { ProfileHero(user) }
            item {
                DetailsCard(
                    user = user,
                    currentWeightKg = state.currentWeightKg,
                    onEditHeight = { editingField = ProfileField.HEIGHT },
                    onEditWeight = { showAddWeight = true },
                    onEditBirthDate = { editingField = ProfileField.BIRTH_DATE },
                    onEditActivity = { editingField = ProfileField.ACTIVITY },
                    onEditSex = { editingField = ProfileField.SEX },
                    onEditGoal = { editingField = ProfileField.GOAL },
                )
            }
            item {
                val weightKg = state.currentWeightKg
                if (user.hasBodyMetrics && weightKg != null) {
                    BodyMetricsCard(user, weightKg, onInfo = { showMetricsInfo = true })
                } else {
                    Text(
                        text = stringResource(R.string.progress_bmi_needs_profile),
                        style = MaterialTheme.typography.bodySmall,
                        color = RepsTheme.colors.textTertiary,
                    )
                }
            }
            item {
                PreferencesCard(
                    user = user,
                    notificationsEnabled = state.notificationsEnabled,
                    themeMode = state.themeMode,
                    onToggleUnits = { viewModel.updateUnits(if (user.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC) },
                    onToggleNotifications = {
                        if (!state.notificationsEnabled) requestNotificationPermissionIfNeeded()
                        viewModel.toggleNotifications()
                    },
                    onEditTheme = { showThemePicker = true },
                    onEditLanguage = { editingField = ProfileField.LANGUAGE },
                    onAccountSettings = { showAccountInfo = true },
                )
            }
            item { CreditsCard() }
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
                        .padding(horizontal = dimens.cardPadding),
                ) {
                    RepsListRow(
                        label = stringResource(R.string.profile_sign_out),
                        danger = true,
                        onClick = { showSignOutConfirm = true },
                        showChevron = false,
                    )
                }
            }
        }
    }

    if (user != null) {
        when (editingField) {
            ProfileField.HEIGHT -> HeightEditSheet(user, onDismiss = { editingField = null }, onSave = { viewModel.updateHeightCm(it); editingField = null })
            ProfileField.BIRTH_DATE -> BirthDateSheet(user, onDismiss = { editingField = null }, onSave = { viewModel.updateBirthDate(it); editingField = null })
            ProfileField.ACTIVITY -> ActivityPickerSheet(user, onDismiss = { editingField = null }, onPick = { viewModel.updateActivity(it); editingField = null })
            ProfileField.SEX -> SexPickerSheet(user, onDismiss = { editingField = null }, onPick = { viewModel.updateSex(it); editingField = null })
            ProfileField.GOAL -> GoalPickerSheet(user, onDismiss = { editingField = null }, onPick = { viewModel.updateGoal(it); editingField = null })
            ProfileField.LANGUAGE -> LanguagePickerSheet(user, onDismiss = { editingField = null }, onPick = {
                editingField = null
                viewModel.updateLanguage(it)
                AppLocale.apply(context, it)
            })
            null -> Unit
        }

        if (showAccountInfo) {
            AccountSettingsSheet(user, onDismiss = { showAccountInfo = false })
        }

        if (showAddWeight) {
            AddWeightSheet(
                units = user.units,
                currentWeightKg = state.currentWeightKg,
                onDismiss = { showAddWeight = false },
                onSave = { kg -> viewModel.addWeight(kg); showAddWeight = false },
            )
        }
    }

    if (showThemePicker) {
        ThemePickerSheet(
            selected = state.themeMode,
            onDismiss = { showThemePicker = false },
            onPick = { viewModel.updateThemeMode(it); showThemePicker = false },
        )
    }

    if (showMetricsInfo) {
        BodyMetricsInfoSheet(onDismiss = { showMetricsInfo = false })
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            containerColor = RepsTheme.colors.surfaceElevated,
            title = { Text(stringResource(R.string.profile_sign_out_confirm_title), color = RepsTheme.colors.textPrimary) },
            text = { Text(stringResource(R.string.profile_sign_out_confirm_body), color = RepsTheme.colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    viewModel.signOut()
                    onSignedOut()
                }) { Text(stringResource(R.string.profile_sign_out), color = RepsError) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(stringResource(R.string.workouts_session_keep_training), color = RepsTheme.colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun ProfileHero(user: User) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(RepsTheme.dimens.avatarSize * 1.6f).background(RepsGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = user.name.firstOrNull()?.uppercase().orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                color = RepsOnGreen,
            )
        }
        Text(user.name, style = MaterialTheme.typography.headlineSmall, color = RepsTheme.colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
        Text(user.email, style = MaterialTheme.typography.bodySmall, color = RepsTheme.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DetailsCard(
    user: User,
    currentWeightKg: Double?,
    onEditHeight: () -> Unit,
    onEditWeight: () -> Unit,
    onEditBirthDate: () -> Unit,
    onEditActivity: () -> Unit,
    onEditSex: () -> Unit,
    onEditGoal: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(horizontal = RepsTheme.dimens.cardPadding)) {
        Text(
            text = stringResource(R.string.profile_your_details),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
        RepsListRow(
            label = stringResource(R.string.profile_height),
            icon = Icons.Outlined.Height,
            value = user.heightCm?.let { UnitConverter.formatHeight(it, user.units) } ?: "—",
            onClick = onEditHeight,
        )
        RepsListRow(
            label = stringResource(R.string.profile_weight),
            icon = Icons.Outlined.MonitorWeight,
            value = currentWeightKg?.let {
                "${UnitConverter.formatWeight(it, user.units)} ${stringResource(if (user.units == UnitSystem.METRIC) R.string.workouts_kg else R.string.workouts_lb)}"
            } ?: "—",
            onClick = onEditWeight,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_birth_date),
            icon = Icons.Outlined.CalendarMonth,
            value = when {
                user.birthDate != null -> stringResource(R.string.profile_age_value, user.ageOn() ?: 0)
                user.age != null -> stringResource(R.string.profile_age_legacy, user.age)
                else -> "—"
            },
            onClick = onEditBirthDate,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_activity),
            icon = Icons.AutoMirrored.Outlined.DirectionsRun,
            value = user.activityLevel?.let { stringResource(activityLabelRes(it)) } ?: "—",
            onClick = onEditActivity,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_sex),
            icon = Icons.Outlined.Person,
            value = user.sex?.let { stringResource(if (it == Sex.MALE) R.string.profile_sex_male else R.string.profile_sex_female) } ?: "—",
            onClick = onEditSex,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_goal),
            icon = Icons.Outlined.Flag,
            value = stringResource(goalLabelRes(user.goal)),
            onClick = onEditGoal,
            showDivider = true,
        )
    }
}

@Composable
private fun BodyMetricsCard(user: User, currentWeightKg: Double, onInfo: () -> Unit) {
    val bmi = BmiCalculator.calculate(currentWeightKg, user.heightCm!!)
    val bmr = BmrCalculator.calculate(user.sex!!, currentWeightKg, user.heightCm, user.ageOn()!!)
    val tdee = user.activityLevel?.let { BmrCalculator.tdee(bmr, it) }
    val kcalPerDay = stringResource(R.string.metrics_kcal_per_day)
    Column {
    // IntrinsicSize.Min lets the shorter card stretch to the taller one: BMI
    // carries a category line ("Healthy") that BMR has no equivalent for, so
    // without this the two tiles are the same width but not the same height.
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.height(IntrinsicSize.Min),
    ) {
        StatCard(
            icon = Icons.Outlined.Straighten,
            label = stringResource(R.string.progress_bmi),
            value = String.format(Locale.US, "%.1f", bmi),
            deltaText = stringResource(BmiCalculator.categorise(bmi).labelRes),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StatCard(
            icon = Icons.Outlined.LocalFireDepartment,
            label = stringResource(R.string.progress_bmr),
            value = bmr.roundToInt().toString(),
            unit = kcalPerDay,
            deltaText = stringResource(R.string.metrics_bmr_caption),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
    StatCard(
        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
        label = stringResource(R.string.metrics_tdee),
        value = tdee?.roundToInt()?.toString() ?: "—",
        unit = kcalPerDay.takeIf { tdee != null },
        deltaText = stringResource(if (tdee != null) R.string.metrics_tdee_caption else R.string.metrics_tdee_needs_activity),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    RepsListRow(
        label = stringResource(R.string.metrics_how_calculated),
        sub = stringResource(R.string.metrics_disclaimer_short),
        icon = Icons.Outlined.Info,
        onClick = onInfo,
    )
    }
}

@Composable
private fun PreferencesCard(
    user: User,
    notificationsEnabled: Boolean,
    themeMode: ThemeMode,
    onToggleUnits: () -> Unit,
    onToggleNotifications: () -> Unit,
    onEditTheme: () -> Unit,
    onEditLanguage: () -> Unit,
    onAccountSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(RepsTheme.colors.surface, MaterialTheme.shapes.large).padding(horizontal = RepsTheme.dimens.cardPadding)) {
        Text(
            text = stringResource(R.string.profile_preferences),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
        RepsListRow(
            label = stringResource(R.string.profile_units),
            icon = Icons.Outlined.MonitorWeight,
            sub = stringResource(if (user.units == UnitSystem.METRIC) R.string.profile_units_metric else R.string.profile_units_imperial),
            trailing = { RepsSwitch(checked = user.units == UnitSystem.IMPERIAL, onCheckedChange = { onToggleUnits() }) },
        )
        RepsListRow(
            label = stringResource(R.string.profile_notifications),
            icon = Icons.Outlined.Notifications,
            sub = stringResource(R.string.profile_notifications_sub),
            showDivider = true,
            trailing = { RepsSwitch(checked = notificationsEnabled, onCheckedChange = { onToggleNotifications() }) },
        )
        RepsListRow(
            label = stringResource(R.string.profile_theme),
            icon = Icons.Outlined.Tonality,
            value = stringResource(themeModeLabelRes(themeMode)),
            onClick = onEditTheme,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_language),
            icon = Icons.Outlined.Language,
            value = stringResource(languageLabelRes(user.language)),
            onClick = onEditLanguage,
            showDivider = true,
        )
        RepsListRow(
            label = stringResource(R.string.profile_settings),
            icon = Icons.Outlined.Settings,
            onClick = onAccountSettings,
            showDivider = true,
        )
    }
}

// The "Author" field of commons.wikimedia.org/wiki/File:Muscles_front_and_back.svg
// (CC BY-SA 4.0). A proper name, so it is not translated.
private const val MUSCLE_MAP_AUTHOR = "OpenStax, Tomáš Kebert & umimeto.org"

@Composable
private fun CreditsCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(RepsTheme.colors.surface, MaterialTheme.shapes.large)
            .padding(horizontal = RepsTheme.dimens.cardPadding, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_credits_title),
            style = MaterialTheme.typography.titleSmall,
            color = RepsTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.profile_credits_muscle_map, MUSCLE_MAP_AUTHOR),
            style = MaterialTheme.typography.bodySmall,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RepsSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = RepsGreen,
            checkedThumbColor = RepsOnGreen,
            uncheckedTrackColor = RepsTheme.colors.surfaceElevated,
            uncheckedBorderColor = RepsTheme.colors.outline,
            uncheckedThumbColor = RepsTheme.colors.textTertiary,
        ),
    )
}

private fun goalLabelRes(goal: Goal) = when (goal) {
    Goal.BULK -> R.string.profile_goal_bulk
    Goal.CUT -> R.string.profile_goal_cut
    Goal.MAINTAIN -> R.string.profile_goal_maintain
}

private fun languageLabelRes(language: com.reps.app.domain.model.AppLanguage) = when (language) {
    com.reps.app.domain.model.AppLanguage.ENGLISH -> R.string.language_english
    com.reps.app.domain.model.AppLanguage.ARABIC -> R.string.language_arabic
    com.reps.app.domain.model.AppLanguage.FRENCH -> R.string.language_french
}

internal fun themeModeLabelRes(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> R.string.profile_theme_system
    ThemeMode.LIGHT -> R.string.profile_theme_light
    ThemeMode.DARK -> R.string.profile_theme_dark
}
