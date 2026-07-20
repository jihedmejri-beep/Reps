package com.reps.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Tapping the tab you are already on scrolls that screen back to the top rather
 * than doing nothing, matching `onTabReselect` in the prototype's navigation.js.
 *
 * A counter rather than a boolean flag: two reselects in a row are two separate
 * events, and a flag would collapse them into one and need resetting afterwards.
 */
@Stable
class TabReselectState {
    var route by mutableStateOf<String?>(null)
        private set

    var count by mutableIntStateOf(0)
        private set

    fun signal(route: String) {
        this.route = route
        count++
    }
}

val LocalTabReselect = staticCompositionLocalOf { TabReselectState() }

@Composable
fun rememberTabReselectState(): TabReselectState = remember { TabReselectState() }

/**
 * Runs [action] whenever the user reselects the tab at [route] while already on
 * it. Skips the initial composition, which is not a reselect.
 */
@Composable
fun OnTabReselected(route: String, action: suspend () -> Unit) {
    val state = LocalTabReselect.current
    LaunchedEffect(state.count) {
        if (state.count > 0 && state.route == route) action()
    }
}
