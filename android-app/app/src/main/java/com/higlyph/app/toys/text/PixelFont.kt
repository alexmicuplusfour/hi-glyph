package com.higlyph.app.toys.text

/**
 * 5x7 pixel font definitions for text rendering.
 * Each character is represented as a 7-row array where each row is a 5-bit pattern.
 */
object PixelFont {

    /**
     * Font character width (always 5 pixels)
     */
    const val CHAR_WIDTH = 5

    /**
     * Font character height (always 7 pixels)
     */
    const val CHAR_HEIGHT = 7

    /**
     * Horizontal spacing between characters (pixels)
     */
    const val CHAR_SPACING = 1

    /**
     * Get the pixel pattern for a character.
     * Returns a 7-element array where each element represents one row (5 bits).
     * @param char The character to get the pattern for
     * @return Array of 7 integers representing the rows, or null if character not supported
     */
    fun getCharPattern(char: Char): IntArray? {
        // Lowercase letters with distinct pixel patterns
        if (char in 'a'..'z') {
            return when (char) {
                'a' -> intArrayOf(0b00000, 0b00000, 0b01110, 0b00001, 0b01111, 0b10001, 0b01111)
                'b' -> intArrayOf(0b10000, 0b10000, 0b10110, 0b11001, 0b10001, 0b10001, 0b11110)
                'c' -> intArrayOf(0b00000, 0b00000, 0b01110, 0b10000, 0b10000, 0b10001, 0b01110)
                'd' -> intArrayOf(0b00001, 0b00001, 0b01101, 0b10011, 0b10001, 0b10001, 0b01111)
                'e' -> intArrayOf(0b00000, 0b00000, 0b01110, 0b10001, 0b11111, 0b10000, 0b01110)
                'f' -> intArrayOf(0b00110, 0b01000, 0b11110, 0b01000, 0b01000, 0b01000, 0b01000)
                'g' -> intArrayOf(0b00000, 0b01111, 0b10001, 0b10001, 0b01111, 0b00001, 0b01110)
                'h' -> intArrayOf(0b10000, 0b10000, 0b10110, 0b11001, 0b10001, 0b10001, 0b10001)
                'i' -> intArrayOf(0b00000, 0b00100, 0b00000, 0b01100, 0b00100, 0b00100, 0b01110)
                'j' -> intArrayOf(0b00010, 0b00000, 0b00110, 0b00010, 0b00010, 0b10010, 0b01100)
                'k' -> intArrayOf(0b10000, 0b10000, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010)
                'l' -> intArrayOf(0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110)
                'm' -> intArrayOf(0b00000, 0b00000, 0b11010, 0b10101, 0b10101, 0b10001, 0b10001)
                'n' -> intArrayOf(0b00000, 0b00000, 0b10110, 0b11001, 0b10001, 0b10001, 0b10001)
                'o' -> intArrayOf(0b00000, 0b00000, 0b01110, 0b10001, 0b10001, 0b10001, 0b01110)
                'p' -> intArrayOf(0b00000, 0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000)
                'q' -> intArrayOf(0b00000, 0b01111, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001)
                'r' -> intArrayOf(0b00000, 0b00000, 0b10110, 0b11001, 0b10000, 0b10000, 0b10000)
                's' -> intArrayOf(0b00000, 0b00000, 0b01110, 0b10000, 0b01110, 0b00001, 0b01110)
                't' -> intArrayOf(0b01000, 0b01000, 0b11110, 0b01000, 0b01000, 0b01000, 0b00110)
                'u' -> intArrayOf(0b00000, 0b00000, 0b10001, 0b10001, 0b10001, 0b10001, 0b01111)
                'v' -> intArrayOf(0b00000, 0b00000, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100)
                'w' -> intArrayOf(0b00000, 0b00000, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001)
                'x' -> intArrayOf(0b00000, 0b00000, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001)
                'y' -> intArrayOf(0b00000, 0b00000, 0b10001, 0b10001, 0b01111, 0b00001, 0b01110)
                'z' -> intArrayOf(0b00000, 0b00000, 0b11111, 0b00010, 0b00100, 0b01000, 0b11111)
                else -> null
            }
        }

        // Uppercase letters, digits, and symbols
        return when (char.uppercaseChar()) {
            'A' -> intArrayOf(
                0b01110,
                0b10001,
                0b10001,
                0b11111,
                0b10001,
                0b10001,
                0b10001
            )
            'B' -> intArrayOf(
                0b11110,
                0b10001,
                0b10001,
                0b11110,
                0b10001,
                0b10001,
                0b11110
            )
            'C' -> intArrayOf(
                0b01110,
                0b10001,
                0b10000,
                0b10000,
                0b10000,
                0b10001,
                0b01110
            )
            'D' -> intArrayOf(
                0b11110,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b11110
            )
            'E' -> intArrayOf(
                0b11111,
                0b10000,
                0b10000,
                0b11110,
                0b10000,
                0b10000,
                0b11111
            )
            'F' -> intArrayOf(
                0b11111,
                0b10000,
                0b10000,
                0b11110,
                0b10000,
                0b10000,
                0b10000
            )
            'G' -> intArrayOf(
                0b01110,
                0b10001,
                0b10000,
                0b10111,
                0b10001,
                0b10001,
                0b01110
            )
            'H' -> intArrayOf(
                0b10001,
                0b10001,
                0b10001,
                0b11111,
                0b10001,
                0b10001,
                0b10001
            )
            'I' -> intArrayOf(
                0b11111,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b11111
            )
            'J' -> intArrayOf(
                0b00111,
                0b00010,
                0b00010,
                0b00010,
                0b00010,
                0b10010,
                0b01100
            )
            'K' -> intArrayOf(
                0b10001,
                0b10010,
                0b10100,
                0b11000,
                0b10100,
                0b10010,
                0b10001
            )
            'L' -> intArrayOf(
                0b10000,
                0b10000,
                0b10000,
                0b10000,
                0b10000,
                0b10000,
                0b11111
            )
            'M' -> intArrayOf(
                0b10001,
                0b11011,
                0b10101,
                0b10101,
                0b10001,
                0b10001,
                0b10001
            )
            'N' -> intArrayOf(
                0b10001,
                0b11001,
                0b10101,
                0b10101,
                0b10011,
                0b10001,
                0b10001
            )
            'O' -> intArrayOf(
                0b01110,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b01110
            )
            'P' -> intArrayOf(
                0b11110,
                0b10001,
                0b10001,
                0b11110,
                0b10000,
                0b10000,
                0b10000
            )
            'Q' -> intArrayOf(
                0b01110,
                0b10001,
                0b10001,
                0b10001,
                0b10101,
                0b10010,
                0b01101
            )
            'R' -> intArrayOf(
                0b11110,
                0b10001,
                0b10001,
                0b11110,
                0b10100,
                0b10010,
                0b10001
            )
            'S' -> intArrayOf(
                0b01110,
                0b10001,
                0b10000,
                0b01110,
                0b00001,
                0b10001,
                0b01110
            )
            'T' -> intArrayOf(
                0b11111,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b00100
            )
            'U' -> intArrayOf(
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b01110
            )
            'V' -> intArrayOf(
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b10001,
                0b01010,
                0b00100
            )
            'W' -> intArrayOf(
                0b10001,
                0b10001,
                0b10001,
                0b10101,
                0b10101,
                0b11011,
                0b10001
            )
            'X' -> intArrayOf(
                0b10001,
                0b10001,
                0b01010,
                0b00100,
                0b01010,
                0b10001,
                0b10001
            )
            'Y' -> intArrayOf(
                0b10001,
                0b10001,
                0b01010,
                0b00100,
                0b00100,
                0b00100,
                0b00100
            )
            'Z' -> intArrayOf(
                0b11111,
                0b00001,
                0b00010,
                0b00100,
                0b01000,
                0b10000,
                0b11111
            )
            '0' -> intArrayOf(
                0b01110,
                0b10001,
                0b10011,
                0b10101,
                0b11001,
                0b10001,
                0b01110
            )
            '1' -> intArrayOf(
                0b00100,
                0b01100,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b01110
            )
            '2' -> intArrayOf(
                0b01110,
                0b10001,
                0b00001,
                0b00010,
                0b00100,
                0b01000,
                0b11111
            )
            '3' -> intArrayOf(
                0b11111,
                0b00010,
                0b00100,
                0b00010,
                0b00001,
                0b10001,
                0b01110
            )
            '4' -> intArrayOf(
                0b00010,
                0b00110,
                0b01010,
                0b10010,
                0b11111,
                0b00010,
                0b00010
            )
            '5' -> intArrayOf(
                0b11111,
                0b10000,
                0b11110,
                0b00001,
                0b00001,
                0b10001,
                0b01110
            )
            '6' -> intArrayOf(
                0b00110,
                0b01000,
                0b10000,
                0b11110,
                0b10001,
                0b10001,
                0b01110
            )
            '7' -> intArrayOf(
                0b11111,
                0b00001,
                0b00010,
                0b00100,
                0b01000,
                0b01000,
                0b01000
            )
            '8' -> intArrayOf(
                0b01110,
                0b10001,
                0b10001,
                0b01110,
                0b10001,
                0b10001,
                0b01110
            )
            '9' -> intArrayOf(
                0b01110,
                0b10001,
                0b10001,
                0b01111,
                0b00001,
                0b00010,
                0b01100
            )
            '!' -> intArrayOf(
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b00100,
                0b00000,
                0b00100
            )
            '?' -> intArrayOf(
                0b01110,
                0b10001,
                0b00001,
                0b00010,
                0b00100,
                0b00000,
                0b00100
            )
            '.' -> intArrayOf(
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00100
            )
            ',' -> intArrayOf(
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00100,
                0b01000
            )
            ':' -> intArrayOf(
                0b00000,
                0b00000,
                0b00100,
                0b00000,
                0b00000,
                0b00100,
                0b00000
            )
            ';' -> intArrayOf(
                0b00000,
                0b00000,
                0b00100,
                0b00000,
                0b00000,
                0b00100,
                0b01000
            )
            '-' -> intArrayOf(
                0b00000,
                0b00000,
                0b00000,
                0b11111,
                0b00000,
                0b00000,
                0b00000
            )
            '+' -> intArrayOf(
                0b00000,
                0b00100,
                0b00100,
                0b11111,
                0b00100,
                0b00100,
                0b00000
            )
            '=' -> intArrayOf(
                0b00000,
                0b00000,
                0b11111,
                0b00000,
                0b11111,
                0b00000,
                0b00000
            )
            '*' -> intArrayOf(
                0b00000,
                0b10101,
                0b01110,
                0b11111,
                0b01110,
                0b10101,
                0b00000
            )
            '/' -> intArrayOf(
                0b00001,
                0b00010,
                0b00010,
                0b00100,
                0b01000,
                0b01000,
                0b10000
            )
            '\\' -> intArrayOf(
                0b10000,
                0b01000,
                0b01000,
                0b00100,
                0b00010,
                0b00010,
                0b00001
            )
            '(' -> intArrayOf(
                0b00010,
                0b00100,
                0b01000,
                0b01000,
                0b01000,
                0b00100,
                0b00010
            )
            ')' -> intArrayOf(
                0b01000,
                0b00100,
                0b00010,
                0b00010,
                0b00010,
                0b00100,
                0b01000
            )
            '[' -> intArrayOf(
                0b01110,
                0b01000,
                0b01000,
                0b01000,
                0b01000,
                0b01000,
                0b01110
            )
            ']' -> intArrayOf(
                0b01110,
                0b00010,
                0b00010,
                0b00010,
                0b00010,
                0b00010,
                0b01110
            )
            '<' -> intArrayOf(
                0b00010,
                0b00100,
                0b01000,
                0b10000,
                0b01000,
                0b00100,
                0b00010
            )
            '>' -> intArrayOf(
                0b01000,
                0b00100,
                0b00010,
                0b00001,
                0b00010,
                0b00100,
                0b01000
            )
            ' ' -> intArrayOf(
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000,
                0b00000
            )
            '\u2764' -> intArrayOf(  // ❤ heart
                0b00000,
                0b01010,
                0b11111,
                0b11111,
                0b01110,
                0b00100,
                0b00000
            )
            else -> null  // Character not supported
        }
    }

    /**
     * Check if a pixel is set in a character pattern row.
     * @param rowPattern The row pattern (5-bit integer)
     * @param x The x position (0-4, left to right)
     * @return true if the pixel is set
     */
    fun isPixelSet(rowPattern: Int, x: Int): Boolean {
        return (rowPattern and (1 shl (4 - x))) != 0
    }
}
