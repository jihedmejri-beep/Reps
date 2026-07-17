package com.reps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.reps.app.core.theme.RepsNearBlack
import com.reps.app.core.theme.RepsTheme
import com.reps.app.navigation.RepsApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RepsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = RepsNearBlack,
                ) {
                    RepsApp()
                }
            }
        }
    }
}
