package com.example.data.model

enum class CameraAnimation(val id: String, val label: String) {
    NONE("NONE", "Nenhuma"),
    PAN_RIGHT("PAN_RIGHT", "Pan Direita"),
    PAN_LEFT("PAN_LEFT", "Pan Esquerda"),
    PAN_UP("PAN_UP", "Pan Cima"),
    PAN_DOWN("PAN_DOWN", "Pan Baixo"),
    ZOOM_IN("ZOOM_IN", "Zoom In"),
    ZOOM_OUT("ZOOM_OUT", "Zoom Out"),
    DIAGONAL_TOP_LEFT("DIAGONAL_TOP_LEFT", "Diagonal Sup-Esq"),
    DIAGONAL_TOP_RIGHT("DIAGONAL_TOP_RIGHT", "Diagonal Sup-Dir"),
    DIAGONAL_BOTTOM_LEFT("DIAGONAL_BOTTOM_LEFT", "Diagonal Inf-Esq"),
    DIAGONAL_BOTTOM_RIGHT("DIAGONAL_BOTTOM_RIGHT", "Diagonal Inf-Dir");

    companion object {
        fun fromId(id: String): CameraAnimation {
            return entries.firstOrNull { it.id == id } ?: NONE
        }
    }
}
