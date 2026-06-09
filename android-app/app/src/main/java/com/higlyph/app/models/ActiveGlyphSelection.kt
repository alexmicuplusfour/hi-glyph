package com.higlyph.app.models

data class ActiveGlyphSelection(
    val imageId: String?,
    val mode: DisplayPriority,
    val updatedAt: Long,
)

enum class DisplayPriority {
    COMPOSITE,
    IDLE_ONLY,
    ALWAYS_ON,
}
