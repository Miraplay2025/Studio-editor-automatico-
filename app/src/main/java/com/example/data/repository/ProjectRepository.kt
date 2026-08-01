package com.example.data.repository

import com.example.data.db.*
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectWithMedia>> = projectDao.getAllProjectsWithMedia()

    fun observeProject(projectId: String): Flow<ProjectWithMedia?> {
        return projectDao.observeProjectWithMediaById(projectId)
    }

    suspend fun getProject(projectId: String): ProjectWithMedia? {
        return projectDao.getProjectWithMediaById(projectId)
    }

    suspend fun saveProject(project: ProjectEntity, items: List<MediaItemEntity>) {
        projectDao.updateProjectAndItems(project, items)
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
    }
}
