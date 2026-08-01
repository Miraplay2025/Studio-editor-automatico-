package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.engine.VideoRenderService
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ExportDialog
import com.example.ui.screens.RenderProgressDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.ProjectViewModel

class MainActivity : ComponentActivity() {

    private var renderService: VideoRenderService? = null
    private var isServiceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as VideoRenderService.LocalBinder
            renderService = binder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            renderService = null
            isServiceBound = false
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bind Foreground Service
        val serviceIntent = Intent(this, VideoRenderService::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val projectViewModel: ProjectViewModel = viewModel()
                val projects by projectViewModel.allProjects.collectAsStateWithLifecycle()

                val renderServiceState by VideoRenderService.renderState.collectAsStateWithLifecycle()

                var showExportDialog by remember { mutableStateOf(false) }
                var showRenderDialog by remember { mutableStateOf(false) }
                val sheetState = rememberModalBottomSheetState()

                NavHost(
                    navController = navController,
                    startDestination = "dashboard",
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("dashboard") {
                        DashboardScreen(
                            projects = projects,
                            onCreateProjectWithMedia = { title, mediaItems ->
                                projectViewModel.createProjectWithMedia(title, mediaItems) { newId ->
                                    navController.navigate("editor/$newId")
                                }
                            },
                            onOpenProject = { projectId ->
                                navController.navigate("editor/$projectId")
                            },
                            onDeleteProject = { projectId ->
                                projectViewModel.deleteProject(projectId)
                            }
                        )
                    }

                    composable(
                        route = "editor/{projectId}",
                        arguments = listOf(navArgument("projectId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        val editorViewModel: EditorViewModel = viewModel()

                        val uiState by editorViewModel.uiState.collectAsStateWithLifecycle()

                        LaunchedEffect(projectId) {
                            editorViewModel.loadProject(projectId)
                        }

                        LaunchedEffect(uiState.toastMessage) {
                            uiState.toastMessage?.let { msg ->
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                editorViewModel.clearToast()
                            }
                        }

                        EditorScreen(
                            viewModel = editorViewModel,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onOpenExportDialog = { showExportDialog = true }
                        )

                        if (showExportDialog) {
                            ExportDialog(
                                currentConfig = uiState.exportConfig,
                                sheetState = sheetState,
                                onDismiss = { showExportDialog = false },
                                onStartRender = { config ->
                                    showExportDialog = false
                                    showRenderDialog = true
                                    editorViewModel.updateExportConfig(config.resolution, config.fps)

                                    // Start Foreground Service Render
                                    renderService?.startRendering(
                                        projectId = projectId,
                                        mediaItems = uiState.mediaItems,
                                        selectedTransitions = uiState.selectedTransitions,
                                        exportConfig = config
                                    )
                                }
                            )
                        }

                        if (showRenderDialog || renderServiceState.isRendering) {
                            RenderProgressDialog(
                                renderState = renderServiceState,
                                onCancelRender = {
                                    renderService?.cancelRendering()
                                    showRenderDialog = false
                                },
                                onDismissDialog = { showRenderDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
    }
}
