package com.reps.app.feature.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.components.RepsPasswordField
import com.reps.app.core.components.RepsTextField
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsTheme
import com.reps.app.core.components.RepsBackButton
import com.reps.app.feature.auth.AuthFooterLink
import com.reps.app.feature.auth.AuthHeader
import com.reps.app.feature.auth.AuthOrDivider
import com.reps.app.feature.auth.authContentWidth

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onSignIn = viewModel::signIn,
        onForgotPassword = viewModel::sendPasswordReset,
        // Real Google sign-in needs the Credential Manager flow and a web client
        // id from google-services.json; wired when Firebase auth goes in.
        onGoogleSignIn = { viewModel.signInWithGoogle(idToken = "") },
        onCreateAccount = onCreateAccount,
        onBack = onBack,
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
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
                // The form scrolls so the keyboard can never bury a field, no
                // matter how short the screen is.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.screenPadding),
        ) {
            Spacer(Modifier.height(8.dp))
            RepsBackButton(onClick = onBack)

            Spacer(Modifier.height(28.dp))
            AuthHeader(
                headline = stringResource(R.string.login_headline),
                subtext = stringResource(R.string.login_subtext),
            )

            Spacer(Modifier.height(28.dp))
            RepsTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = stringResource(R.string.auth_email),
                placeholder = "alex@reps.app",
                leadingIcon = rememberVectorPainter(Icons.Outlined.Email),
                error = state.emailError?.let { stringResource(it) },
                enabled = !state.loading,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(16.dp))
            RepsPasswordField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.auth_password),
                leadingIcon = rememberVectorPainter(Icons.Outlined.Lock),
                error = state.passwordError?.let { stringResource(it) },
                enabled = !state.loading,
                imeAction = ImeAction.Done,
                onImeAction = onSignIn,
            )

            Text(
                text = stringResource(R.string.login_forgot_password),
                style = MaterialTheme.typography.labelMedium,
                color = RepsGreen,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onForgotPassword)
                    .padding(top = 12.dp, bottom = 4.dp),
            )

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
                text = stringResource(R.string.login_sign_in),
                onClick = onSignIn,
                loading = state.loading,
            )

            Spacer(Modifier.height(20.dp))
            AuthOrDivider()

            Spacer(Modifier.height(20.dp))
            RepsOutlinedButton(
                text = stringResource(R.string.auth_continue_google),
                onClick = onGoogleSignIn,
                enabled = !state.loading,
                leadingIcon = painterResource(R.drawable.ic_google),
                tintLeadingIcon = false,
            )

            Spacer(Modifier.height(24.dp))
            AuthFooterLink(
                question = stringResource(R.string.login_new_here),
                action = stringResource(R.string.login_create_account),
                onClick = onCreateAccount,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A, widthDp = 360, heightDp = 800)
@Composable
private fun LoginPreview() {
    com.reps.app.core.theme.RepsTheme {
        LoginContent(
            state = LoginUiState(email = "alex@reps.app", password = "supersecret"),
            onEmailChange = {}, onPasswordChange = {}, onSignIn = {},
            onForgotPassword = {}, onGoogleSignIn = {}, onCreateAccount = {}, onBack = {},
        )
    }
}
