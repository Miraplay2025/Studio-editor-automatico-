package com.example.data.models

enum class AspectRatio(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_9_16("9:16 Vertical", 9, 16),
    RATIO_16_9("16:9 Horizontal", 16, 9),
    RATIO_1_1("1:1 Quadrado", 1, 1)
}

enum class ExportResolution(val label: String, val baseHeight: Int) {
    RES_320P("320p", 320),
    RES_480P("480p", 480),
    RES_720P("720p (HD)", 720),
    RES_1080P("1080p (Full HD)", 1080);

    fun getDimensions(aspectRatio: AspectRatio): Pair<Int, Int> {
        return when (aspectRatio) {
            AspectRatio.RATIO_9_16 -> {
                val h = if (baseHeight % 2 == 0) baseHeight else baseHeight + 1
                val w = ((h * 9) / 16) / 2 * 2
                Pair(w.coerceAtLeast(180), h)
            }
            AspectRatio.RATIO_16_9 -> {
                val h = if (baseHeight % 2 == 0) baseHeight else baseHeight + 1
                val w = ((h * 16) / 9) / 2 * 2
                Pair(w, h)
            }
            AspectRatio.RATIO_1_1 -> {
                val side = if (baseHeight % 2 == 0) baseHeight else baseHeight + 1
                Pair(side, side)
            }
        }
    }
}

data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.RES_720P,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_9_16,
    val fps: Int = 30
)
