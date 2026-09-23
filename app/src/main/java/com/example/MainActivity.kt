package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.core.navigation.AppNavGraph
import com.example.ui.theme.InternetStorerTheme
import com.example.ui.theme.WarmIvoryBackground

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as InternetStorerApplication).container

        setContent {
            val themeMode by appContainer.preferencesManager.themeMode.collectAsState(initial = "Soft Romantic")

            InternetStorerTheme(themePreference = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = WarmIvoryBackground
                ) {
                    AppNavGraph(appContainer = appContainer)
                }
            }
        }
    }
}
