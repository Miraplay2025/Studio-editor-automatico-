package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.models.AspectRatio
import com.example.data.models.CameraMotion
import com.example.data.models.MediaItem
import com.example.data.models.MediaType
import com.example.data.models.MotionAnimation
import com.example.data.models.TransitionEffect
import com.example.engine.VideoEncoderEngine
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BorderLight
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.PrimaryPurpleText
import com.example.ui.theme.SecondaryMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.EditorPanel
import com.example.ui.viewmodel.EditorUiState
import com.example.ui.viewmodel.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    uiState: EditorUiState,
    onBack: () -> Unit,
    onOpenExportDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timelineListState = rememberLazyListState()
    var showImportOptionsDialog by remember { mutableStateOf(false) }

    // Gallery Files Picker Launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addMediaUris(uris, context)
        }
    }

    // Directory Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.addFolderUri(treeUri, context)
        }
    }

    // Smooth scroll timeline to missing animation index if requested
    LaunchedEffect(uiState.selectedMediaIndex) {
        if (uiState.selectedMediaIndex in uiState.mediaItems.indices) {
            timelineListState.animateScrollToItem(uiState.selectedMediaIndex)
        }
    }

    Scaffold(
        containerColor = DarkCanvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.project?.title ?: "Editor de Vídeo",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onOpenExportDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = PrimaryPurpleText),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .testTag("export_video_button")
                            .padding(end = 8.dp)
                    ) {
                        Text("Exportar", color = PrimaryPurpleText, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Upper Preview Player Area
            PreviewPlayerArea(
                uiState = uiState,
                onTogglePlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekTo(it) },
                onSkipToStart = { viewModel.skipToStart() },
                onSelectAspectRatio = { viewModel.setAspectRatio(it) }
            )

            // 2. Floating Red Arrow Missing Animation Indicator
            if (uiState.missingAnimationIndex >= 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AlertRed.copy(alpha = 0.9f))
                            .clickable { viewModel.jumpToMissingAnimation() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mídia #${uiState.missingAnimationIndex + 1} sem animação! Toque para corrigir.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. Horizontal Timeline
            TimelineSection(
                uiState = uiState,
                listState = timelineListState,
                onSelectMedia = { viewModel.selectMedia(it) },
                onRemoveMedia = { viewModel.removeMediaItem(it) },
                onAddMediaClick = { showImportOptionsDialog = true }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 4. Dynamic Control Panel Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .border(1.dp, Color(0xFF252838), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(12.dp)
            ) {
                when (uiState.activePanel) {
                    EditorPanel.MAIN_CONTROLS -> MainControlsPanel(
                        uiState = uiState,
                        onOpenImport = { showImportOptionsDialog = true },
                        onOpenPanel = { viewModel.setPanel(it) }
                    )
                    EditorPanel.ANIMATIONS -> AnimationsSubPanel(
                        uiState = uiState,
                        onSelectMotion = { viewModel.updateSelectedMediaMotion(it) },
                        onUpdateDuration = { viewModel.updateSelectedMediaDuration(it) },
                        onDone = { viewModel.setPanel(EditorPanel.MAIN_CONTROLS) }
                    )
                    EditorPanel.CAMERA_ZOOM -> CameraSubPanel(
                        uiState = uiState,
                        onSelectCamera = { viewModel.updateSelectedMediaCamera(it) },
                        onDone = { viewModel.setPanel(EditorPanel.MAIN_CONTROLS) }
                    )
                    EditorPanel.TRANSITIONS -> TransitionsSubPanel(
                        uiState = uiState,
                        onToggleTransition = { viewModel.toggleTransitionSelection(it) },
                        onToggleMultiSelect = { viewModel.toggleMultiSelectTransitionsMode() },
                        onSelectAll = { viewModel.selectAllTransitions() },
                        onDone = { viewModel.setPanel(EditorPanel.MAIN_CONTROLS) }
                    )
                    EditorPanel.AUDIO_SYNC -> AudioSubPanel(
                        uiState = uiState,
                        onAutoSync = { viewModel.autoSyncTimelineWithAudio() },
                        onDone = { viewModel.setPanel(EditorPanel.MAIN_CONTROLS) }
                    )
                    EditorPanel.ASPECT_RATIO -> AspectRatioSubPanel(
                        uiState = uiState,
                        onSelectRatio = { viewModel.setAspectRatio(it) },
                        onDone = { viewModel.setPanel(EditorPanel.MAIN_CONTROLS) }
                    )
                    EditorPanel.EXPORT_SETTINGS -> { /* Opened via dialog */ }
                }
            }
        }
    }

    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = {
                Text(
                    text = "Adicionar Mídia",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showImportOptionsDialog = false
                                mediaPickerLauncher.launch("*/*")
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    tint = PrimaryPurple
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Fotos e Vídeos da Galeria", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Selecione múltiplos arquivos", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                showImportOptionsDialog = false
                                folderPickerLauncher.launch(null)
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryMint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = SecondaryMint
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Pasta / Diretório Completo", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Importar todas as mídias da pasta", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportOptionsDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
fun PreviewPlayerArea(
    uiState: EditorUiState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipToStart: () -> Unit,
    onSelectAspectRatio: (AspectRatio) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkCanvas)
            .padding(8.dp)
    ) {
        // Aspect Ratio Selector Pills
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            AspectRatio.entries.forEach { ratio ->
                val isSelected = ratio == uiState.aspectRatio
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) PrimaryPurple else DarkSurfaceVariant)
                        .clickable { onSelectAspectRatio(ratio) }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = ratio.label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Responsive Video Canvas Container
        val aspectVal = when (uiState.aspectRatio) {
            AspectRatio.RATIO_9_16 -> 9f / 16f
            AspectRatio.RATIO_16_9 -> 16f / 9f
            AspectRatio.RATIO_1_1 -> 1f
        }

        Box(
            modifier = Modifier
                .height(260.dp)
                .aspectRatio(aspectVal)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF2B2E42), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val (targetW, targetH) = uiState.exportConfig.resolution.getDimensions(uiState.aspectRatio)

            // Render active Canvas frame
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bitmapCache = mapOf<String, android.graphics.Bitmap>()
                VideoEncoderEngine.drawCompositionFrame(
                    canvas = drawContext.canvas.nativeCanvas,
                    currentTimeMs = uiState.currentTimeMs,
                    width = size.width.toInt(),
                    height = size.height.toInt(),
                    mediaItems = uiState.mediaItems,
                    bitmapCache = emptyMap(),
                    selectedTransitions = uiState.selectedTransitions
                )
            }

            if (uiState.mediaItems.isEmpty()) {
                Text("Sem mídias na linha do tempo", color = TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Playback Controls Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipToStart) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Início", tint = TextPrimary)
            }

            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(PrimaryPurple)
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = uiState.currentTimeMs.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..uiState.totalDurationMs.coerceAtLeast(1000L).toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryPurple,
                    activeTrackColor = PrimaryPurple,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            val currentSec = (uiState.currentTimeMs / 1000)
            val totalSec = (uiState.totalDurationMs / 1000)
            Text(
                text = String.format("%02d:%02d / %02d:%02d", currentSec / 60, currentSec % 60, totalSec / 60, totalSec % 60),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
fun TimelineSection(
    uiState: EditorUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectMedia: (Int) -> Unit,
    onRemoveMedia: (Int) -> Unit,
    onAddMediaClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Linha do Tempo (${uiState.mediaItems.size} mídias)",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Selecione uma foto para editar animações",
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            itemsIndexed(uiState.mediaItems) { index, item ->
                val isSelected = index == uiState.selectedMediaIndex
                val isMissingAnim = item.type == MediaType.IMAGE && item.motionAnimation == MotionAnimation.NONE

                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) PrimaryPurple else if (isMissingAnim) AlertRed else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectMedia(index) }
                        .padding(4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Missing animation warning badge
                            if (isMissingAnim) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .background(AlertRed, CircleShape)
                                        .padding(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Sem Animação",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Delete item button
                            IconButton(
                                onClick = { onRemoveMedia(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remover",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = item.motionAnimation.displayName,
                            color = if (isMissingAnim) AlertRed else TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = "${item.durationMs / 1000f}s",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                        .clickable(onClick = onAddMediaClick),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar", tint = PrimaryPurple)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Adicionar", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Audio Track Bar
        if (uiState.audioTracks.isNotEmpty()) {
            val audio = uiState.audioTracks.first()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryPurple.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${audio.name} (${audio.detectedPausesMs.size} pausas detectadas)",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MainControlsPanel(
    uiState: EditorUiState,
    onOpenImport: () -> Unit,
    onOpenPanel: (EditorPanel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ControlIconButton(
            icon = Icons.Default.Add,
            label = "Mídia / Pasta",
            onClick = onOpenImport
        )
        ControlIconButton(
            icon = Icons.Default.Animation,
            label = "Animações",
            badge = if (uiState.missingAnimationIndex >= 0) "!" else null,
            onClick = { onOpenPanel(EditorPanel.ANIMATIONS) }
        )
        ControlIconButton(
            icon = Icons.Default.Videocam,
            label = "Zoom / Câmera",
            onClick = { onOpenPanel(EditorPanel.CAMERA_ZOOM) }
        )
        ControlIconButton(
            icon = Icons.Default.Transform,
            label = "Transições",
            badge = "${uiState.selectedTransitions.size}",
            onClick = { onOpenPanel(EditorPanel.TRANSITIONS) }
        )
        ControlIconButton(
            icon = Icons.Default.GraphicEq,
            label = "Áudio & Sync",
            onClick = { onOpenPanel(EditorPanel.AUDIO_SYNC) }
        )
        ControlIconButton(
            icon = Icons.Default.AspectRatio,
            label = "Proporção",
            onClick = { onOpenPanel(EditorPanel.ASPECT_RATIO) }
        )
    }
}

@Composable
fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = TextPrimary, modifier = Modifier.size(22.dp))
            }

            badge?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AlertRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = it, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = TextSecondary, fontSize = 11.sp)
    }
}

@Composable
fun SubPanelHeader(title: String, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = PrimaryPurpleText),
            shape = RoundedCornerShape(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = "Concluído", tint = PrimaryPurpleText, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Voltar", color = PrimaryPurpleText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationsSubPanel(
    uiState: EditorUiState,
    onSelectMotion: (MotionAnimation) -> Unit,
    onUpdateDuration: (Long) -> Unit,
    onDone: () -> Unit
) {
    val activeMedia = uiState.mediaItems.getOrNull(uiState.selectedMediaIndex)

    Column(modifier = Modifier.fillMaxWidth()) {
        SubPanelHeader(title = "Animação de Movimento (${activeMedia?.title ?: ""})", onDone = onDone)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(MotionAnimation.entries) { _, motion ->
                val isSelected = activeMedia?.motionAnimation == motion
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectMotion(motion) },
                    label = { Text(motion.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryPurple,
                        selectedLabelColor = PrimaryPurpleText,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Duration Slider
        activeMedia?.let { media ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Duração da Mídia:", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = media.durationMs.toFloat(),
                    onValueChange = { onUpdateDuration(it.toLong()) },
                    valueRange = 1000f..8000f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = PrimaryPurple, activeTrackColor = PrimaryPurple)
                )
                Text("${media.durationMs / 1000f}s", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSubPanel(
    uiState: EditorUiState,
    onSelectCamera: (CameraMotion) -> Unit,
    onDone: () -> Unit
) {
    val activeMedia = uiState.mediaItems.getOrNull(uiState.selectedMediaIndex)

    Column(modifier = Modifier.fillMaxWidth()) {
        SubPanelHeader(title = "Movimento de Câmera (Pan & Zoom)", onDone = onDone)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(CameraMotion.entries) { _, camera ->
                val isSelected = activeMedia?.cameraMotion == camera
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCamera(camera) },
                    label = { Text(camera.displayName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryPurple,
                        selectedLabelColor = PrimaryPurpleText,
                        containerColor = DarkSurfaceVariant,
                        labelColor = TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
fun TransitionsSubPanel(
    uiState: EditorUiState,
    onToggleTransition: (String) -> Unit,
    onToggleMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPanelHeader(title = "Efeitos de Transição (20 Opções Suaves)", onDone = onDone)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onToggleMultiSelect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isMultiSelectTransitions) SecondaryMint else DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (uiState.isMultiSelectTransitions) "Multi-Seleção Ativa" else "Selecionar Transições",
                    color = if (uiState.isMultiSelectTransitions) Color.Black else TextPrimary,
                    fontSize = 11.sp
                )
            }

            TextButton(onClick = onSelectAll) {
                Text("Selecionar Todas (${TransitionEffect.ALL_TRANSITIONS.size})", color = PrimaryPurple, fontSize = 11.sp)
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(TransitionEffect.ALL_TRANSITIONS) { _, effect ->
                val isSelected = uiState.selectedTransitions.contains(effect.id)
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryPurple.copy(alpha = 0.3f) else DarkSurfaceVariant)
                        .border(1.dp, if (isSelected) PrimaryPurple else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { onToggleTransition(effect.id) }
                        .padding(8.dp)
                ) {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(effect.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryPurple, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(effect.description, color = TextMuted, fontSize = 9.sp, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
fun AudioSubPanel(
    uiState: EditorUiState,
    onAutoSync: () -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPanelHeader(title = "Trilha Sonora & Sincronização por Pausas", onDone = onDone)

        Text(
            text = "O motor analisa a faixa de áudio e detecta pausas de fala e silêncio para ajustar automaticamente o tempo de cada mídia na linha do tempo.",
            color = TextSecondary,
            fontSize = 11.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAutoSync,
            enabled = !uiState.isAnalyzingAudio,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple, contentColor = PrimaryPurpleText),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isAnalyzingAudio) {
                CircularProgressIndicator(color = PrimaryPurpleText, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analisando Pausas do Áudio...", color = PrimaryPurpleText, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryPurpleText)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto-Sincronizar Linha do Tempo por Pausas", color = PrimaryPurpleText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AspectRatioSubPanel(
    uiState: EditorUiState,
    onSelectRatio: (AspectRatio) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubPanelHeader(title = "Proporção do Vídeo", onDone = onDone)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AspectRatio.entries.forEach { ratio ->
                val isSelected = ratio == uiState.aspectRatio
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) PrimaryPurple else DarkSurfaceVariant)
                        .clickable { onSelectRatio(ratio) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ratio.label,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
