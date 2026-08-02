package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.JsonUtil
import com.example.data.local.ProjectRepository
import com.example.data.models.AspectRatio
import com.example.data.models.AudioTrack
import com.example.data.models.CameraMotion
import com.example.data.models.ExportConfig
import com.example.data.models.ExportResolution
import com.example.data.models.MediaItem
import com.example.data.models.MediaType
import com.example.data.models.MotionAnimation
import com.example.data.models.ProjectEntity
import com.example.data.models.TransitionEffect
import com.example.engine.AudioSyncEngine
import com.example.engine.SampleMediaProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.utils.MediaHelper

enum class EditorPanel {
    MAIN_CONTROLS,
    ANIMATIONS,
    CAMERA_ZOOM,
    TRANSITIONS,
    AUDIO_SYNC,
    ASPECT_RATIO,
    EXPORT_SETTINGS
}

data class EditorUiState(
    val project: ProjectEntity? = null,
    val mediaItems: List<MediaItem> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val selectedTransitions: List<String> = listOf("CROSSFADE", "SLIDE_LEFT", "SLIDE_RIGHT"),
    val previewTransitionEffectId: String? = null,
    val activePanel: EditorPanel = EditorPanel.MAIN_CONTROLS,
    val selectedMediaIndex: Int = 0,
    val isPlaying: Boolean = false,
    val currentTimeMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val exportConfig: ExportConfig = ExportConfig(),
    val isMultiSelectTransitions: Boolean = false,
    val missingAnimationIndex: Int = -1,
    val isAnalyzingAudio: Boolean = false,
    val toastMessage: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(AppDatabase.getDatabase(application).projectDao())

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playbackJob: Job? = null

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            var entity = repository.getProjectById(projectId)
            if (entity == null) {
                entity = ProjectEntity(id = projectId)
                repository.saveProject(entity)
            }

            var items = JsonUtil.deserializeMediaItems(entity.mediaItemsJson)
            val tracks = JsonUtil.deserializeAudioTracks(entity.audioTracksJson)
            val transitions = JsonUtil.deserializeTransitions(entity.selectedTransitionsJson)

            val totalDuration = items.sumOf { it.durationMs }
            val missingIdx = findFirstMissingAnimationIndex(items)
            val ratio = try { AspectRatio.valueOf(entity.aspectRatioName) } catch (e: Exception) { AspectRatio.RATIO_9_16 }
            val res = try { ExportResolution.valueOf(entity.exportResolutionName) } catch (e: Exception) { ExportResolution.RES_720P }

            _uiState.update {
                it.copy(
                    project = entity,
                    mediaItems = items,
                    audioTracks = tracks,
                    selectedTransitions = transitions,
                    totalDurationMs = totalDuration,
                    missingAnimationIndex = missingIdx,
                    aspectRatio = ratio,
                    exportConfig = ExportConfig(resolution = res, aspectRatio = ratio, fps = entity.exportFps)
                )
            }

            saveCurrentState()
        }
    }

    private fun findFirstMissingAnimationIndex(items: List<MediaItem>): Int {
        return items.indexOfFirst { it.type == MediaType.IMAGE && it.motionAnimation == MotionAnimation.NONE }
    }

    fun selectMedia(index: Int) {
        if (index in _uiState.value.mediaItems.indices) {
            _uiState.update { it.copy(selectedMediaIndex = index) }
            val itemStartTime = _uiState.value.mediaItems.take(index).sumOf { it.durationMs }
            seekTo(itemStartTime)
        }
    }

    fun jumpToMissingAnimation() {
        val missingIdx = _uiState.value.missingAnimationIndex
        if (missingIdx >= 0) {
            selectMedia(missingIdx)
            setPanel(EditorPanel.ANIMATIONS)
        }
    }

    fun setPanel(panel: EditorPanel) {
        _uiState.update { it.copy(activePanel = panel) }
    }

    fun updateSelectedMediaMotion(motion: MotionAnimation) {
        val idx = _uiState.value.selectedMediaIndex
        val items = _uiState.value.mediaItems.toMutableList()
        if (idx in items.indices) {
            val currentMotion = items[idx].motionAnimation
            val newMotion = if (currentMotion == motion) MotionAnimation.NONE else motion
            val updated = items[idx].copy(motionAnimation = newMotion)
            items[idx] = updated
            val missingIdx = findFirstMissingAnimationIndex(items)
            _uiState.update {
                it.copy(
                    mediaItems = items,
                    missingAnimationIndex = missingIdx
                )
            }
            saveCurrentState()
        }
    }

    fun updateSelectedMediaCamera(camera: CameraMotion) {
        val idx = _uiState.value.selectedMediaIndex
        val items = _uiState.value.mediaItems.toMutableList()
        if (idx in items.indices) {
            val currentCamera = items[idx].cameraMotion
            val newCamera = if (currentCamera == camera) CameraMotion.NONE else camera
            val updated = items[idx].copy(cameraMotion = newCamera)
            items[idx] = updated
            _uiState.update { it.copy(mediaItems = items) }
            saveCurrentState()
        }
    }

    fun updateSelectedMediaDuration(durationMs: Long) {
        val idx = _uiState.value.selectedMediaIndex
        val items = _uiState.value.mediaItems.toMutableList()
        if (idx in items.indices) {
            items[idx] = items[idx].copy(durationMs = durationMs)
            val total = items.sumOf { it.durationMs }
            _uiState.update { it.copy(mediaItems = items, totalDurationMs = total) }
            saveCurrentState()
        }
    }

    fun previewTransition(effectId: String) {
        val idx = _uiState.value.selectedMediaIndex
        val items = _uiState.value.mediaItems
        val transitionTime = if (items.isNotEmpty() && idx in items.indices) {
            val startTime = items.take(idx).sumOf { it.durationMs }
            val itemDuration = items[idx].durationMs
            (startTime + itemDuration - 600L).coerceAtLeast(0L)
        } else {
            0L
        }

        _uiState.update { it.copy(previewTransitionEffectId = effectId) }
        seekTo(transitionTime)
        play()
    }

    fun applyPreviewTransition(effectId: String? = null) {
        val targetEffect = effectId ?: _uiState.value.previewTransitionEffectId ?: return
        val currentList = _uiState.value.selectedTransitions.toMutableList()
        if (!currentList.contains(targetEffect)) {
            currentList.add(targetEffect)
        }
        val idx = _uiState.value.selectedMediaIndex
        val items = _uiState.value.mediaItems.toMutableList()
        if (idx in items.indices) {
            items[idx] = items[idx].copy(transitionOverride = targetEffect)
        }

        _uiState.update {
            it.copy(
                selectedTransitions = currentList,
                mediaItems = items,
                previewTransitionEffectId = null,
                toastMessage = "Transição '${targetEffect}' aplicada com sucesso!"
            )
        }
        saveCurrentState()
    }

    fun toggleTransitionSelection(effectId: String) {
        previewTransition(effectId)
    }

    fun toggleMultiSelectTransitionsMode() {
        _uiState.update { it.copy(isMultiSelectTransitions = !it.isMultiSelectTransitions) }
    }

    fun selectAllTransitions() {
        val allIds = TransitionEffect.ALL_TRANSITIONS.map { it.id }
        _uiState.update { it.copy(selectedTransitions = allIds) }
        saveCurrentState()
    }

    fun addAudioUri(uri: Uri, context: Context) {
        val trackName = uri.lastPathSegment?.takeLast(25) ?: "Trilha Sonora"
        val newTrack = AudioTrack(
            id = java.util.UUID.randomUUID().toString(),
            uri = uri.toString(),
            name = trackName
        )
        val updatedTracks = listOf(newTrack)
        _uiState.update { it.copy(audioTracks = updatedTracks, toastMessage = "Trilha sonora adicionada!") }
        saveCurrentState()
    }

    fun removeAudioTrack(index: Int) {
        val tracks = _uiState.value.audioTracks.toMutableList()
        if (index in tracks.indices) {
            tracks.removeAt(index)
            _uiState.update { it.copy(audioTracks = tracks) }
            saveCurrentState()
        }
    }

    fun addMediaUris(uris: List<Uri>, context: Context) {
        val newItems = MediaHelper.processFileUris(context, uris)
        if (newItems.isEmpty()) return
        val currentItems = _uiState.value.mediaItems.toMutableList()
        currentItems.addAll(newItems)
        val total = currentItems.sumOf { it.durationMs }
        val missingIdx = findFirstMissingAnimationIndex(currentItems)
        _uiState.update {
            it.copy(
                mediaItems = currentItems,
                totalDurationMs = total,
                missingAnimationIndex = missingIdx
            )
        }
        saveCurrentState()
    }

    fun addFolderUri(treeUri: Uri, context: Context) {
        val newItems = MediaHelper.processFolderUri(context, treeUri)
        if (newItems.isEmpty()) {
            showToast("Nenhuma foto ou vídeo encontrado na pasta.")
            return
        }
        val currentItems = _uiState.value.mediaItems.toMutableList()
        currentItems.addAll(newItems)
        val total = currentItems.sumOf { it.durationMs }
        val missingIdx = findFirstMissingAnimationIndex(currentItems)
        _uiState.update {
            it.copy(
                mediaItems = currentItems,
                totalDurationMs = total,
                missingAnimationIndex = missingIdx
            )
        }
        saveCurrentState()
    }

    fun removeMediaItem(index: Int) {
        val currentItems = _uiState.value.mediaItems.toMutableList()
        if (index in currentItems.indices) {
            currentItems.removeAt(index)
            val newIdx = (index - 1).coerceAtLeast(0)
            val total = currentItems.sumOf { it.durationMs }
            val missingIdx = findFirstMissingAnimationIndex(currentItems)
            _uiState.update {
                it.copy(
                    mediaItems = currentItems,
                    selectedMediaIndex = newIdx,
                    totalDurationMs = total,
                    missingAnimationIndex = missingIdx
                )
            }
            saveCurrentState()
        }
    }

    fun setAspectRatio(aspectRatio: AspectRatio) {
        val updatedExport = _uiState.value.exportConfig.copy(aspectRatio = aspectRatio)
        _uiState.update { it.copy(aspectRatio = aspectRatio, exportConfig = updatedExport) }
        saveCurrentState()
    }

    fun updateExportConfig(resolution: ExportResolution, fps: Int) {
        val updated = _uiState.value.exportConfig.copy(resolution = resolution, fps = fps)
        _uiState.update { it.copy(exportConfig = updated) }
        saveCurrentState()
    }

    fun autoSyncTimelineWithAudio() {
        val audioTrack = _uiState.value.audioTracks.firstOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingAudio = true) }
            val pauses = AudioSyncEngine.detectAudioPauses(getApplication(), audioTrack.uri)
            val syncedItems = AudioSyncEngine.syncMediaToAudioPauses(_uiState.value.mediaItems, pauses)
            val total = syncedItems.sumOf { it.durationMs }

            _uiState.update {
                it.copy(
                    mediaItems = syncedItems,
                    totalDurationMs = total,
                    isAnalyzingAudio = false,
                    toastMessage = "Linha do tempo sincronizada com as pausas do áudio!"
                )
            }
            saveCurrentState()
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    private fun play() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true) }
        playbackJob = viewModelScope.launch {
            val fpsDelay = 33L // ~30 FPS
            while (_uiState.value.isPlaying) {
                delay(fpsDelay)
                val newTime = _uiState.value.currentTimeMs + fpsDelay
                if (newTime >= _uiState.value.totalDurationMs) {
                    _uiState.update { it.copy(currentTimeMs = 0L, isPlaying = false) }
                    break
                } else {
                    _uiState.update { it.copy(currentTimeMs = newTime) }
                }
            }
        }
    }

    fun pause() {
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(timeMs: Long) {
        val clamped = timeMs.coerceIn(0L, _uiState.value.totalDurationMs)
        _uiState.update { it.copy(currentTimeMs = clamped) }
    }

    fun skipToStart() {
        pause()
        seekTo(0L)
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun saveCurrentState() {
        val currentProj = _uiState.value.project ?: return
        viewModelScope.launch {
            val updatedEntity = currentProj.copy(
                aspectRatioName = _uiState.value.aspectRatio.name,
                exportResolutionName = _uiState.value.exportConfig.resolution.name,
                exportFps = _uiState.value.exportConfig.fps,
                mediaItemsJson = JsonUtil.serializeMediaItems(_uiState.value.mediaItems),
                audioTracksJson = JsonUtil.serializeAudioTracks(_uiState.value.audioTracks),
                selectedTransitionsJson = JsonUtil.serializeTransitions(_uiState.value.selectedTransitions)
            )
            repository.saveProject(updatedEntity)
            _uiState.update { it.copy(project = updatedEntity) }
        }
    }
}
