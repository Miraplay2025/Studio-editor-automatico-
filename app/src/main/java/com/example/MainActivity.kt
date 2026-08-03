package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.MainViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest

enum class AppScreen {
    SPLASH, DASHBOARD, EDITOR
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D0E17)
                ) {
                    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

                    LaunchedEffect(Unit) {
                        viewModel.toastEvent.collectLatest { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                        }
                    }

                    when (currentScreen) {
                        AppScreen.SPLASH -> {
                            SplashScreen(
                                onSplashFinished = {
                                    currentScreen = AppScreen.DASHBOARD
                                }
                            )
                        }
                        AppScreen.DASHBOARD -> {
                            DashboardScreen(
                                viewModel = viewModel,
                                onCreateNewProject = {
                                    currentScreen = AppScreen.EDITOR
                                },
                                onOpenProject = { _ ->
                                    currentScreen = AppScreen.EDITOR
                                }
                            )
                        }
                        AppScreen.EDITOR -> {
                            EditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    currentScreen = AppScreen.DASHBOARD
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

