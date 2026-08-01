package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailUri: String? = null,
    val mediaCount: Int = 0,
    val totalDurationSeconds: Double = 0.0,
    val audioNarrationUrisJson: String = "[]",
    val activeTransitionsJson: String = "[\"CROSS_DISSOLVE\",\"FADE\",\"SLIDE_LEFT\"]",
    val transitionDurationSeconds: Float = 1.0f,
    val isDraft: Boolean = true
)

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val uri: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val durationSeconds: Double = 3.0,
    val animationType: String = "NONE", // CameraAnimation id
    val orderIndex: Int = 0
)
