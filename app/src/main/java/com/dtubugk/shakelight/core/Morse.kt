package com.dtubugk.shakelight.core

/** Turns text into flash on/off steps using standard international Morse timing. */
object Morse {

    private val CODES = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '!' to "-.-.--", '/' to "-..-.",
        '@' to ".--.-.", '-' to "-....-",
    )

    /** Each step is (flash on?, duration in ms). Empty if nothing in [text] can be encoded. */
    fun timings(text: String, unitMs: Long): List<Pair<Boolean, Long>> {
        val steps = mutableListOf<Pair<Boolean, Long>>()
        val words = text.uppercase().split(Regex("\\s+"))
            .map { word -> word.mapNotNull { CODES[it] } }
            .filter { it.isNotEmpty() }

        words.forEachIndexed { wordIndex, letters ->
            if (wordIndex > 0) steps += false to 7 * unitMs
            letters.forEachIndexed { letterIndex, code ->
                if (letterIndex > 0) steps += false to 3 * unitMs
                code.forEachIndexed { symbolIndex, symbol ->
                    if (symbolIndex > 0) steps += false to unitMs
                    steps += true to if (symbol == '.') unitMs else 3 * unitMs
                }
            }
        }
        return steps
    }
}
