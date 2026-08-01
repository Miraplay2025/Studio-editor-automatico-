package com.example.data.model

enum class TransitionType(val id: String, val label: String) {
    FADE("FADE", "Fade Simples"),
    CROSS_DISSOLVE("CROSS_DISSOLVE", "Cross Dissolve"),
    SLIDE_LEFT("SLIDE_LEFT", "Slide Smooth - Esquerda"),
    SLIDE_RIGHT("SLIDE_RIGHT", "Slide Smooth - Direita"),
    SLIDE_UP("SLIDE_UP", "Slide Smooth - Cima"),
    SLIDE_DOWN("SLIDE_DOWN", "Slide Smooth - Baixo"),
    WIPE_SOFT_LEFT("WIPE_SOFT_LEFT", "Wipe Soft - Esquerda"),
    WIPE_SOFT_RIGHT("WIPE_SOFT_RIGHT", "Wipe Soft - Direita"),
    ZOOM_BLUR("ZOOM_BLUR", "Zoom Blur Leve"),
    PUSH_LEFT("PUSH_LEFT", "Push Smooth - Esquerda"),
    PUSH_RIGHT("PUSH_RIGHT", "Push Smooth - Direita"),
    COLOR_PHASE("COLOR_PHASE", "Color Phase"),
    BLACK_FADE("BLACK_FADE", "Fade em Preto"),
    WHITE_FADE("WHITE_FADE", "Fade em Branco"),
    CIRCLE_CROP("CIRCLE_CROP", "Corte Circular"),
    WIPE_RADIAL("WIPE_RADIAL", "Varredura Radial"),
    DIP_BLACK("DIP_BLACK", "Mergulho no Preto"),
    PIXELATE_DISSOLVE("PIXELATE_DISSOLVE", "Dissolução de Pixels"),
    SOFT_EXPAND("SOFT_EXPAND", "Expansão Suave"),
    IRIS_ZOOM("IRIS_ZOOM", "Íris Zoom");

    companion object {
        fun fromId(id: String): TransitionType {
            return entries.firstOrNull { it.id == id } ?: CROSS_DISSOLVE
        }
    }
}

data class TransitionConfigItem(
    val type: TransitionType,
    val isActive: Boolean = true,
    val durationSeconds: Float = 1.0f
)
