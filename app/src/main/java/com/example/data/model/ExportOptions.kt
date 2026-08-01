package com.example.data.model

data class AudioSegment(
    val id: Int,
    val text: String,
    val startTimeSeconds: Double,
    val endTimeSeconds: Double,
    val durationSeconds: Double,
    val isSilence: Boolean = false
) {
    val formattedTimestamp: String
        get() = String.format(
            "%02d:%05.2f - %02d:%05.2f",
            (startTimeSeconds / 60).toInt(),
            startTimeSeconds % 60,
            (endTimeSeconds / 60).toInt(),
            endTimeSeconds % 60
        )
}

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    RES_320P("320p (QVGA)", 426, 240),
    RES_480P("480p (SD)", 640, 360),
    RES_720P("720p (HD)", 1280, 720),
    RES_1080P("1080p (Full HD)", 1920, 1080)
}

enum class ExportQuality(val label: String, val bitrate: Int) {
    LOW("Baixa (1.5 Mbps)", 1_500_000),
    MEDIUM("Média (3.0 Mbps)", 3_000_000),
    HIGH("Alta (6.0 Mbps)", 6_000_000),
    MAXIMUM("Máxima (12.0 Mbps)", 12_000_000)
}

enum class ExportFps(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS (Cinema)"),
    FPS_30(30, "30 FPS (Padrão)"),
    FPS_48(48, "48 FPS (Suave)"),
    FPS_60(60, "60 FPS (Ultra Suave)")
}

data class ExportOptions(
    val resolution: ExportResolution = ExportResolution.RES_720P,
    val quality: ExportQuality = ExportQuality.MEDIUM,
    val fps: ExportFps = ExportFps.FPS_30
)

data class RenderLogMessage(
    val timestamp: String,
    val text: String
)

sealed class RenderProgressState {
    object Idle : RenderProgressState()
    data class Processing(
        val progressPercent: Int, // 0 to 100
        val currentStep: String,
        val logs: List<RenderLogMessage> = emptyList()
    ) : RenderProgressState()
    data class Success(val outputFilePath: String, val outputFileUri: String) : RenderProgressState()
    data class Error(val errorMessage: String) : RenderProgressState()
}
