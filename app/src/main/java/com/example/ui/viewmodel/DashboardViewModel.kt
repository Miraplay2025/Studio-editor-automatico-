package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.*
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    val projectsState: StateFlow<List<ProjectWithMedia>>

    init {
        val database = AppDatabase.getInstance(application)
        repository = ProjectRepository(database.projectDao())

        projectsState = repository.allProjects
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun createNewProjectFromUris(uris: List<Uri>, onProjectCreated: (String) -> Unit) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val projectTitle = "Projeto CineCut #${(1000..9999).random()}"

            val mediaEntities = uris.mapIndexed { idx, uri ->
                val uriStr = uri.toString()
                val isVideo = uriStr.contains("video") || uriStr.endsWith(".mp4")
                MediaItemEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    uri = uriStr,
                    mediaType = if (isVideo) "VIDEO" else "IMAGE",
                    durationSeconds = 3.0,
                    animationType = "NONE", // Default to unassigned to test floating red arrow validation
                    orderIndex = idx
                )
            }

            val projectEntity = ProjectEntity(
                id = projectId,
                title = projectTitle,
                thumbnailUri = uris.firstOrNull()?.toString(),
                mediaCount = mediaEntities.size,
                totalDurationSeconds = mediaEntities.sumOf { it.durationSeconds },
                isDraft = true
            )

            repository.saveProject(projectEntity, mediaEntities)
            onProjectCreated(projectId)
        }
    }
}
