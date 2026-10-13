package com.dtubugk.shakelight.cast

import android.content.Context
import android.os.Build
import com.dtubugk.shakelight.core.Prefs
import org.json.JSONObject

/**
 * Commands the computer sends while projecting (always on the main thread).
 * Coordinates are 0..1 of the phone screen; "pts" are [x, y, ms-since-press] triples.
 *
 *   {"t":"gesture","pts":[[x,y,ms],…]}   {"t":"scroll","x":…,"y":…,"dx":…,"dy":…}
 *   {"t":"key","k":"back|home|recents|notifications|quick|split|screenshot|lock|power|vol+|vol-|mute"}
 *   {"t":"text","s":"…"}   {"t":"backspace"}   {"t":"enter"}
 *   {"t":"apps"}   {"t":"launch","p":"package","window":true}   {"t":"landscape","on":true}
 */
class RemoteCommands(private val context: Context, private val session: CastSession) {

    private val allowed get() = Prefs.get(context).getBoolean(Prefs.CAST_CONTROL, true)

    fun status(): JSONObject = JSONObject()
        .put("control", allowed && ControlService.instance != null)
        .put("controlAllowed", allowed)
        .put("canRotate", DesktopMode.canRotate(context))
        .put("landscape", DesktopMode.isLandscape(context))
        .put("windows", DesktopMode.supportsWindows(context))
        .put("sdk", Build.VERSION.SDK_INT)

    fun handle(cmd: JSONObject) {
        if (!allowed) return
        val control = ControlService.instance
        when (cmd.optString("t")) {
            "gesture" -> {
                val pts = cmd.optJSONArray("pts") ?: return
                val points = (0 until pts.length()).mapNotNull { i ->
                    val p = pts.optJSONArray(i) ?: return@mapNotNull null
                    Triple(p.optDouble(0).toFloat(), p.optDouble(1).toFloat(), p.optLong(2))
                }.filter { !it.first.isNaN() && !it.second.isNaN() }
                control?.gesture(points)
            }
            "scroll" -> control?.scroll(
                cmd.optDouble("x", 0.5).toFloat(), cmd.optDouble("y", 0.5).toFloat(),
                cmd.optDouble("dx", 0.0).toFloat(), cmd.optDouble("dy", 0.0).toFloat(),
            )
            "key" -> control?.key(cmd.optString("k"))
            "text" -> cmd.optString("s").takeIf { it.isNotEmpty() }?.let { control?.type(it) }
            "backspace" -> control?.backspace()
            "enter" -> control?.enter()
            "apps" -> session.sendAsync(CastProtocol.APPS) { DesktopMode.appsJson(context) }
            "launch" -> DesktopMode.launch(context, cmd.optString("p"), cmd.optBoolean("window"))
            "landscape" -> {
                DesktopMode.setLandscape(context, cmd.optBoolean("on"))
                session.sendStatusIfChanged(force = true)
            }
        }
    }
}
