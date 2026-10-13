package com.dtubugk.shakelight.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/** Finds computers running ShakeLight Receiver: they broadcast a small JSON message every second. */
class ReceiverDiscovery(context: Context, private val onChange: (List<Receiver>) -> Unit) {

    /** [version] is empty when unknown (typed address). */
    data class Receiver(val name: String, val host: String, val port: Int, val version: String = "") {
        val isOutdated: Boolean get() = version.isNotEmpty() && !CastProtocol.isSupported(version)
    }

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var socket: DatagramSocket? = null

    fun start() {
        // Many phones drop broadcast packets unless an app holds this lock.
        multicastLock = wifi?.createMulticastLock("ShakeLight")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        val s = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000
                bind(InetSocketAddress(CastProtocol.DISCOVERY_PORT))
            }
        } catch (e: Exception) {
            return
        }
        socket = s

        thread(name = "receiver-discovery", isDaemon = true) {
            val seen = LinkedHashMap<String, Pair<Receiver, Long>>()
            var published = emptyList<Receiver>()
            val buffer = ByteArray(2048)
            while (socket === s) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    parse(packet)?.let { seen[it.host] = it to SystemClock.elapsedRealtime() }
                } catch (e: SocketTimeoutException) {
                    // nothing this second
                } catch (e: Exception) {
                    break // socket closed by stop()
                }
                val now = SystemClock.elapsedRealtime()
                seen.entries.removeAll { now - it.value.second > STALE_MS }
                val current = seen.values.map { it.first }.sortedBy { it.name.lowercase() }
                if (current != published) {
                    published = current
                    main.post { onChange(current) }
                }
            }
        }
    }

    fun stop() {
        socket?.close()
        socket = null
        multicastLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
    }

    private fun parse(packet: DatagramPacket): Receiver? = runCatching {
        val json = JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        if (json.optString("app") != "shakelight-receiver") return null
        val host = packet.address.hostAddress ?: return null
        Receiver(
            name = json.optString("name").ifBlank { host },
            host = host,
            port = json.optInt("port", CastProtocol.STREAM_PORT),
            version = json.optString("version"),
        )
    }.getOrNull()

    private companion object {
        const val STALE_MS = 4000L
    }
}
