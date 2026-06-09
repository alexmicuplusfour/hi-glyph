package com.higlyph.app.toys

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.higlyph.app.serialization.GlyphImageSerializer

/**
 * Glyph Toy that plays custom frame-by-frame animations.
 *
 * Controlled via SharedPreferences:
 * - "animation_frames" - JSON array of 169-char binary strings
 * - "animation_fps" - frames per second, default 10
 * - "animation_loop" - true to loop infinitely, false to play once
 * - "animation_paused" - true to pause playback
 *
 * Hardware API will write to these preferences to control playback.
 */
class AnimationToyService : GlyphToyBase("AnimationToy") {

    private var displayAdapter: GlyphDisplayAdapter? = null
    private val serializer = com.higlyph.app.serialization.GlyphImageSerializer

    private var currentFrames: List<PixelGrid> = emptyList()
    private var currentFrameIndex = 0

    private var fps = DEFAULT_FPS
    private var shouldLoop = true
    private var isPaused = false

    private val handler = Handler(Looper.getMainLooper())
    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!isPaused && currentFrames.isNotEmpty()) {
                displayCurrentFrame()
                advanceFrame()

                // Schedule next frame
                val delayMs = (1000.0 / fps).toLong()
                handler.postDelayed(this, delayMs)
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            PREF_FRAMES -> {
                val framesJson = prefs.getString(PREF_FRAMES, "[]") ?: "[]"
                updateFrames(framesJson)
            }
            PREF_FPS -> {
                fps = prefs.getInt(PREF_FPS, DEFAULT_FPS).coerceIn(1, 60)
                restartAnimation()
            }
            PREF_LOOP -> {
                shouldLoop = prefs.getBoolean(PREF_LOOP, true)
            }
            PREF_PAUSED -> {
                isPaused = prefs.getBoolean(PREF_PAUSED, false)
                if (!isPaused) {
                    restartAnimation()
                }
            }
        }
    }

    override fun onServiceConnected(context: Context, gmm: GlyphMatrixManager) {
        displayAdapter = GlyphDisplayAdapter(context, gmm)

        // Load preferences
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val framesJson = prefs.getString(PREF_FRAMES, "[]") ?: "[]"
        fps = prefs.getInt(PREF_FPS, DEFAULT_FPS).coerceIn(1, 60)
        shouldLoop = prefs.getBoolean(PREF_LOOP, true)
        isPaused = prefs.getBoolean(PREF_PAUSED, false)

        // Register listener for updates
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Start playing
        updateFrames(framesJson)
        startAnimation()

        Log.d(TAG, "Animation service connected, ${currentFrames.size} frames, fps=$fps")
    }

    override fun onServiceDisconnected(context: Context) {
        stopAnimation()
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        displayAdapter = null
        Log.d(TAG, "Animation service disconnected")
    }

    override fun onAod() {
        // Refresh every minute (required for AOD toys)
        displayCurrentFrame()
    }

    private fun updateFrames(framesJson: String) {
        Log.d(TAG, "Updating animation frames from JSON")

        val frames = mutableListOf<PixelGrid>()

        // Parse JSON array of binary strings
        // Simple manual parsing to avoid adding Gson dependency for this toy
        val trimmed = framesJson.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val content = trimmed.substring(1, trimmed.length - 1)
            if (content.isNotEmpty()) {
                // Split by commas not inside quotes
                val items = content.split("\",\"")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.length == 169 }

                for (binaryString in items) {
                    val grid = serializer.binaryToPixelGrid(binaryString)
                    if (grid != null) {
                        frames.add(grid)
                    }
                }
            }
        }

        currentFrames = frames
        currentFrameIndex = 0

        if (currentFrames.isEmpty()) {
            // Display empty grid if no frames
            displayAdapter?.display(PixelGrid(13))
            Log.d(TAG, "No valid frames to display")
        } else {
            Log.d(TAG, "Loaded ${currentFrames.size} animation frames")
        }
    }

    private fun displayCurrentFrame() {
        if (currentFrames.isEmpty()) return

        val frame = currentFrames[currentFrameIndex]
        displayAdapter?.display(frame)

        // Publish to live preview
        LiveGlyphPreview.publish(
            grid = frame,
            source = LiveGlyphSource.ANIMATION_TOY,
            mode = LiveGlyphMode.ANIMATION
        )
    }

    private fun advanceFrame() {
        if (currentFrames.isEmpty()) return

        currentFrameIndex++

        if (currentFrameIndex >= currentFrames.size) {
            if (shouldLoop) {
                currentFrameIndex = 0
            } else {
                // Stop playback after one pass
                Log.d(TAG, "Animation completed (no loop)")
                stopAnimation()
            }
        }
    }

    private fun startAnimation() {
        if (currentFrames.isEmpty() || isPaused) return

        stopAnimation()  // Clear any existing callbacks
        handler.post(animationRunnable)
        Log.d(TAG, "Animation started at $fps FPS")
    }

    private fun stopAnimation() {
        handler.removeCallbacks(animationRunnable)
    }

    private fun restartAnimation() {
        stopAnimation()
        if (!isPaused) {
            startAnimation()
        }
    }

    companion object {
        private const val TAG = "AnimationToy"
        private const val PREF_FILE = "animation_prefs"
        private const val PREF_FRAMES = "animation_frames"
        private const val PREF_FPS = "animation_fps"
        private const val PREF_LOOP = "animation_loop"
        private const val PREF_PAUSED = "animation_paused"

        private const val DEFAULT_FPS = 10
    }
}
