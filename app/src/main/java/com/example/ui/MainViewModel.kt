package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AspectRatioOption
import com.example.data.AudioTrack
import com.example.data.CameraAnimation
import com.example.data.ExportFps
import com.example.data.ExportQuality
import com.example.data.MediaClip
import com.example.data.MediaType
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import com.example.data.RenderState
import com.example.data.TransitionType
import com.example.data.VideoResolution
import com.example.data.ZoomAnimation
import com.example.engine.AudioSpeechAnalyzer
import com.example.engine.RenderingForegroundService
import com.example.engine.VideoRendererEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository
    val projectsList: StateFlow<List<ProjectEntity>>

    init {
        val dao = AppDatabase.getDatabase(application).projectDao()
        repository = ProjectRepository(dao)
        projectsList = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Active Project Draft State
    private val _currentProjectId = MutableStateFlow<Long>(0L)
    val currentProjectId: StateFlow<Long> = _currentProjectId.asStateFlow()

    private val _projectTitle = MutableStateFlow("Novo Projeto")
    val projectTitle: StateFlow<String> = _projectTitle.asStateFlow()

    private val _mediaClips = MutableStateFlow<List<MediaClip>>(emptyList())
    val mediaClips: StateFlow<List<MediaClip>> = _mediaClips.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioOption.RATIO_16_9)
    val aspectRatio: StateFlow<AspectRatioOption> = _aspectRatio.asStateFlow()

    // Transitions pool (User checked transitions)
    private val _selectedTransitions = MutableStateFlow<Set<TransitionType>>(
        setOf(TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT)
    )
    val selectedTransitions: StateFlow<Set<TransitionType>> = _selectedTransitions.asStateFlow()

    private val _isTransitionSelectionMode = MutableStateFlow(false)
    val isTransitionSelectionMode: StateFlow<Boolean> = _isTransitionSelectionMode.asStateFlow()

    // Audio narration track
    private val _audioTrack = MutableStateFlow<AudioTrack?>(null)
    val audioTrack: StateFlow<AudioTrack?> = _audioTrack.asStateFlow()

    private val _isAnalyzingAudio = MutableStateFlow(false)
    val isAnalyzingAudio: StateFlow<Boolean> = _isAnalyzingAudio.asStateFlow()

    // Render State & Export Options
    private val _renderState = MutableStateFlow<RenderState>(RenderState.Idle)
    val renderState: StateFlow<RenderState> = _renderState.asStateFlow()

    private val _showAutoEditModal = MutableStateFlow(false)
    val showAutoEditModal: StateFlow<Boolean> = _showAutoEditModal.asStateFlow()

    private val _showExportModal = MutableStateFlow(false)
    val showExportModal: StateFlow<Boolean> = _showExportModal.asStateFlow()

    private val _exportResolution = MutableStateFlow(VideoResolution.RES_720P)
    val exportResolution: StateFlow<VideoResolution> = _exportResolution.asStateFlow()

    private val _exportQuality = MutableStateFlow(ExportQuality.MEDIUM)
    val exportQuality: StateFlow<ExportQuality> = _exportQuality.asStateFlow()

    private val _exportFps = MutableStateFlow(ExportFps.FPS_30)
    val exportFps: StateFlow<ExportFps> = _exportFps.asStateFlow()

    private val _lastExportedFile = MutableStateFlow<File?>(null)
    val lastExportedFile: StateFlow<File?> = _lastExportedFile.asStateFlow()

    // UI Event messages (Toast/Alerts)
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var renderJob: Job? = null
    private val speechAnalyzer = AudioSpeechAnalyzer(application)
    private val rendererEngine = VideoRendererEngine(application)

    fun createNewProject() {
        _currentProjectId.value = 0L
        _projectTitle.value = "Projeto ${System.currentTimeMillis() % 1000}"
        _mediaClips.value = emptyList()
        _selectedClipId.value = null
        _aspectRatio.value = AspectRatioOption.RATIO_16_9
        _audioTrack.value = null
        _renderState.value = RenderState.Idle
        _lastExportedFile.value = null
        _selectedTransitions.value = setOf(
            TransitionType.FADE, TransitionType.DISSOLVE, TransitionType.SLIDE_LEFT, TransitionType.SLIDE_RIGHT
        )
    }

    fun loadProject(entity: ProjectEntity) {
        _currentProjectId.value = entity.id
        _projectTitle.value = entity.title
        _aspectRatio.value = AspectRatioOption.values().find { it.label == entity.aspectRatio } ?: AspectRatioOption.RATIO_16_9

        // Parse items JSON
        val clips = mutableListOf<MediaClip>()
        try {
            val array = JSONArray(entity.itemsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                clips.add(
                    MediaClip(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        uriString = obj.optString("uriString"),
                        mediaType = if (obj.optString("type") == "VIDEO") MediaType.VIDEO else MediaType.IMAGE,
                        durationSec = obj.optDouble("durationSec", 3.5).toFloat(),
                        cameraAnim = CameraAnimation.values().find { it.name == obj.optString("cameraAnim") } ?: CameraAnimation.NONE,
                        zoomAnim = ZoomAnimation.values().find { it.name == obj.optString("zoomAnim") } ?: ZoomAnimation.NONE,
                        assignedTransition = TransitionType.values().find { it.id == obj.optString("transition") }
                    )
                )
            }
        } catch (_: Exception) {}

        _mediaClips.value = clips
        _selectedClipId.value = clips.firstOrNull()?.id

        if (entity.audioUri.isNotEmpty()) {
            _audioTrack.value = AudioTrack(uriString = entity.audioUri, title = "Narração Gravada", durationMs = 0)
        } else {
            _audioTrack.value = null
        }
    }

    fun saveCurrentProjectDraft() {
        viewModelScope.launch {
            if (_mediaClips.value.isEmpty() && _currentProjectId.value == 0L) return@launch

            val jsonArray = JSONArray()
            for (clip in _mediaClips.value) {
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("uriString", clip.uriString)
                    put("type", clip.mediaType.name)
                    put("durationSec", clip.durationSec.toDouble())
                    put("cameraAnim", clip.cameraAnim.name)
                    put("zoomAnim", clip.zoomAnim.name)
                    put("transition", clip.assignedTransition?.id ?: "")
                }
                jsonArray.put(obj)
            }

            val transitionsArray = JSONArray()
            _selectedTransitions.value.forEach { transitionsArray.put(it.id) }

            val thumbnail = _mediaClips.value.firstOrNull()?.uriString ?: ""

            val entity = ProjectEntity(
                id = _currentProjectId.value,
                title = _projectTitle.value,
                updatedAt = System.currentTimeMillis(),
                thumbnailPath = thumbnail,
                aspectRatio = _aspectRatio.value.label,
                itemsJson = jsonArray.toString(),
                selectedTransitionsJson = transitionsArray.toString(),
                audioUri = _audioTrack.value?.uriString ?: "",
                exportPath = _lastExportedFile.value?.absolutePath ?: ""
            )

            val savedId = repository.saveProject(entity)
            _currentProjectId.value = savedId
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _toastEvent.emit("Projeto excluído")
        }
    }

    fun addMediaUris(uris: List<Uri>) {
        val current = _mediaClips.value.toMutableList()
        for (uri in uris) {
            val clip = MediaClip(
                uriString = uri.toString(),
                mediaType = MediaType.IMAGE,
                durationSec = 3.5f,
                cameraAnim = CameraAnimation.NONE,
                zoomAnim = ZoomAnimation.NONE
            )
            current.add(clip)
        }
        _mediaClips.value = current
        if (_selectedClipId.value == null) {
            _selectedClipId.value = current.firstOrNull()?.id
        }
        saveCurrentProjectDraft()
    }

    fun removeClip(clipId: String) {
        val current = _mediaClips.value.filter { it.id != clipId }
        _mediaClips.value = current
        if (_selectedClipId.value == clipId) {
            _selectedClipId.value = current.firstOrNull()?.id
        }
        saveCurrentProjectDraft()
    }

    fun selectClip(clipId: String) {
        _selectedClipId.value = clipId
    }

    fun setAspectRatio(option: AspectRatioOption) {
        _aspectRatio.value = option
        saveCurrentProjectDraft()
    }

    // Toggle Camera Animation Rule: Max 1 Camera Anim per image. Clicking again removes it.
    fun toggleCameraAnimation(clipId: String, animation: CameraAnimation) {
        val updated = _mediaClips.value.map { clip ->
            if (clip.id == clipId) {
                val newAnim = if (clip.cameraAnim == animation) CameraAnimation.NONE else animation
                clip.copy(cameraAnim = newAnim)
            } else clip
        }
        _mediaClips.value = updated
    }

    // Toggle Zoom Animation Rule: Max 1 Zoom Anim per image. Clicking again removes it.
    fun toggleZoomAnimation(clipId: String, animation: ZoomAnimation) {
        val updated = _mediaClips.value.map { clip ->
            if (clip.id == clipId) {
                val newZoom = if (clip.zoomAnim == animation) ZoomAnimation.NONE else animation
                clip.copy(zoomAnim = newZoom)
            } else clip
        }
        _mediaClips.value = updated
    }

    // Transition Pool Toggle
    fun toggleTransitionSelectionMode() {
        _isTransitionSelectionMode.value = !_isTransitionSelectionMode.value
    }

    fun toggleTransitionInPool(transition: TransitionType) {
        val current = _selectedTransitions.value.toMutableSet()
        if (current.contains(transition)) {
            // Safety check: keep at least 1 checked
            if (current.size > 1) {
                current.remove(transition)
            } else {
                viewModelScope.launch {
                    _toastEvent.emit("É necessário manter pelo menos 1 transição selecionada!")
                }
            }
        } else {
            current.add(transition)
        }
        _selectedTransitions.value = current
    }

    fun assignTransitionToSelectedClip(transition: TransitionType) {
        val selId = _selectedClipId.value ?: return
        val updated = _mediaClips.value.map { clip ->
            if (clip.id == selId) {
                val newTrans = if (clip.assignedTransition == transition) null else transition
                clip.copy(assignedTransition = newTrans)
            } else clip
        }
        _mediaClips.value = updated
    }

    // Audio & Narration Sync
    fun setAudioTrack(uriString: String, title: String) {
        _audioTrack.value = AudioTrack(uriString = uriString, title = title, durationMs = 0)
        saveCurrentProjectDraft()
    }

    fun removeAudioTrack() {
        _audioTrack.value = null
        saveCurrentProjectDraft()
    }

    fun syncImagesToSpeechPauses() {
        val audio = _audioTrack.value ?: return
        if (_mediaClips.value.isEmpty()) return

        viewModelScope.launch {
            _isAnalyzingAudio.value = true
            try {
                val markers = speechAnalyzer.analyzeAudioPauses(Uri.parse(audio.uriString))
                if (markers.isNotEmpty()) {
                    val clips = _mediaClips.value.toMutableList()
                    var prevTimestamp = 0f

                    for (i in clips.indices) {
                        if (i < markers.size) {
                            val markTimestamp = markers[i].timestampSec
                            val dur = (markTimestamp - prevTimestamp).coerceIn(1.5f, 8.0f)
                            clips[i] = clips[i].copy(durationSec = dur)
                            prevTimestamp = markTimestamp
                        } else {
                            clips[i] = clips[i].copy(durationSec = 3.5f)
                        }
                    }

                    _mediaClips.value = clips
                    _toastEvent.emit("Sincronização por IA concluída! Duração dos quadros ajustada às pausas de fala.")
                    saveCurrentProjectDraft()
                } else {
                    _toastEvent.emit("Pausas de fala analisadas. Duração uniforme configurada.")
                }
            } catch (e: Exception) {
                _toastEvent.emit("Erro ao analisar áudio para sincronização: ${e.message}")
            } finally {
                _isAnalyzingAudio.value = false
            }
        }
    }

    // Auto-Editing Execution
    fun startAutoEditing() {
        // Safety Lock Check: Must have at least 1 transition checked
        if (_selectedTransitions.value.isEmpty()) {
            viewModelScope.launch {
                _toastEvent.emit("Ative pelo menos 1 transição para iniciar a edição automática!")
            }
            return
        }

        if (_mediaClips.value.isEmpty()) {
            viewModelScope.launch {
                _toastEvent.emit("Adicione mídias na timeline para editar!")
            }
            return
        }

        // Randomly assign transitions & camera/zoom animations from selected pool
        val pool = _selectedTransitions.value.toList()
        val cameraOptions = CameraAnimation.values().filter { it != CameraAnimation.NONE }
        val zoomOptions = ZoomAnimation.values().filter { it != ZoomAnimation.NONE }

        val randomizedClips = _mediaClips.value.mapIndexed { index, clip ->
            val randomTransition = pool[Random.nextInt(pool.size)]
            val randomCam = cameraOptions[Random.nextInt(cameraOptions.size)]
            val randomZoom = zoomOptions[Random.nextInt(zoomOptions.size)]

            clip.copy(
                assignedTransition = randomTransition,
                cameraAnim = if (clip.cameraAnim == CameraAnimation.NONE) randomCam else clip.cameraAnim,
                zoomAnim = if (clip.zoomAnim == ZoomAnimation.NONE) randomZoom else clip.zoomAnim
            )
        }

        _mediaClips.value = randomizedClips
        _showAutoEditModal.value = true

        executeRenderingProcess()
    }

    private fun executeRenderingProcess() {
        renderJob?.cancel()

        renderJob = viewModelScope.launch {
            val app = getApplication<Application>()
            RenderingForegroundService.startService(app, "Iniciando edição automática...", 0)

            val logsList = mutableListOf<String>()

            _renderState.value = RenderState.Processing(0f, "Iniciando", logsList)

            try {
                val outputFile = rendererEngine.renderVideo(
                    clips = _mediaClips.value,
                    aspectRatio = _aspectRatio.value,
                    resolution = _exportResolution.value,
                    quality = _exportQuality.value,
                    fps = _exportFps.value,
                    audioUriString = _audioTrack.value?.uriString,
                    onProgress = { progress ->
                        logsList.add("[${progress.currentStep}] ${progress.logMessage}")
                        _renderState.value = RenderState.Processing(
                            progress = progress.percent,
                            currentStep = progress.currentStep,
                            logs = logsList.toList()
                        )
                        RenderingForegroundService.startService(app, progress.logMessage, progress.percent.toInt())
                    }
                )

                _lastExportedFile.value = outputFile
                _renderState.value = RenderState.Success(outputFile.absolutePath, logsList)
                RenderingForegroundService.stopService(app)
                saveCurrentProjectDraft()

            } catch (e: InterruptedException) {
                _renderState.value = RenderState.Idle
                RenderingForegroundService.stopService(app)
                _toastEvent.emit("Renderização cancelada.")
            } catch (e: Exception) {
                _renderState.value = RenderState.Error("Falha na renderização: ${e.localizedMessage ?: e.message}")
                RenderingForegroundService.stopService(app)
            }
        }
    }

    fun cancelRendering() {
        renderJob?.cancel()
        _renderState.value = RenderState.Idle
        _showAutoEditModal.value = false
        RenderingForegroundService.stopService(getApplication())
    }

    fun dismissAutoEditModal() {
        _showAutoEditModal.value = false
    }

    fun openExportModal() {
        _showExportModal.value = true
    }

    fun dismissExportModal() {
        _showExportModal.value = false
    }

    fun setExportResolution(resolution: VideoResolution) {
        _exportResolution.value = resolution
    }

    fun setExportQuality(quality: ExportQuality) {
        _exportQuality.value = quality
    }

    fun setExportFps(fps: ExportFps) {
        _exportFps.value = fps
    }
}
