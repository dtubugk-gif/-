package com.callsoundboard.app.model

/**
 * A single saved audio clip that the user can play into a call.
 *
 * @param id    stable identifier used for list diffing and deletion
 * @param label human-readable name shown in the UI (usually the file name)
 * @param uri   persisted SAF content:// URI string for the audio file
 */
data class SoundClip(
    val id: String,
    val label: String,
    val uri: String
)
