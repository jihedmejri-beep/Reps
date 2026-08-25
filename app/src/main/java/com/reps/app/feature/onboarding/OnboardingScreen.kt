package com.reps.app.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale.Companion.FillWidth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.reps.app.R
import com.reps.app.core.components.RepsButton
import com.reps.app.core.components.RepsOutlinedButton
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTheme
import com.reps.app.feature.auth.authContentWidth
import com.reps.app.feature.splash.RepsBarsAnimation

@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit,
    onHaveAccount: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingContent(
        onGetStarted = { viewModel.completeOnboarding(onGetStarted) },
        onHaveAccount = { viewModel.completeOnboarding(onHaveAccount) },
    )
}

@Composable
private fun OnboardingContent(
    onGetStarted: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    val dimens = RepsTheme.dimens

    BoxWithConstraints(
        // Always the near-black brand background: the wordmark shown here is
        // white, so this screen stays dark whatever the app's theme.
        Modifier.fillMaxSize().background(RepsNearBlack),
    ) {
        // The mark is sized off screen width so it holds its proportions from a
        // small phone up to an unfolded foldable.
        val wordmarkWidth = (maxWidth * 0.42f).coerceAtMost(280.dp)
        val barsWidth = wordmarkWidth * 0.5f

        Column(
            Modifier
                .authContentWidth()
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dimens.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo block sits in the upper half, copy and CTAs in the lower.
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The brief asks for this mark to move as it does on the
                    // start animation, so it reuses that exact component.
                    RepsBarsAnimation(
                        modifier = Modifier
                            .width(barsWidth)
                            .height(barsWidth * 1.12f),
                    )
                    Spacer(Modifier.height(wordmarkWidth * 0.15f))
                    Image(
                        painter = painterResource(R.drawable.logo_wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = FillWidth,
                        modifier = Modifier.width(wordmarkWidth),
                    )
                }
            }

            Text(
                text = stringResource(R.string.onboarding_headline).uppercase(),
                style = RepsTheme.textStyles.sectionTitle,
                color = RepsTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.onboarding_subtext),
                style = MaterialTheme.typography.bodyMedium,
                color = RepsTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Spacer(Modifier.height(32.dp))

            RepsButton(
                text = stringResource(R.string.onboarding_get_started),
                onClick = onGetStarted,
            )
            Spacer(Modifier.height(12.dp))
            RepsOutlinedButton(
                text = stringResource(R.string.onboarding_have_account),
                onClick = onHaveAccount,
            )

            Spacer(Modifier.height(dimens.sectionGap * 2))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A, widthDp = 360, heightDp = 800)
@Composable
private fun OnboardingPreview() {
    RepsTheme {
        OnboardingContent(onGetStarted = {}, onHaveAccount = {})
    }
}
