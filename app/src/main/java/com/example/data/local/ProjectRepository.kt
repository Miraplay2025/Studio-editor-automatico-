package com.example.data.local

import com.example.data.models.ProjectEntity
import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    suspend fun getProjectById(id: String): ProjectEntity? {
        return projectDao.getProjectById(id)
    }

    suspend fun saveProject(project: ProjectEntity) {
        projectDao.insertOrUpdateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
    }
}
