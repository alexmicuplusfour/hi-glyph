package com.higlyph.app.api

/**
 * Data models for the Hardware API.
 * These define the JSON structure for API requests and responses.
 */

// ── Request Models ──────────────────────────────────────────────────────────────

data class DisplayFrameRequest(
    val pixels: String,  // 169-char binary string
    val brightness: Int = 255  // 0-255
)

data class ScrollingTextRequest(
    val text: String,
    val speed: Int = 10,  // FPS
    val loopCount: Int = 0,  // 0 = infinite
    val verticalOffset: Int = -1  // -1 = centered
)

data class StaticTextRequest(
    val text: String,
    val verticalOffset: Int = -1  // -1 = centered
)

data class StaticImageRequest(
    val pixels: String  // 169-char binary string
)

data class AnimationRequest(
    val frames: List<String>,  // List of 169-char binary strings
    val fps: Int = 10,
    val loop: Boolean = true
)

data class BrightnessRequest(
    val brightness: Int  // 0-255
)

data class SpeedRequest(
    val speed: Int  // FPS
)

data class PauseRequest(
    val paused: Boolean
)

data class EqualizerConfigRequest(
    val barCount: Int? = null,
    val decay: Float? = null,
    val sensitivity: Float? = null
)

// ── Response Models ──────────────────────────────────────────────────────────────

data class ApiResponse(
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, Any>? = null
)

data class StatusResponse(
    val mode: String,  // "static", "scrolling", "clock", "equalizer", "call", "animation", "idle"
    val brightness: Int,
    val activeContent: ActiveContentInfo,
    val uptimeSeconds: Long
)

data class ActiveContentInfo(
    val type: String,  // "message", "image", "system", "animation"
    val preview: String,  // 169-char binary of current frame
    val details: Map<String, Any>? = null
)

data class CapabilitiesResponse(
    val device: String,  // "DEVICE_25111p"
    val matrixSize: Int,  // 13
    val brightnessRange: IntRange,  // 0..255
    val maxFps: Int,  // 60
    val features: List<String>
)

data class TestPatternResponse(
    val pattern: String,  // "checkerboard", "brightness-ramp", "all-on", "all-off"
    val applied: Boolean
)
