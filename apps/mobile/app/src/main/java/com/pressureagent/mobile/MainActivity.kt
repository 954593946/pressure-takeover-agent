package com.pressureagent.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pressureagent.mobile.ui.navigation.AppNavigation
import com.pressureagent.mobile.ui.theme.PressureTakeoverTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PressureTakeoverTheme {
                AppNavigation()
            }
        }
    }
}
