package com.dtubugk.shakelight.cast

/**
 * Wire format shared with the computer app (receiver/protocol.go).
 * Phone -> computer: "SLC1", then packets of type(1) | length(4) | pts µs(8) | payload.
 * Computer -> phone: single control bytes.
 */
object CastProtocol {
    const val MAGIC = "SLC1"

    const val HELLO = 0
    const val CONFIG = 1
    const val KEY_FRAME = 2
    const val FRAME = 3
    const val AUDIO = 4

    const val CTRL_REQUEST_KEYFRAME = 1

    const val DISCOVERY_PORT = 47800
    const val STREAM_PORT = 47801
}
