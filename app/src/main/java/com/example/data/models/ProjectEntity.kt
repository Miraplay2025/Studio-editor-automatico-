package com.example.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "Novo Projeto",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val aspectRatioName: String = AspectRatio.RATIO_9_16.name,
    val exportResolutionName: String = ExportResolution.RES_720P.name,
    val exportFps: Int = 30,
    val selectedTransitionsJson: String = "[\"CROSSFADE\"]",
    val mediaItemsJson: String = "[]",
    val audioTracksJson: String = "[]",
    val renderedVideoPath: String? = null,
    val thumbnailUri: String? = null
)
