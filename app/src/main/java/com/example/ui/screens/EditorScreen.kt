package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ActiveSubMenu
import com.example.data.AspectRatioOption
import com.example.data.CameraAnimation
import com.example.data.MediaClip
import com.example.data.ZoomAnimation
import com.example.ui.MainViewModel
import com.example.ui.components.AnimationSubMenu
import com.example.ui.components.AudioSubMenu
import com.example.ui.components.AutoEditModal
import com.example.ui.components.ExportModal
import com.example.ui.components.RatioSubMenu
import com.example.ui.components.TransitionSubMenu
import com.example.ui.components.ZoomSubMenu
import kotlinx.coroutines.delay

@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val projectTitle by viewModel.projectTitle.collectAsStateWithLifecycle()
    val mediaClips by viewModel.mediaClips.collectAsStateWithLifecycle()
    val selectedClipId by viewModel.selectedClipId.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.aspectRatio.collectAsStateWithLifecycle()
    val selectedTransitionsPool by viewModel.selectedTransitions.collectAsStateWithLifecycle()
    val isSelectionModeActive by viewModel.isTransitionSelectionMode.collectAsStateWithLifecycle()
    val audioTrack by viewModel.audioTrack.collectAsStateWithLifecycle()
    val isAnalyzingAudio by viewModel.isAnalyzingAudio.collectAsStateWithLifecycle()
    val renderState by viewModel.renderState.collectAsStateWithLifecycle()
    val showAutoEditModal by viewModel.showAutoEditModal.collectAsStateWithLifecycle()
    val showExportModal by viewModel.showExportModal.collectAsStateWithLifecycle()
    val exportResolution by viewModel.exportResolution.collectAsStateWithLifecycle()
    val exportQuality by viewModel.exportQuality.collectAsStateWithLifecycle()
    val exportFps by viewModel.exportFps.collectAsStateWithLifecycle()
    val lastExportedFile by viewModel.lastExportedFile.collectAsStateWithLifecycle()

    var activeSubMenu by remember { mutableStateOf(ActiveSubMenu.NONE) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackSec by remember { mutableFloatStateOf(0f) }

    val totalDurationSec = remember(mediaClips) {
        mediaClips.sumOf { it.durationSec.toDouble() }.toFloat()
    }

    val selectedClip = remember(mediaClips, selectedClipId) {
        mediaClips.find { it.id == selectedClipId } ?: mediaClips.firstOrNull()
    }

    // Media Picker launcher for multiple images & videos
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaUris(uris)
        }
    }

    // Audio Picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAudioTrack(it.toString(), "Narração Gravação Audio")
        }
    }

    // Playback ticker simulation for live player preview
    LaunchedEffect(isPlaying, totalDurationSec) {
        if (isPlaying && totalDurationSec > 0f) {
            while (isPlaying) {
                delay(50)
                currentPlaybackSec += 0.05f
                if (currentPlaybackSec >= totalDurationSec) {
                    currentPlaybackSec = 0f
                    isPlaying = false
                }
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF0D0E17)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. TOP BAR: Back Arrow (Autosaves Draft) + Iniciar Edição Automática
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141624))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.saveCurrentProjectDraft()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar e Salvar Rascunho",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = projectTitle,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { viewModel.startAutoEditing() },
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("start_auto_edit_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Iniciar Edição Automática",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 2. PLAYER DE VÍDEO CENTRALIZADO COM CONTROLES & SELETOR DE PROPORÇÃO
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Ratio Container Frame
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(aspectRatio.ratio)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1C2C))
                            .border(1.dp, Color(0xFF323755), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedClip != null) {
                            // Live visual preview applying Camera & Zoom transforms
                            val cam = selectedClip.cameraAnim
                            val zoom = selectedClip.zoomAnim

                            val scaleX = if (zoom == ZoomAnimation.ZOOM_IN) 1.15f else 1.0f
                            val scaleY = if (zoom == ZoomAnimation.ZOOM_IN) 1.15f else 1.0f

                            AsyncImage(
                                model = selectedClip.uriString,
                                contentDescription = "Pré-visualização do clipe",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        this.scaleX = scaleX
                                        this.scaleY = scaleY
                                    }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFF5A5F82),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Nenhuma mídia na timeline", color = Color(0xFF8E95C0), fontSize = 13.sp)
                            }
                        }

                        // Play/Pause Overlay Button
                        IconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausar" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Player Scrubber Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format("%.1fs", currentPlaybackSec),
                            fontSize = 11.sp,
                            color = Color(0xFFB0B5E0)
                        )
                        Slider(
                            value = currentPlaybackSec,
                            onValueChange = { currentPlaybackSec = it },
                            valueRange = 0f..totalDurationSec.coerceAtLeast(1f),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF7C4DFF),
                                activeTrackColor = Color(0xFF7C4DFF),
                                inactiveTrackColor = Color(0xFF343854)
                            )
                        )
                        Text(
                            text = String.format("%.1fs", totalDurationSec),
                            fontSize = 11.sp,
                            color = Color(0xFFB0B5E0)
                        )
                    }
                }
            }

            // 3. AREA INFERIOR DINÂMICA (SUBSTITUIÇÃO MODULAR OU TIMELINE)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141624))
            ) {
                if (activeSubMenu != ActiveSubMenu.NONE) {
                    // Modular Replacement Sub-Menu
                    when (activeSubMenu) {
                        ActiveSubMenu.RATIO -> {
                            RatioSubMenu(
                                currentRatio = aspectRatio,
                                onSelectRatio = { viewModel.setAspectRatio(it) },
                                onClose = { activeSubMenu = ActiveSubMenu.NONE }
                            )
                        }
                        ActiveSubMenu.ANIMATION -> {
                            AnimationSubMenu(
                                selectedClip = selectedClip,
                                onToggleCameraAnim = { anim -> viewModel.toggleCameraAnimation(selectedClip?.id ?: "", anim) },
                                onToggleZoomAnim = { zoom -> viewModel.toggleZoomAnimation(selectedClip?.id ?: "", zoom) },
                                onClose = { activeSubMenu = ActiveSubMenu.NONE }
                            )
                        }
                        ActiveSubMenu.ZOOM -> {
                            ZoomSubMenu(
                                selectedClip = selectedClip,
                                onToggleZoomAnim = { zoom -> viewModel.toggleZoomAnimation(selectedClip?.id ?: "", zoom) },
                                onClose = { activeSubMenu = ActiveSubMenu.NONE }
                            )
                        }
                        ActiveSubMenu.TRANSITION -> {
                            TransitionSubMenu(
                                selectedTransitionsPool = selectedTransitionsPool,
                                isSelectionModeActive = isSelectionModeActive,
                                onToggleSelectionMode = { viewModel.toggleTransitionSelectionMode() },
                                onToggleTransitionInPool = { trans -> viewModel.toggleTransitionInPool(trans) },
                                onPreviewTransition = { trans -> viewModel.assignTransitionToSelectedClip(trans) },
                                onClose = { activeSubMenu = ActiveSubMenu.NONE }
                            )
                        }
                        ActiveSubMenu.AUDIO -> {
                            AudioSubMenu(
                                audioTrack = audioTrack,
                                isAnalyzing = isAnalyzingAudio,
                                onUploadAudio = { audioPickerLauncher.launch("audio/*") },
                                onRemoveAudio = { viewModel.removeAudioTrack() },
                                onSyncSpeechPauses = { viewModel.syncImagesToSpeechPauses() },
                                onClose = { activeSubMenu = ActiveSubMenu.NONE }
                            )
                        }
                        else -> { activeSubMenu = ActiveSubMenu.NONE }
                    }
                } else {
                    // Standard Timeline View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Linha do Tempo (${mediaClips.size} mídias)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )

                            IconButton(
                                onClick = { mediaPickerLauncher.launch("image/* video/*") },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF6200EE))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Adicionar Mídia",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Horizontal Media Timeline Track
                        if (mediaClips.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2132))
                                    .clickable { mediaPickerLauncher.launch("image/* video/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF7C4DFF))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clique aqui para importar mídias ou pasta", color = Color(0xFF9096B8), fontSize = 13.sp)
                                }
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                itemsIndexed(
                                    items = mediaClips,
                                    key = { _, item -> item.id }
                                ) { index, clip ->
                                    val isSelected = clip.id == selectedClipId

                                    Card(
                                        modifier = Modifier
                                            .width(90.dp)
                                            .height(90.dp)
                                            .clickable { viewModel.selectClip(clip.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222538)),
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF7C4DFF)) else null
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = clip.uriString,
                                                contentDescription = "Miniatura clipe ${index + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Duration Badge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.Black.copy(alpha = 0.7f))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = String.format("%.1fs", clip.durationSec),
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // Delete Button
                                            IconButton(
                                                onClick = { viewModel.removeClip(clip.id) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Remover clipe",
                                                    tint = Color(0xFFFF5252),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. BOTTOM MENU NAVIGATION BAR (Proporção, Animações, Zoom, Transições, Áudio)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1D2D))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Resource 1: Proporção
                BottomNavItem(
                    icon = Icons.Default.AspectRatio,
                    label = "Proporção",
                    isActive = activeSubMenu == ActiveSubMenu.RATIO,
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ActiveSubMenu.RATIO) ActiveSubMenu.NONE else ActiveSubMenu.RATIO
                    }
                )

                // Resource 2: Animações
                BottomNavItem(
                    icon = Icons.Default.Videocam,
                    label = "Animações",
                    isActive = activeSubMenu == ActiveSubMenu.ANIMATION,
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ActiveSubMenu.ANIMATION) ActiveSubMenu.NONE else ActiveSubMenu.ANIMATION
                    }
                )

                // Resource 3: Zoom
                BottomNavItem(
                    icon = Icons.Default.ZoomIn,
                    label = "Zoom",
                    isActive = activeSubMenu == ActiveSubMenu.ZOOM,
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ActiveSubMenu.ZOOM) ActiveSubMenu.NONE else ActiveSubMenu.ZOOM
                    }
                )

                // Resource 4: Transições
                BottomNavItem(
                    icon = Icons.Default.Transform,
                    label = "Transições",
                    isActive = activeSubMenu == ActiveSubMenu.TRANSITION,
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ActiveSubMenu.TRANSITION) ActiveSubMenu.NONE else ActiveSubMenu.TRANSITION
                    }
                )

                // Resource 5: Áudio & Sincronização
                BottomNavItem(
                    icon = Icons.Default.Mic,
                    label = "Áudio",
                    isActive = activeSubMenu == ActiveSubMenu.AUDIO,
                    onClick = {
                        activeSubMenu = if (activeSubMenu == ActiveSubMenu.AUDIO) ActiveSubMenu.NONE else ActiveSubMenu.AUDIO
                    }
                )
            }
        }
    }

    // Auto Edit Progress Modal
    if (showAutoEditModal) {
        AutoEditModal(
            renderState = renderState,
            onCancelRender = { viewModel.cancelRendering() },
            onOpenExportModal = { viewModel.openExportModal() },
            onDismiss = { viewModel.dismissAutoEditModal() }
        )
    }

    // Export & Download Options Modal
    if (showExportModal) {
        ExportModal(
            exportedFile = lastExportedFile,
            selectedResolution = exportResolution,
            selectedQuality = exportQuality,
            selectedFps = exportFps,
            onSelectResolution = { viewModel.setExportResolution(it) },
            onSelectQuality = { viewModel.setExportQuality(it) },
            onSelectFps = { viewModel.setExportFps(it) },
            onDismiss = { viewModel.dismissExportModal() },
            onSaveToGallery = { viewModel.dismissExportModal() }
        )
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) Color(0xFF7C4DFF) else Color(0xFF8E95C0),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) Color.White else Color(0xFF8E95C0)
        )
    }
}
