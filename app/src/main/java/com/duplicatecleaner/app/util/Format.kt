package com.duplicatecleaner.app.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(Locale.getDefault(), pattern, value, UNITS[unit])
}

fun formatDate(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(millis))

/** "/storage/emulated/0/DCIM/Camera/x.jpg" -> "DCIM/Camera"; SD cards keep their volume id. */
fun displayFolder(path: String): String {
    if (path.isEmpty()) return ""
    val dir = path.substringBeforeLast('/', "")
    val trimmed = dir
        .removePrefix("/storage/emulated/0")
        .removePrefix("/storage/")
        .trimStart('/')
    return trimmed.ifEmpty { "/" }
}
