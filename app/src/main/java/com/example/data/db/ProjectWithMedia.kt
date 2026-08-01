package com.example.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithMedia(
    @Embedded val project: ProjectEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val mediaItems: List<MediaItemEntity>
)
