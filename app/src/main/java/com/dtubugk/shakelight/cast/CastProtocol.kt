package com.dtubugk.shakelight.cast

/**
 * Wire format shared with the computer app (receiver/protocol.go).
 * Phone -> computer: "SLC2", then packets of type(1) | length(4) | pts µs(8) | payload.
 * Computer -> phone: packets of type(1) | length(4) | payload.
 */
object CastProtocol {
    const val MAGIC = "SLC2"

    const val HELLO = 0
    const val CONFIG = 1
    const val KEY_FRAME = 2
    const val FRAME = 3
    const val AUDIO = 4
    const val STATUS = 5 // JSON: what the computer may do (control, landscape…)
    const val APPS = 6 // JSON: launchable apps with icons

    const val CTRL_REQUEST_KEYFRAME = 1
    const val CTRL_COMMAND = 2 // JSON command, see RemoteCommands
    const val CTRL_WELCOME = 3 // JSON {"version": "..."} sent by the receiver right after connecting

    /** Oldest computer receiver that speaks this protocol (1.0.0 only understands "SLC1"). */
    const val MIN_RECEIVER_VERSION = "1.1.0"

    fun isSupported(receiverVersion: String): Boolean = compareVersions(receiverVersion, MIN_RECEIVER_VERSION) >= 0

    fun compareVersions(a: String, b: String): Int {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    const val DISCOVERY_PORT = 47800
    const val STREAM_PORT = 47801
}
