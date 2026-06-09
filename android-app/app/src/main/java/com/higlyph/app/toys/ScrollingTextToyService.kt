package com.higlyph.app.toys

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.GlyphMatrixManager
import com.higlyph.app.toys.text.TextRenderer

/**
 * Glyph Toy that displays horizontally scrolling text messages.
 *
 * Controlled via SharedPreferences:
 * - "scrolling_text_message" - the text to display
 * - "scrolling_text_speed" - FPS (frames per second), default 10
 * - "scrolling_text_loop_count" - how many times to loop (0 = infinite)
 * - "scrolling_text_paused" - true to pause animation
 *
 * Hardware API will write to these preferences to control the display.
 */
class ScrollingTextToyService : GlyphToyBase("ScrollingTextToy") {

    private var displayAdapter: GlyphDisplayAdapter? = null
    private val textRenderer = TextRenderer(13)

    private var currentFrames: List<PixelGrid> = emptyList()
    private var currentFrameIndex = 0
    private var loopsCompleted = 0

    private var fps = DEFAULT_FPS
    private var loopCount = 0  // 0 = infinite
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
        Log.d(TAG, "TOY: Preference changed: key=$key")
        when (key) {
            PREF_STATIC_PIXELS -> {
                val pixels = prefs.getString(PREF_STATIC_PIXELS, "") ?: ""
                if (pixels.length == 169) {
                    Log.d(TAG, "TOY: Static pixels received, looping as single frame")
                    val grid = com.higlyph.app.serialization.GlyphImageSerializer.binaryToPixelGrid(pixels)
                    if (grid != null) {
                        currentFrames = listOf(grid)
                        currentFrameIndex = 0
                        loopsCompleted = 0
                        loopCount = 0
                        isPaused = false
                        restartAnimation()
                    }
                }
            }
            PREF_MESSAGE -> {
                val message = prefs.getString(PREF_MESSAGE, "") ?: ""
                Log.d(TAG, "TOY: Message changed to '$message'")
                updateText(message)
            }
            PREF_SPEED -> {
                fps = prefs.getInt(PREF_SPEED, DEFAULT_FPS).coerceIn(1, 30)
                Log.d(TAG, "TOY: Speed changed to $fps FPS")
                restartAnimation()
            }
            PREF_LOOP_COUNT -> {
                loopCount = prefs.getInt(PREF_LOOP_COUNT, 0).coerceAtLeast(0)
                Log.d(TAG, "TOY: Loop count changed to $loopCount")
            }
            PREF_PAUSED -> {
                isPaused = prefs.getBoolean(PREF_PAUSED, false)
                Log.d(TAG, "TOY: Paused state changed to $isPaused")
                if (!isPaused) {
                    restartAnimation()
                }
            }
            else -> {
                Log.d(TAG, "TOY: Unknown preference key changed: $key")
            }
        }
    }

    override fun onServiceConnected(context: Context, gmm: GlyphMatrixManager) {
        displayAdapter = GlyphDisplayAdapter(context, gmm)

        // Load preferences
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val message = prefs.getString(PREF_MESSAGE, DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
        fps = prefs.getInt(PREF_SPEED, DEFAULT_FPS).coerceIn(1, 30)
        loopCount = prefs.getInt(PREF_LOOP_COUNT, 0).coerceAtLeast(0)
        isPaused = prefs.getBoolean(PREF_PAUSED, false)

        // Register listener for updates
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Start displaying
        updateText(message)
        startAnimation()

        Log.d(TAG, "ScrollingText service connected, message='$message', fps=$fps")
    }

    override fun onServiceDisconnected(context: Context) {
        stopAnimation()
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        displayAdapter = null
        Log.d(TAG, "ScrollingText service disconnected")
    }

    override fun onAod() {
        // Refresh every minute (required for AOD toys)
        displayCurrentFrame()
    }

    private fun updateText(message: String) {
        Log.d(TAG, "Updating text to: '$message'")
        currentFrames = if (message.isNotEmpty()) {
            textRenderer.generateScrollingFrames(message, verticalOffset = -1, loopCount = loopCount)
        } else {
            emptyList()
        }
        currentFrameIndex = 0
        loopsCompleted = 0

        if (currentFrames.isEmpty()) {
            // Display empty grid if no text
            displayAdapter?.display(PixelGrid(13))
        }
    }

    private fun displayCurrentFrame() {
        if (currentFrames.isEmpty()) return

        val frame = currentFrames[currentFrameIndex]
        displayAdapter?.display(frame)

        // Publish to live preview
        LiveGlyphPreview.publish(
            grid = frame,
            source = LiveGlyphSource.SCROLLING_TEXT_TOY,
            mode = LiveGlyphMode.SCROLLING_TEXT
        )
    }

    private fun advanceFrame() {
        if (currentFrames.isEmpty()) return

        currentFrameIndex++

        if (currentFrameIndex >= currentFrames.size) {
            currentFrameIndex = 0
            loopsCompleted++

            // Check if we should stop looping
            if (loopCount > 0 && loopsCompleted >= loopCount) {
                Log.d(TAG, "Completed $loopsCompleted loops, stopping")
                stopAnimation()
                // Show empty grid or hold last frame
                displayAdapter?.display(PixelGrid(13))
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

    private fun displayStaticGrid(grid: PixelGrid) {
        displayAdapter?.display(grid)
        LiveGlyphPreview.publish(
            grid = grid,
            source = LiveGlyphSource.SCROLLING_TEXT_TOY,
            mode = LiveGlyphMode.SCROLLING_TEXT
        )
    }

    companion object {
        private const val TAG = "ScrollingTextToy"
        private const val PREF_FILE = "scrolling_text_prefs"
        private const val PREF_MESSAGE = "scrolling_text_message"
        private const val PREF_SPEED = "scrolling_text_speed"
        private const val PREF_LOOP_COUNT = "scrolling_text_loop_count"
        private const val PREF_PAUSED = "scrolling_text_paused"
        const val PREF_STATIC_PIXELS = "scrolling_text_static_pixels"

        private const val DEFAULT_MESSAGE = "hi! glyph"
        private const val DEFAULT_FPS = 10
    }
}
