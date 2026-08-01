package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CameraAnimation
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onBackToDashboard: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val title by viewModel.projectTitle.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.selectedItemIndex.collectAsStateWithLifecycle()
    val transitionConfigs by viewModel.transitionConfigs.collectAsStateWithLifecycle()
    val transitionDuration by viewModel.transitionDuration.collectAsStateWithLifecycle()
    val audioUris by viewModel.audioUris.collectAsStateWithLifecycle()
    val audioSegments by viewModel.audioSegments.collectAsStateWithLifecycle()
    val exportOptions by viewModel.exportOptions.collectAsStateWithLifecycle()
    val renderState by viewModel.renderProgressState.collectAsStateWithLifecycle()
    val hasUnassignedAnimation by viewModel.hasUnassignedAnimation.collectAsStateWithLifecycle()
    val firstUnassignedIndex by viewModel.firstUnassignedIndex.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Animations, 1: Transitions, 2: Audio
    var showExportModal by remember { mutableStateOf(false) }

    // Pickers for adding media & audio files
    val addMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addMediaItems(uris)
    }

    val uploadAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadAndProcessAudioFiles(uris)
    }

    val selectedMediaItem = mediaItems.getOrNull(selectedIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.saveProjectDraft()
                            onBackToDashboard()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Auto Edit Button
                    Button(
                        onClick = {
                            viewModel.startAutoEditing(context) { error ->
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .testTag("start_auto_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Iniciar",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edição Automática", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Button
                    IconButton(
                        onClick = { showExportModal = true },
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Exportar Vídeo",
                            tint = SecondaryCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Preview Player
            PreviewPlayerComponent(
                selectedItem = selectedMediaItem,
                modifier = Modifier.padding(12.dp)
            )

            // Scrollable Timeline with Floating Red Arrow Validation
            TimelineComponent(
                mediaItems = mediaItems,
                selectedIndex = selectedIndex,
                hasUnassignedAnimation = hasUnassignedAnimation,
                firstUnassignedIndex = firstUnassignedIndex,
                onItemSelected = { viewModel.selectMediaItem(it) },
                onRemoveItem = { viewModel.removeMediaItem(it) },
                onAddMediaClick = { addMediaLauncher.launch(arrayOf("image/*", "video/*")) }
            )

            // Panel Tabs (0: Animações, 1: Transições, 2: Áudio)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = DarkSurface,
                contentColor = SecondaryCyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = "Animações", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Animações (10)", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_animations")
                )

                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Transições", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Transições (20)", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_transitions")
                )

                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, contentDescription = "Áudio", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Áudio & VAD", fontSize = 12.sp)
                        }
                    },
                    modifier = Modifier.testTag("tab_audio")
                )
            }

            // Tab Panel Contents
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(DarkBackground)
            ) {
                when (activeTab) {
                    0 -> {
                        val currentAnim = CameraAnimation.fromId(selectedMediaItem?.animationType ?: "NONE")
                        CameraAnimationPanel(
                            selectedAnimation = currentAnim,
                            onAnimationSelected = { anim ->
                                viewModel.applyCameraAnimationToSelectedItem(anim)
                            }
                        )
                    }

                    1 -> {
                        TransitionPanel(
                            transitionItems = transitionConfigs,
                            transitionDurationSec = transitionDuration,
                            onToggleActive = { type -> viewModel.toggleTransitionActive(type) },
                            onDurationChanged = { dur -> viewModel.updateTransitionDuration(dur) }
                        )
                    }

                    2 -> {
                        AudioNarrationPanel(
                            audioUris = audioUris,
                            segments = audioSegments,
                            onUploadAudioClick = { uploadAudioLauncher.launch(arrayOf("audio/*")) }
                        )
                    }
                }
            }
        }
    }

    // Render Progress Modal Overlay
    RenderProgressModal(
        state = renderState,
        onCancelRequested = { viewModel.cancelRendering() },
        onDismissSuccess = { viewModel.cancelRendering() }
    )

    // Export Modal Dialog
    if (showExportModal) {
        ExportModal(
            currentOptions = exportOptions,
            onDismiss = { showExportModal = false },
            onConfirmExport = { options ->
                viewModel.updateExportOptions(options.resolution, options.quality, options.fps)
                showExportModal = false
                viewModel.startAutoEditing(context) { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
