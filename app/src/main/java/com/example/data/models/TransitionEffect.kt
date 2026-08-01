package com.example.data.models

data class TransitionEffect(
    val id: String,
    val name: String,
    val description: String,
    val durationMs: Long = 1000L
) {
    companion object {
        val ALL_TRANSITIONS = listOf(
            TransitionEffect("CROSSFADE", "Crossfade", "Transição suave de esmaecimento cruzado"),
            TransitionEffect("SLIDE_LEFT", "Deslizar Esquerda", "Deslocamento suave para esquerda"),
            TransitionEffect("SLIDE_RIGHT", "Deslizar Direita", "Deslocamento suave para direita"),
            TransitionEffect("SLIDE_UP", "Deslizar Cima", "Subida suave da nova imagem"),
            TransitionEffect("SLIDE_DOWN", "Deslizar Baixo", "Descida suave da nova imagem"),
            TransitionEffect("DISSOLVE", "Dissolver", "Efeito de fusão gradual de pixels"),
            TransitionEffect("WIPE_HORIZONTAL", "Cortina Horizontal", "Varredura lateral limpa"),
            TransitionEffect("WIPE_VERTICAL", "Cortina Vertical", "Varredura vertical do topo"),
            TransitionEffect("CIRCLE_CROP", "Abertura Circular", "Abertura do centro em círculo"),
            TransitionEffect("ZOOM_CROSS", "Zoom Cruzado", "Aproximação com transição fluida"),
            TransitionEffect("BLUR_FADE", "Desfocar & Esmaecer", "Desfocagem suave na transição"),
            TransitionEffect("PUSH_LEFT", "Empurrar Esquerda", "Empurrão lateral contínuo"),
            TransitionEffect("PUSH_RIGHT", "Empurrar Direita", "Empurrão para direita"),
            TransitionEffect("FADE_BLACK", "Fade para Preto", "Passagem rápida pelo tom escuro"),
            TransitionEffect("FADE_WHITE", "Fade para Branco", "Flash luminoso suave"),
            TransitionEffect("SCALE_UP", "Escalar Centro", "Expansão a partir do centro"),
            TransitionEffect("SPLIT_HORIZONTAL", "Divisão Horizontal", "Separação em duas partes"),
            TransitionEffect("SPLIT_VERTICAL", "Divisão Vertical", "Separação vertical em duas partes"),
            TransitionEffect("SMOOTH_GLITCH", "Glitch Suave", "Distorção digital leve e moderna"),
            TransitionEffect("RIPPLE_FADE", "Onda Suave", "Efeito de ondulação orgânica")
        )

        fun getById(id: String): TransitionEffect {
            return ALL_TRANSITIONS.firstOrNull { it.id == id } ?: ALL_TRANSITIONS[0]
        }
    }
}
