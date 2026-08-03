package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val thumbnailPath: String = "",
    val aspectRatio: String = "16:9",
    val itemsJson: String = "[]",
    val selectedTransitionsJson: String = "[]",
    val audioUri: String = "",
    val exportPath: String = "",
    val isExported: Boolean = false
)
