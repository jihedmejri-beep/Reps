package com.reps.app.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsPasswordField
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.components.RepsBackButton
import com.reps.app.feature.auth.AuthFooterLink
import com.reps.app.feature.auth.AuthHeader
import com.reps.app.feature.auth.authContentWidth

@Composable
fun SignUpScreen(
    onSignedUp: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedUp) {
        if (state.signedUp) onSignedUp()
    }

    SignUpContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTermsChange = viewModel::onTermsChange,
        onSubmit = viewModel::signUp,
        onSignIn = onSignIn,
        onBack = onBack,
    )
}

@Composable
private fun SignUpContent(
    state: SignUpUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTermsChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val dimens = RepsTheme.dimens

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                // Width is capped and centred before the scroll, so on a wide
                // screen the form stays a readable column instead of stretching.
                .authContentWidth()
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            RepsBackButton(onClick = onBack)

            Spacer(Modifier.height(28.dp))
            AuthHeader(
                headline = stringResource(R.string.signup_headline),
                subtext = stringResource(R.string.signup_subtext),
            )

            Spacer(Modifier.height(28.dp))
            RepsTextField(
                value = state.name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.signup_name),
                leadingIcon = rememberVectorPainter(Icons.Outlined.PersonOutline),
                error = state.nameError?.let { stringResource(it) },
                enabled = !state.loading,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(12.dp))
            RepsTextField(
                value = state.email,
                onValueChange = onEmailChange,
                placeholder = stringResource(R.string.auth_email),
                leadingIcon = rememberVectorPainter(Icons.Outlined.Email),
                error = state.emailError?.let { stringResource(it) },
                enabled = !state.loading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(12.dp))
            RepsPasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.auth_password),
                leadingIcon = rememberVectorPainter(Icons.Outlined.Lock),
                error = state.passwordError?.let { stringResource(it) },
                enabled = !state.loading,
                imeAction = ImeAction.Done,
                onImeAction = onSubmit,
            )

            Spacer(Modifier.height(16.dp))
            TermsRow(accepted = state.termsAccepted, onChange = onTermsChange)

            state.formError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RepsError,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            RepsButton(
                text = stringResource(R.string.signup_create_account),
                onClick = onSubmit,
                // The brief requires accepting terms before this is usable.
                enabled = state.termsAccepted,
                loading = state.loading,
            )

            Spacer(Modifier.height(20.dp))
            AuthFooterLink(
                question = stringResource(R.string.signup_have_account),
                action = stringResource(R.string.signup_sign_in),
                onClick = onSignIn,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TermsRow(accepted: Boolean, onChange: (Boolean) -> Unit) {
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = RepsTheme.colors.textSecondary)) {
            append(stringResource(R.string.signup_terms_prefix))
            append(" ")
        }
        withStyle(SpanStyle(color = RepsGreen)) { append(stringResource(R.string.signup_terms)) }
        withStyle(SpanStyle(color = RepsTheme.colors.textSecondary)) {
            append(" ")
            append(stringResource(R.string.signup_terms_and))
            append(" ")
        }
        withStyle(SpanStyle(color = RepsGreen)) { append(stringResource(R.string.signup_privacy)) }
    }

    Row(
        // The whole row toggles, so the tap target is the text too, not just
        // the 20dp box. toggleable also gives it the right checkbox semantics.
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = accepted, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(
            checked = accepted,
            // Handled by the row's toggleable, which owns the semantics.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = RepsGreen,
                checkmarkColor = RepsOnGreen,
                uncheckedColor = RepsTheme.colors.outline,
            ),
            modifier = Modifier.size(20.dp),
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A, widthDp = 360, heightDp = 800)
@Composable
private fun SignUpPreview() {
    RepsTheme {
        SignUpContent(
            state = SignUpUiState(
                name = "Alex Rivera",
                email = "alex@reps.app",
                password = "supersecret",
                termsAccepted = true,
            ),
            onNameChange = {}, onEmailChange = {}, onPasswordChange = {},
            onTermsChange = {}, onSubmit = {}, onSignIn = {}, onBack = {},
        )
    }
}
