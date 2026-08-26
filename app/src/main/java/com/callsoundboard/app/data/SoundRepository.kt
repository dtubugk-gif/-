package com.callsoundboard.app.data

import android.content.Context
import com.callsoundboard.app.model.SoundClip
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the list of saved clips as a JSON string in SharedPreferences.
 * No external JSON library is needed — org.json ships with Android.
 */
class SoundRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getAll(): MutableList<SoundClip> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<SoundClip>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SoundClip(
                        id = o.getString("id"),
                        label = o.getString("label"),
                        uri = o.getString("uri")
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt store — start clean rather than crash.
        }
        return list
    }

    fun save(list: List<SoundClip>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("label", it.label)
            o.put("uri", it.uri)
            arr.put(o)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun add(clip: SoundClip) {
        val list = getAll()
        list.add(clip)
        save(list)
    }

    /** Set of URIs already saved, for de-duplicating bulk imports. */
    fun existingUris(): Set<String> = getAll().map { it.uri }.toHashSet()

    /**
     * Adds any clips whose URI isn't already saved. Returns how many were added.
     */
    fun addAllNew(clips: List<SoundClip>): Int {
        val list = getAll()
        val known = list.map { it.uri }.toHashSet()
        var added = 0
        clips.forEach { clip ->
            if (known.add(clip.uri)) {
                list.add(clip)
                added++
            }
        }
        if (added > 0) save(list)
        return added
    }

    fun remove(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    companion object {
        private const val PREFS = "sounds"
        private const val KEY = "clips"
    }
}
