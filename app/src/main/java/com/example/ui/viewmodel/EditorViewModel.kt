package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.OfflineSpeechVadEngine
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.repository.ProjectRepository
import com.example.service.VideoRenderService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository = ProjectRepository(AppDatabase.getInstance(application).projectDao())
    private val vadEngine = OfflineSpeechVadEngine(application)

    private val _currentProjectId = MutableStateFlow<String?>(null)

    private val _projectTitle = MutableStateFlow("Projeto Sem Título")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    val mediaItems: StateFlow<List<MediaItemEntity>> = _mediaItems.asStateFlow()

    private val _selectedItemIndex = MutableStateFlow(0)
    val selectedItemIndex: StateFlow<Int> = _selectedItemIndex.asStateFlow()

    // 20 Transitions
    private val _transitionConfigs = MutableStateFlow(
        TransitionType.entries.map { type ->
            TransitionConfigItem(
                type = type,
                isActive = (type == TransitionType.CROSS_DISSOLVE || type == TransitionType.FADE || type == TransitionType.SLIDE_LEFT)
            )
        }
    )
    val transitionConfigs: StateFlow<List<TransitionConfigItem>> = _transitionConfigs.asStateFlow()

    private val _transitionDuration = MutableStateFlow(1.0f)
    val transitionDuration: StateFlow<Float> = _transitionDuration.asStateFlow()

    // Audio narration
    private val _audioUris = MutableStateFlow<List<Uri>>(emptyList())
    val audioUris: StateFlow<List<Uri>> = _audioUris.asStateFlow()

    private val _audioSegments = MutableStateFlow<List<AudioSegment>>(emptyList())
    val audioSegments: StateFlow<List<AudioSegment>> = _audioSegments.asStateFlow()

    private var narrationFile: File? = null

    // Export Modal State
    private val _exportOptions = MutableStateFlow(ExportOptions())
    val exportOptions: StateFlow<ExportOptions> = _exportOptions.asStateFlow()

    // Render Progress State from Service
    val renderProgressState: StateFlow<RenderProgressState> = VideoRenderService.renderState

    // Floating Red Arrow validation
    val hasUnassignedAnimation: StateFlow<Boolean> = _mediaItems.map { items ->
        items.any { it.mediaType == "IMAGE" && it.animationType == "NONE" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val firstUnassignedIndex: StateFlow<Int> = _mediaItems.map { items ->
        items.indexOfFirst { it.mediaType == "IMAGE" && it.animationType == "NONE" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    fun loadProject(projectId: String) {
        _currentProjectId.value = projectId
        viewModelScope.launch {
            val projectWithMedia = repository.getProject(projectId)
            if (projectWithMedia != null) {
                _projectTitle.value = projectWithMedia.project.title
                _mediaItems.value = projectWithMedia.mediaItems.sortedBy { it.orderIndex }
                _transitionDuration.value = projectWithMedia.project.transitionDurationSeconds
            }
        }
    }

    fun saveProjectDraft() {
        val projectId = _currentProjectId.value ?: return
        viewModelScope.launch {
            val activeIds = _transitionConfigs.value.filter { it.isActive }.map { it.type.id }
            val activeJson = activeIds.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

            val projectEntity = ProjectEntity(
                id = projectId,
                title = _projectTitle.value,
                updatedAt = System.currentTimeMillis(),
                thumbnailUri = _mediaItems.value.firstOrNull()?.uri,
                mediaCount = _mediaItems.value.size,
                totalDurationSeconds = _mediaItems.value.sumOf { it.durationSeconds },
                activeTransitionsJson = activeJson,
                transitionDurationSeconds = _transitionDuration.value,
                isDraft = true
            )
            repository.saveProject(projectEntity, _mediaItems.value)
        }
    }

    fun selectMediaItem(index: Int) {
        if (index in _mediaItems.value.indices) {
            _selectedItemIndex.value = index
        }
    }

    fun applyCameraAnimationToSelectedItem(animation: CameraAnimation) {
        val index = _selectedItemIndex.value
        val items = _mediaItems.value.toMutableList()
        if (index in items.indices) {
            val oldItem = items[index]
            items[index] = oldItem.copy(animationType = animation.id)
            _mediaItems.value = items
            saveProjectDraft()
        }
    }

    fun toggleTransitionActive(type: TransitionType) {
        val updated = _transitionConfigs.value.map { item ->
            if (item.type == type) item.copy(isActive = !item.isActive) else item
        }
        _transitionConfigs.value = updated
        saveProjectDraft()
    }

    fun updateTransitionDuration(durationSec: Float) {
        _transitionDuration.value = durationSec
        saveProjectDraft()
    }

    fun addMediaItems(uris: List<Uri>) {
        val projectId = _currentProjectId.value ?: return
        val currentSize = _mediaItems.value.size
        val newEntities = uris.mapIndexed { idx, uri ->
            val uriStr = uri.toString()
            val isVideo = uriStr.contains("video") || uriStr.endsWith(".mp4")
            MediaItemEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                uri = uriStr,
                mediaType = if (isVideo) "VIDEO" else "IMAGE",
                durationSeconds = 3.0,
                animationType = "NONE",
                orderIndex = currentSize + idx
            )
        }
        _mediaItems.value = _mediaItems.value + newEntities
        saveProjectDraft()
    }

    fun removeMediaItem(index: Int) {
        val items = _mediaItems.value.toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            val reordered = items.mapIndexed { i, item -> item.copy(orderIndex = i) }
            _mediaItems.value = reordered
            if (_selectedItemIndex.value >= reordered.size) {
                _selectedItemIndex.value = (reordered.size - 1).coerceAtLeast(0)
            }
            saveProjectDraft()
        }
    }

    fun uploadAndProcessAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _audioUris.value = _audioUris.value + uris

        viewModelScope.launch {
            val concatenatedFile = vadEngine.concatenateAudioFiles(_audioUris.value)
            narrationFile = concatenatedFile
            val detectedSegments = vadEngine.analyzeAudioPhrasesAndPauses(concatenatedFile)
            _audioSegments.value = detectedSegments

            // Auto-sync timeline durations with detected phrase timestamps
            val syncedItems = vadEngine.syncTimelineWithAudio(_mediaItems.value, detectedSegments)
            _mediaItems.value = syncedItems
            saveProjectDraft()
        }
    }

    fun updateExportOptions(resolution: ExportResolution, quality: ExportQuality, fps: ExportFps) {
        _exportOptions.value = ExportOptions(resolution, quality, fps)
    }

    fun startAutoEditing(context: Context, onValidationError: (String) -> Unit) {
        val activeTransitions = _transitionConfigs.value.filter { it.isActive }.map { it.type }
        if (activeTransitions.isEmpty()) {
            onValidationError("Selecione pelo menos 1 transição ativa!")
            return
        }

        if (_mediaItems.value.isEmpty()) {
            onValidationError("Adicione pelo menos 1 imagem ou vídeo à timeline!")
            return
        }

        // Auto assign random camera animation if user left any unassigned
        val updatedItems = _mediaItems.value.map { item ->
            if (item.mediaType == "IMAGE" && item.animationType == "NONE") {
                val randomAnim = CameraAnimation.entries.filter { it != CameraAnimation.NONE }.random()
                item.copy(animationType = randomAnim.id)
            } else {
                item
            }
        }
        _mediaItems.value = updatedItems
        saveProjectDraft()

        val serviceIntent = Intent(context, VideoRenderService::class.java)
        context.startForegroundService(serviceIntent)

        // Bind to service and run render
        val binder = VideoRenderService()
        binder.startRenderTask(
            mediaItems = _mediaItems.value,
            activeTransitions = activeTransitions,
            transitionDurationSec = _transitionDuration.value,
            narrationAudioFile = narrationFile,
            exportOptions = _exportOptions.value
        )
    }

    fun cancelRendering() {
        val binder = VideoRenderService()
        binder.cancelRendering()
    }
}
