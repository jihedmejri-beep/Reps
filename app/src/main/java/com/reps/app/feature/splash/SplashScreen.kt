package com.reps.app.feature.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale.Companion.FillWidth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reps.app.R
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme
import kotlinx.coroutines.delay

// Timings measured from the supplied start-animation video.
private const val FADE_IN_MS = 500
private const val WORDMARK_FADE_MS = 470
private const val HOLD_UNTIL_MS = 2400L
private const val FADE_OUT_MS = 120
private const val HINT_PULSE_MS = 2000

@Composable
fun SplashScreen(
    onFinished: (SplashDestination) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val currentOnFinished by rememberUpdatedState(onFinished)
    var leaving by remember { mutableStateOf(false) }

    // Tapping is a shortcut, not a second path: it sets the same flag the timer
    // does, so the exit animation and destination are identical either way.
    fun leave() { leaving = true }

    LaunchedEffect(Unit) {
        delay(HOLD_UNTIL_MS)
        leave()
    }

    LaunchedEffect(leaving, destination) {
        if (!leaving) return@LaunchedEffect
        delay(FADE_OUT_MS.toLong())
        // If preferences are somehow still settling, wait rather than guessing
        // wrong and sending a returning user back through onboarding.
        currentOnFinished(destination ?: return@LaunchedEffect)
    }

    SplashContent(leaving = leaving, onTap = ::leave)
}

@Composable
private fun SplashContent(
    leaving: Boolean,
    onTap: () -> Unit,
) {
    val contentAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(if (leaving) FADE_OUT_MS else FADE_IN_MS, easing = LinearEasing),
        label = "splashAlpha",
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (leaving) 0f else 1f,
        animationSpec = tween(if (leaving) FADE_OUT_MS else WORDMARK_FADE_MS, easing = LinearEasing),
        label = "wordmarkAlpha",
    )

    val pulse = rememberInfiniteTransition(label = "hint")
    val hintAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(HINT_PULSE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hintAlpha",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // The splash is a fixed brand moment - always the near-black brand
            // background, regardless of the app's light/dark setting, because
            // the wordmark on it is white.
            .background(RepsNearBlack)
            // No ripple: the splash has no pressed state to communicate.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
    ) {

        // Everything scales off screen width, so the mark keeps the same
        // proportions from a 320dp phone to an unfolded foldable. The cap stops
        // it ballooning on tablets.
        val wordmarkWidth = (maxWidth * 0.378f).coerceAtMost(260.dp)
        val barsWidth = wordmarkWidth * 0.5f
        val barsHeight = barsWidth * 1.12f
        val gapBarsToWordmark = wordmarkWidth * 0.15f
        val gapWordmarkToTagline = wordmarkWidth * 0.20f

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RepsBarsAnimation(
                modifier = Modifier
                    .width(barsWidth)
                    .height(barsHeight)
                    .alpha(contentAlpha),
                color = RepsGreen,
                running = !leaving,
            )

            Box(Modifier.height(gapBarsToWordmark))

            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = stringResource(R.string.app_name),
                contentScale = FillWidth,
                modifier = Modifier.width(wordmarkWidth).alpha(wordmarkAlpha),
            )

            Box(Modifier.height(gapWordmarkToTagline))

            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = RepsGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(contentAlpha),
            )
        }

        Text(
            text = stringResource(R.string.splash_tap_to_continue),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = RepsTextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
                .fillMaxWidth()
                .alpha(hintAlpha * contentAlpha),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0A, widthDp = 360, heightDp = 780)
@Composable
private fun SplashPreview() {
    RepsTheme {
        SplashContent(leaving = false, onTap = {})
    }
}
