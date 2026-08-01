package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjectsWithMedia(): Flow<List<ProjectWithMedia>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectWithMediaById(projectId: String): ProjectWithMedia?

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId LIMIT 1")
    fun observeProjectWithMediaById(projectId: String): Flow<ProjectWithMedia?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items WHERE projectId = :projectId")
    suspend fun deleteMediaItemsForProject(projectId: String)

    @Transaction
    suspend fun updateProjectAndItems(project: ProjectEntity, items: List<MediaItemEntity>) {
        insertProject(project)
        deleteMediaItemsForProject(project.id)
        insertMediaItems(items)
    }

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)
}
