package com.higlyph.app.api

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import com.higlyph.app.serialization.GlyphImageSerializer
import com.higlyph.app.toys.LiveGlyphPreview
import com.higlyph.app.toys.PixelGrid
import com.higlyph.app.toys.text.TextRenderer

/**
 * Handles all Hardware API logic.
 * Coordinates between API requests and toy services via SharedPreferences.
 */
class HardwareApiHandler(private val context: Context) {

    private val serializer = GlyphImageSerializer
    private val textRenderer = TextRenderer(13)
    private val startTime = SystemClock.elapsedRealtime()

    // ── Core Display ──────────────────────────────────────────────────────────────

    fun displayFrame(pixels: String, brightness: Int): ApiResponse {
        if (!isValidBinaryString(pixels)) {
            return ApiResponse(false, "Invalid pixels format. Must be 169-char binary string.")
        }

        val grid = serializer.binaryToPixelGrid(pixels)
            ?: return ApiResponse(false, "Failed to parse pixel grid")

        // Store in static image toy preferences
        val prefs = getStaticImagePrefs()
        prefs.edit()
            .putString("static_image_pixels", pixels)
            .putInt("static_image_brightness", brightness.coerceIn(0, 255))
            .apply()

        // Push to scrolling text toy as a static frame (triggers its prefListener).
        // Remove first so the listener fires even when pixels are unchanged.
        val scrollingPrefs = getScrollingTextPrefs()
        scrollingPrefs.edit().remove(com.higlyph.app.toys.ScrollingTextToyService.PREF_STATIC_PIXELS).commit()
        scrollingPrefs.edit().putString(com.higlyph.app.toys.ScrollingTextToyService.PREF_STATIC_PIXELS, pixels).commit()

        return ApiResponse(true, "Frame displayed")
    }

    fun setBrightness(brightness: Int): ApiResponse {
        val validBrightness = brightness.coerceIn(0, 255)

        // Update all toy preferences
        getStaticImagePrefs().edit().putInt("static_image_brightness", validBrightness).apply()

        return ApiResponse(true, "Brightness set to $validBrightness")
    }

    fun clearDisplay(): ApiResponse {
        val emptyGrid = "0".repeat(169)
        displayFrame(emptyGrid, 255)
        return ApiResponse(true, "Display cleared")
    }

    // ── Text Rendering ──────────────────────────────────────────────────────────────

    fun displayStaticText(text: String, verticalOffset: Int): ApiResponse {
        val frames = textRenderer.generateStaticFrame(text)
        if (frames.isEmpty()) {
            return ApiResponse(false, "Text too wide for static display or empty")
        }

        val pixels = serializer.pixelGridToBinary(frames[0])
        return displayFrame(pixels, 255)
    }

    fun startScrollingText(text: String, speed: Int, loopCount: Int, verticalOffset: Int): ApiResponse {
        Log.d(TAG, "API: startScrollingText called with message='$text', speed=$speed")
        val prefs = getScrollingTextPrefs()

        val success = prefs.edit()
            .putString("scrolling_text_message", text)
            .putInt("scrolling_text_speed", speed.coerceIn(1, 30))
            .putInt("scrolling_text_loop_count", loopCount.coerceAtLeast(0))
            .putBoolean("scrolling_text_paused", false)
            .commit()

        Log.d(TAG, "API: SharedPreferences commit result=$success")

        // Verify write
        val written = prefs.getString("scrolling_text_message", null)
        Log.d(TAG, "API: Verification read: message='$written'")

        return ApiResponse(
            true,
            "Scrolling text started",
            mapOf("text" to text, "speed" to speed, "loopCount" to loopCount, "committed" to success)
        )
    }

    fun setScrollSpeed(speed: Int): ApiResponse {
        val validSpeed = speed.coerceIn(1, 30)
        getScrollingTextPrefs().edit()
            .putInt("scrolling_text_speed", validSpeed)
            .commit()

        return ApiResponse(true, "Scroll speed set to $validSpeed FPS")
    }

    fun pauseScrolling(paused: Boolean): ApiResponse {
        getScrollingTextPrefs().edit()
            .putBoolean("scrolling_text_paused", paused)
            .commit()

        return ApiResponse(true, if (paused) "Scrolling paused" else "Scrolling resumed")
    }

    fun stopScrolling(): ApiResponse {
        val prefs = getScrollingTextPrefs()
        prefs.edit()
            .putString("scrolling_text_message", "")
            .putBoolean("scrolling_text_paused", true)
            .commit()

        return ApiResponse(true, "Scrolling stopped")
    }

    // ── Static Image ──────────────────────────────────────────────────────────────

    fun showStaticImage(pixels: String): ApiResponse {
        return displayFrame(pixels, 255)
    }

    // ── Animation ──────────────────────────────────────────────────────────────────

    fun startAnimation(frames: List<String>, fps: Int, loop: Boolean): ApiResponse {
        // Validate all frames
        for ((index, frame) in frames.withIndex()) {
            if (!isValidBinaryString(frame)) {
                return ApiResponse(false, "Invalid frame at index $index")
            }
        }

        if (frames.isEmpty()) {
            return ApiResponse(false, "No frames provided")
        }

        // Store as JSON array
        val framesJson = frames.joinToString(
            prefix = "[\"",
            separator = "\",\"",
            postfix = "\"]"
        )

        val prefs = getAnimationPrefs()
        prefs.edit()
            .putString("animation_frames", framesJson)
            .putInt("animation_fps", fps.coerceIn(1, 60))
            .putBoolean("animation_loop", loop)
            .putBoolean("animation_paused", false)
            .apply()

        return ApiResponse(
            true,
            "Animation started",
            mapOf("frameCount" to frames.size, "fps" to fps, "loop" to loop)
        )
    }

    fun setAnimationFps(fps: Int): ApiResponse {
        val validFps = fps.coerceIn(1, 60)
        getAnimationPrefs().edit()
            .putInt("animation_fps", validFps)
            .apply()

        return ApiResponse(true, "Animation FPS set to $validFps")
    }

    fun stopAnimation(): ApiResponse {
        val prefs = getAnimationPrefs()
        prefs.edit()
            .putString("animation_frames", "[]")
            .putBoolean("animation_paused", true)
            .apply()

        return ApiResponse(true, "Animation stopped")
    }

    // ── Built-in Toys ──────────────────────────────────────────────────────────────

    fun activateClockToy(): ApiResponse {
        // User must manually select "Composite Glyph" in AOD settings
        return ApiResponse(
            true,
            "Select 'hi! glyph: Multi function' in Settings → Glyph Interface → Always-on Glyph Toy"
        )
    }

    fun activateEqualizerToy(): ApiResponse {
        // User must manually select "Composite Glyph" in AOD settings and play audio
        return ApiResponse(
            true,
            "Select 'hi! glyph: Multi function' and play audio. Ensure microphone permission is granted."
        )
    }

    fun configureEqualizer(barCount: Int?, decay: Float?, sensitivity: Float?): ApiResponse {
        // Equalizer config would require modifying CompositeToyService
        // For now, return info message
        return ApiResponse(
            false,
            "Equalizer configuration not yet exposed via hardware API"
        )
    }

    // ── Status ──────────────────────────────────────────────────────────────────────

    fun getStatus(): StatusResponse {
        val latestFrame = LiveGlyphPreview.latestFrame()
        val uptimeSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000

        val mode = when (latestFrame?.mode) {
            com.higlyph.app.toys.LiveGlyphMode.CALL -> "call"
            com.higlyph.app.toys.LiveGlyphMode.CLOCK -> "clock"
            com.higlyph.app.toys.LiveGlyphMode.EQUALIZER -> "equalizer"
            com.higlyph.app.toys.LiveGlyphMode.CUSTOM_IDLE -> "custom_idle"
            com.higlyph.app.toys.LiveGlyphMode.STATIC_IMAGE -> "static"
            com.higlyph.app.toys.LiveGlyphMode.SCROLLING_TEXT -> "scrolling"
            com.higlyph.app.toys.LiveGlyphMode.ANIMATION -> "animation"
            null -> "idle"
        }

        val preview = latestFrame?.let { serializer.pixelGridToBinary(it.grid) } ?: "0".repeat(169)

        return StatusResponse(
            mode = mode,
            brightness = 255,  // Hardcoded for now
            activeContent = ActiveContentInfo(
                type = latestFrame?.source?.name?.lowercase() ?: "none",
                preview = preview
            ),
            uptimeSeconds = uptimeSeconds
        )
    }

    fun getCapabilities(): CapabilitiesResponse {
        return CapabilitiesResponse(
            device = "DEVICE_25111p",
            matrixSize = 13,
            brightnessRange = 0..255,
            maxFps = 60,
            features = listOf(
                "text_rendering",
                "scrolling_text",
                "static_image",
                "animation",
                "clock",
                "equalizer",
                "websocket_stream"
            )
        )
    }

    // ── Test Patterns ──────────────────────────────────────────────────────────────

    fun showTestPattern(pattern: String): TestPatternResponse {
        val grid = when (pattern) {
            "checkerboard" -> generateCheckerboard()
            "all-on" -> generateAllOn()
            "all-off" -> PixelGrid(13)
            "brightness-ramp" -> {
                // TODO: Implement brightness ramp (requires multiple frames)
                generateCheckerboard()
            }
            else -> return TestPatternResponse(pattern, false)
        }

        val pixels = serializer.pixelGridToBinary(grid)
        displayFrame(pixels, 255)

        return TestPatternResponse(pattern, true)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private fun getStaticImagePrefs(): SharedPreferences {
        return context.getSharedPreferences("static_image_hardware_api", Context.MODE_PRIVATE)
    }

    private fun getScrollingTextPrefs(): SharedPreferences {
        return context.getSharedPreferences("scrolling_text_prefs", Context.MODE_PRIVATE)
    }

    private fun getAnimationPrefs(): SharedPreferences {
        return context.getSharedPreferences("animation_prefs", Context.MODE_PRIVATE)
    }

    private fun isValidBinaryString(s: String): Boolean {
        return s.length == 169 && s.all { it == '0' || it == '1' }
    }

    private fun generateCheckerboard(): PixelGrid {
        val grid = PixelGrid(13)
        for (y in 0 until 13) {
            for (x in 0 until 13) {
                if ((x + y) % 2 == 0) {
                    grid.set(x, y, true)
                }
            }
        }
        return grid
    }

    private fun generateAllOn(): PixelGrid {
        val grid = PixelGrid(13)
        for (y in 0 until 13) {
            for (x in 0 until 13) {
                grid.set(x, y, true)
            }
        }
        return grid
    }

    companion object {
        private const val TAG = "HardwareApiHandler"
    }
}
