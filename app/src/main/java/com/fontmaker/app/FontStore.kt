package com.fontmaker.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/** Saves fonts as small JSON files in the app's private storage. */
class FontStore(context: Context) {
    private val dir = File(context.filesDir, "fonts").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("fontmaker", Context.MODE_PRIVATE)

    fun list(): List<HandFont> =
        (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { load(it) }
            .sortedBy { it.name.lowercase() }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun active(): HandFont {
        activeId()?.let { id -> load(fileFor(id))?.let { return it } }
        list().firstOrNull()?.let {
            setActive(it.id)
            return it
        }
        return create("My Handwriting")
    }

    /** Changes whenever the active font is switched or edited. */
    fun activeStamp(): String {
        val id = activeId() ?: return ""
        return id + ":" + fileFor(id).lastModified()
    }

    fun setActive(id: String) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun create(name: String): HandFont {
        val font = HandFont("f" + System.currentTimeMillis(), name)
        save(font)
        setActive(font.id)
        return font
    }

    fun save(font: HandFont) {
        val target = fileFor(font.id)
        val tmp = File(dir, font.id + ".tmp")
        tmp.writeText(toJson(font).toString())
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun delete(font: HandFont) {
        fileFor(font.id).delete()
        if (activeId() == font.id) prefs.edit().remove(KEY_ACTIVE).apply()
    }

    private fun fileFor(id: String) = File(dir, "$id.json")

    private fun load(f: File): HandFont? = try {
        if (f.exists()) fromJson(JSONObject(f.readText())) else null
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_ACTIVE = "active_font"

        private fun r(v: Float): Double = (v * 10000f).roundToInt() / 10000.0

        fun toJson(font: HandFont): JSONObject {
            val glyphs = JSONObject()
            for ((ch, g) in font.glyphs) {
                if (g.isEmpty) continue
                val strokes = JSONArray()
                for (s in g.strokes) {
                    if (s.points.isEmpty()) continue
                    val arr = JSONArray()
                    for (p in s.points) {
                        arr.put(r(p.x))
                        arr.put(r(p.y))
                    }
                    strokes.put(arr)
                }
                glyphs.put(ch, strokes)
            }
            return JSONObject()
                .put("version", 1)
                .put("id", font.id)
                .put("name", font.name)
                .put("strokeWidth", r(font.strokeWidth))
                .put("glyphs", glyphs)
        }

        fun fromJson(o: JSONObject): HandFont {
            val font = HandFont(
                id = o.getString("id"),
                name = o.optString("name", "My Font"),
                strokeWidth = o.optDouble("strokeWidth", Metrics.DEFAULT_STROKE.toDouble()).toFloat(),
            )
            val glyphs = o.optJSONObject("glyphs") ?: JSONObject()
            for (ch in glyphs.keys()) {
                val strokes = glyphs.getJSONArray(ch)
                val g = Glyph()
                for (i in 0 until strokes.length()) {
                    val arr = strokes.getJSONArray(i)
                    val s = Stroke()
                    var j = 0
                    while (j + 1 < arr.length()) {
                        s.points.add(Pt(arr.getDouble(j).toFloat(), arr.getDouble(j + 1).toFloat()))
                        j += 2
                    }
                    if (s.points.isNotEmpty()) g.strokes.add(s)
                }
                if (!g.isEmpty) font.glyphs[ch] = g
            }
            return font
        }
    }
}
