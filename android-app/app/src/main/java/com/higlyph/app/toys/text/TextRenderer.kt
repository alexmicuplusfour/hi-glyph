package com.higlyph.app.toys.text

import com.higlyph.app.toys.PixelGrid

/**
 * Pure text rendering engine that converts strings to scrollable PixelGrid sequences.
 * No Android dependencies - can be unit tested with JVM tests.
 */
class TextRenderer(
    private val gridSize: Int = 13
) {

    /**
     * Render text as a wide bitmap that can be scrolled horizontally.
     * @param text The text to render
     * @param verticalOffset Vertical position (0 = top, negative = centered based on font height)
     * @return A wide PixelGrid containing the full rendered text
     */
    private fun renderTextBitmap(text: String, verticalOffset: Int = -1): WidePixelGrid? {
        if (text.isEmpty()) return null

        // Calculate total width needed
        val totalWidth = calculateTextWidth(text)
        if (totalWidth == 0) return null

        // Create wide grid to hold the full text
        val wideGrid = WidePixelGrid(totalWidth, gridSize)

        // Calculate vertical position (center if offset is -1)
        val yStart = if (verticalOffset < 0) {
            (gridSize - PixelFont.CHAR_HEIGHT) / 2
        } else {
            verticalOffset
        }

        // Render each character
        var xOffset = 0
        for (char in text) {
            val pattern = PixelFont.getCharPattern(char)
            if (pattern != null) {
                renderChar(wideGrid, char, xOffset, yStart)
                xOffset += PixelFont.CHAR_WIDTH + PixelFont.CHAR_SPACING
            }
        }

        return wideGrid
    }

    /**
     * Generate a sequence of PixelGrid frames for scrolling animation.
     * Each frame shows a 13-pixel-wide window of the text.
     * @param text The text to render
     * @param verticalOffset Vertical position (0 = top, -1 = centered)
     * @param loopCount Number of times to loop (0 = infinite, generates frames for one pass)
     * @return List of PixelGrid frames for animation
     */
    fun generateScrollingFrames(
        text: String,
        verticalOffset: Int = -1,
        loopCount: Int = 0
    ): List<PixelGrid> {
        val wideBitmap = renderTextBitmap(text, verticalOffset) ?: return emptyList()

        val frames = mutableListOf<PixelGrid>()
        val totalWidth = wideBitmap.width

        // Start at -(gridSize-1) so text enters from the right edge.
        // End at totalWidth-1 so text fully exits to the left.
        // Looping back to the start is then seamless with no gap.
        for (frameIndex in -(gridSize - 1) until totalWidth) {
            val frame = PixelGrid(gridSize)

            for (y in 0 until gridSize) {
                for (x in 0 until gridSize) {
                    val sourceX = frameIndex + x
                    if (sourceX >= 0 && sourceX < totalWidth) {
                        if (wideBitmap.get(sourceX, y)) {
                            frame.set(x, y, true)
                        }
                    }
                }
            }

            frames.add(frame)
        }

        return frames
    }

    /**
     * Generate frames for static centered text (no scrolling).
     * Text is centered horizontally and vertically on the 13x13 grid.
     * @param text The text to render
     * @return Single-frame list, or empty if text doesn't fit
     */
    fun generateStaticFrame(text: String): List<PixelGrid> {
        val wideBitmap = renderTextBitmap(text, -1) ?: return emptyList()
        val totalWidth = wideBitmap.width

        if (totalWidth > gridSize) {
            // Text too wide for static display
            return emptyList()
        }

        val frame = PixelGrid(gridSize)
        val xOffset = (gridSize - totalWidth) / 2

        for (y in 0 until gridSize) {
            for (x in 0 until totalWidth) {
                if (wideBitmap.get(x, y)) {
                    frame.set(xOffset + x, y, true)
                }
            }
        }

        return listOf(frame)
    }

    /**
     * Calculate the total width (in pixels) needed to render the text.
     */
    private fun calculateTextWidth(text: String): Int {
        var width = 0
        var charCount = 0

        for (char in text) {
            if (PixelFont.getCharPattern(char) != null) {
                width += PixelFont.CHAR_WIDTH
                charCount++
            }
        }

        // Add spacing between characters (but not after the last one)
        if (charCount > 0) {
            width += (charCount - 1) * PixelFont.CHAR_SPACING
        }

        return width
    }

    /**
     * Render a single character onto the wide grid.
     */
    private fun renderChar(grid: WidePixelGrid, char: Char, xOffset: Int, yOffset: Int) {
        val pattern = PixelFont.getCharPattern(char) ?: return

        for (y in pattern.indices) {
            val rowPattern = pattern[y]
            for (x in 0 until PixelFont.CHAR_WIDTH) {
                if (PixelFont.isPixelSet(rowPattern, x)) {
                    grid.set(xOffset + x, yOffset + y, true)
                }
            }
        }
    }

    /**
     * Internal class for wide bitmaps (wider than 13 pixels).
     * Used to render full text before slicing into frames.
     */
    private class WidePixelGrid(
        val width: Int,
        private val height: Int
    ) {
        private val pixels = BooleanArray(width * height)

        fun set(x: Int, y: Int, lit: Boolean = true) {
            if (x in 0 until width && y in 0 until height) {
                pixels[(y * width) + x] = lit
            }
        }

        fun get(x: Int, y: Int): Boolean {
            return if (x in 0 until width && y in 0 until height) {
                pixels[(y * width) + x]
            } else {
                false
            }
        }

        fun clear() {
            pixels.fill(false)
        }
    }
}
