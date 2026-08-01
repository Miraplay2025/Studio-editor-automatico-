package com.example.data.models

import java.util.UUID

data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String = "Música de Fundo",
    val durationMs: Long = 10000L,
    val volume: Float = 1.0f,
    val detectedPausesMs: List<Long> = emptyList()
)
