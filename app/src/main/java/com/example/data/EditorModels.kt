package com.example.data

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

enum class CameraAnimation(val label: String, val iconName: String) {
    NONE("Nenhuma", "block"),
    MOVE_LEFT("Mover Esquerda", "arrow_back"),
    MOVE_RIGHT("Mover Direita", "arrow_forward"),
    MOVE_UP("Mover Cima", "arrow_upward"),
    MOVE_DOWN("Mover Baixo", "arrow_downward"),
    DIAG_UP_LEFT("Diag. Cima-Esq", "north_west"),
    DIAG_UP_RIGHT("Diag. Cima-Dir", "north_east"),
    DIAG_DOWN_LEFT("Diag. Baixo-Esq", "south_west"),
    DIAG_DOWN_RIGHT("Diag. Baixo-Dir", "south_east"),
    PAN_HORIZ("Pan Horizontal", "swap_horiz"),
    PAN_VERT("Pan Vertical", "swap_vert")
}

enum class ZoomAnimation(val label: String, val iconName: String) {
    NONE("Nenhum", "block"),
    ZOOM_IN("Zoom In", "zoom_in"),
    ZOOM_OUT("Zoom Out", "zoom_out"),
    PAN_LEFT("Pan Esq", "west"),
    PAN_RIGHT("Pan Dir", "east"),
    UP("Subir", "expand_less"),
    DOWN("Descer", "expand_more")
}

enum class TransitionType(val id: String, val label: String, val category: String) {
    FADE("fade", "Dissolver Fade", "Suave"),
    DISSOLVE("dissolve", "Cross Dissolve", "Suave"),
    SLIDE_LEFT("slide_left", "Deslizar Esquerda", "Movimento"),
    SLIDE_RIGHT("slide_right", "Deslizar Direita", "Movimento"),
    SLIDE_UP("slide_up", "Deslizar Cima", "Movimento"),
    SLIDE_DOWN("slide_down", "Deslizar Baixo", "Movimento"),
    ZOOM_IN("zoom_in", "Zoom Suave In", "Zoom"),
    ZOOM_OUT("zoom_out", "Zoom Suave Out", "Zoom"),
    WIPE_LEFT("wipe_left", "Varredura Esquerda", "Varredura"),
    WIPE_RIGHT("wipe_right", "Varredura Direita", "Varredura"),
    WIPE_UP("wipe_up", "Varredura Cima", "Varredura"),
    WIPE_DOWN("wipe_down", "Varredura Baixo", "Varredura"),
    PUSH_LEFT("push_left", "Empurrar Esquerda", "Empurrar"),
    PUSH_RIGHT("push_right", "Empurrar Direita", "Empurrar"),
    BLUR_FADE("blur_fade", "Desfocar & Fade", "Especial"),
    DIP_BLACK("dip_black", "Flash Preto", "Cor"),
    DIP_WHITE("dip_white", "Flash Branco", "Cor"),
    SPLIT_HORIZ("split_horiz", "Divisão Horizontal", "Divisão"),
    SPLIT_VERT("split_vert", "Divisão Vertical", "Divisão"),
    CIRCLE_CROP("circle_crop", "Revelar Círculo", "Forma")
}

enum class AspectRatioOption(val label: String, val ratio: Float, val width: Int, val height: Int) {
    RATIO_16_9("16:9", 16f / 9f, 1920, 1080),
    RATIO_9_16("9:16", 9f / 16f, 1080, 1920),
    RATIO_1_1("1:1", 1f, 1080, 1080),
    RATIO_4_5("4:5", 4f / 5f, 1080, 1350)
}

enum class VideoResolution(val label: String, val heightPx: Int) {
    RES_320P("320p", 320),
    RES_480P("480p", 480),
    RES_720P("720p (HD)", 720),
    RES_1080P("1080p (FHD)", 1080)
}

enum class ExportQuality(val label: String, val bitrateBps: Int) {
    MIN("Mínima", 1_000_000),
    MEDIUM("Média", 2_500_000),
    HIGH("Alta", 5_000_000),
    MAX("Máxima", 8_000_000)
}

enum class ExportFps(val label: String, val fpsValue: Int) {
    FPS_24("24 FPS", 24),
    FPS_30("30 FPS", 30),
    FPS_60("60 FPS", 60)
}

data class MediaClip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val mediaType: MediaType = MediaType.IMAGE,
    var durationSec: Float = 3.5f,
    var cameraAnim: CameraAnimation = CameraAnimation.NONE,
    var zoomAnim: ZoomAnimation = ZoomAnimation.NONE,
    var assignedTransition: TransitionType? = null
)

data class AudioTrack(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uriString: String,
    val title: String,
    val durationMs: Long
)

enum class ActiveSubMenu {
    NONE, RATIO, ANIMATION, ZOOM, TRANSITION, AUDIO
}

sealed class RenderState {
    object Idle : RenderState()
    data class Processing(val progress: Float, val currentStep: String, val logs: List<String>) : RenderState()
    data class Success(val outputFilePath: String, val logs: List<String>) : RenderState()
    data class Error(val message: String) : RenderState()
}
