package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.local.JsonUtil
import com.example.data.models.MediaItem
import com.example.data.models.ProjectEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository

    val allProjects: StateFlow<List<ProjectEntity>>

    init {
        val dao = AppDatabase.getDatabase(application).projectDao()
        repository = ProjectRepository(dao)
        allProjects = repository.allProjects.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
        }
    }

    fun createNewProject(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val newProject = ProjectEntity(
                title = "Projeto ${System.currentTimeMillis() % 10000}"
            )
            repository.saveProject(newProject)
            onCreated(newProject.id)
        }
    }

    fun createProjectWithMedia(
        title: String = "Novo Projeto",
        mediaItems: List<MediaItem>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            if (mediaItems.isEmpty()) return@launch

            val thumbnailUri = mediaItems.firstOrNull()?.uri
            val newProject = ProjectEntity(
                title = title,
                thumbnailUri = thumbnailUri,
                mediaItemsJson = JsonUtil.serializeMediaItems(mediaItems)
            )
            repository.saveProject(newProject)
            onCreated(newProject.id)
        }
    }
}
