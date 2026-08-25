package com.routines.appclose.service

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * מצב חי של שירות הזיהוי, לתצוגה במסכי האפליקציה (אותו תהליך).
 * עוזר לאבחן "השגרות לא רצות": האם השירות באמת מחובר, ואילו יציאות זוהו.
 */
object ServiceStatus {

    data class Detection(
        val packageName: String,
        val label: String,
        val ruleRan: Boolean,
        val timestamp: Long,
    )

    val connected = MutableStateFlow(false)
    val recentDetections = MutableStateFlow<List<Detection>>(emptyList())

    fun addDetection(detection: Detection) {
        recentDetections.value = (listOf(detection) + recentDetections.value).take(20)
    }
}
