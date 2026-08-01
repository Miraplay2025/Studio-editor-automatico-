package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.theme.CineCutTheme
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CineCutTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CineCutAppNavigation()
                }
            }
        }
    }
}

@Composable
fun CineCutAppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "dashboard"
    ) {
        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                viewModel = dashboardViewModel,
                onOpenProject = { projectId ->
                    navController.navigate("editor/$projectId")
                }
            )
        }

        composable(
            route = "editor/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val editorViewModel: EditorViewModel = viewModel()
            EditorScreen(
                projectId = projectId,
                viewModel = editorViewModel,
                onBackToDashboard = {
                    navController.popBackStack()
                }
            )
        }
    }
}
