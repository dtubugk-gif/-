package com.dtubugk.shakelight.cast

/** Text-field editing for keys typed on the computer: insert at the cursor, delete before it. */
object TextEdit {

    data class Result(val text: String, val cursor: Int)

    fun insert(current: String, selectionStart: Int, selectionEnd: Int, typed: String): Result {
        val (start, end) = normalize(current, selectionStart, selectionEnd)
        return Result(current.substring(0, start) + typed + current.substring(end), start + typed.length)
    }

    fun backspace(current: String, selectionStart: Int, selectionEnd: Int): Result {
        val (start, end) = normalize(current, selectionStart, selectionEnd)
        if (start != end) return Result(current.substring(0, start) + current.substring(end), start)
        if (start == 0) return Result(current, 0)
        // Delete a whole character, including emoji made of two UTF-16 units.
        val from = current.offsetByCodePoints(start, -1)
        return Result(current.substring(0, from) + current.substring(start), from)
    }

    /** A missing selection (-1) means "cursor at the end"; out-of-range values are clamped. */
    private fun normalize(current: String, selectionStart: Int, selectionEnd: Int): Pair<Int, Int> {
        val len = current.length
        val a = if (selectionStart < 0) len else selectionStart.coerceAtMost(len)
        val b = if (selectionEnd < 0) a else selectionEnd.coerceAtMost(len)
        return minOf(a, b) to maxOf(a, b)
    }
}
