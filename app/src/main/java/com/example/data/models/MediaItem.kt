package com.example.data.models

import java.util.UUID

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class MotionAnimation(val displayName: String, val description: String) {
    NONE("Nenhuma", "Sem animação de movimento"),
    SWING("Balançar", "Oscilação suave estilo pêndulo"),
    SHRINK("Encolher", "Redução gradual de escala"),
    YOYO("Ioiô", "Expansão e contração contínua"),
    BOUNCE("Quicar", "Efeito de mola e impacto suave"),
    PULSE("Pulsar", "Batimento rítmico de escala"),
    FADE_IN_OUT("Esmaecer", "Entrada e saída suave de opacidade"),
    SLIDE_IN("Deslizar", "Entrada lateral dinâmica")
}

enum class CameraMotion(val displayName: String, val description: String) {
    NONE("Nenhum", "Câmera estática"),
    ZOOM_IN("Zoom In", "Aproximação central progressiva"),
    ZOOM_OUT("Zoom Out", "Afastamento central progressivo"),
    PAN_LEFT("Pan Esquerda", "Deslocamento de câmera para esquerda"),
    PAN_RIGHT("Pan Direita", "Deslocamento de câmera para direita"),
    PAN_UP("Pan Cima", "Deslocamento de câmera para cima"),
    PAN_DOWN("Pan Baixo", "Deslocamento de câmera para baixo")
}

data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val type: MediaType = MediaType.IMAGE,
    val title: String = "Mídia",
    val durationMs: Long = 3000L,
    val motionAnimation: MotionAnimation = MotionAnimation.NONE,
    val motionDurationMs: Long = 1500L,
    val cameraMotion: CameraMotion = CameraMotion.NONE,
    val transitionOverride: String? = null
)
