package il.rikavon.feature.mascot.sound

import android.util.Log
import il.rikavon.feature.mascot.model.VoiceProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * The free voice: the same Azure neural voices (Avri and Hila for Hebrew) through the channel the Edge browser's
 * "Read aloud" uses, which needs no account and no key. It is not a documented service: Microsoft may change or
 * close it without notice, and the moment it stops answering the device engine takes over, so nothing breaks.
 * The message shapes here follow what Edge itself sends. Kept pure where it can be, so it is testable.
 */
object EdgeSpeech {
    /** What Settings stores as the "key" when the user picks the free voice. */
    const val KEY = "edge"

    /** The voices the channel offers that fit the pet: the two Hebrew ones, then English ones that speak Hebrew too. */
    val VOICES: List<String> =
        listOf(
            "he-IL-AvriNeural",
            "he-IL-HilaNeural",
            "en-US-AndrewMultilingualNeural",
            "en-US-AvaMultilingualNeural",
            "en-US-BrianMultilingualNeural",
            "en-US-EmmaMultilingualNeural",
            "en-US-AriaNeural",
            "en-US-GuyNeural",
        )

    const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
    val USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$CHROMIUM_MAJOR.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR.0.0.0"

    /** The voice for a pet in a language: the user's [choice], else the one cast for the personality. */
    fun voice(profile: VoiceProfile, languageTag: String, choice: String? = null): String {
        choice?.takeIf { it in VOICES }?.let { return it }
        val cast = CAST[profile.personality] ?: DEFAULT
        return if (NeuralSpeech.languageCode(languageTag) == "he") cast.first else cast.second
    }

    /** The socket URL for one request; [skewMillis] corrects the phone's clock by what the service last said. */
    fun url(nowMillis: Long, skewMillis: Long, connectionId: String): String =
        "wss://$HOST$PATH?TrustedClientToken=$TOKEN" +
            "&Sec-MS-GEC=${securityToken((nowMillis + skewMillis) / MILLIS)}" +
            "&Sec-MS-GEC-Version=1-$CHROMIUM&ConnectionId=$connectionId"

    /**
     * The proof the channel asks for: the current five-minute slot in Windows ticks, with the client token,
     * hashed. It is derived from the clock alone, so a phone whose clock is off gets a refusal (and a retry).
     */
    fun securityToken(nowSeconds: Long): String {
        var ticks = nowSeconds + WINDOWS_EPOCH_SECONDS
        ticks -= ticks % SLOT_SECONDS
        ticks *= TICKS_PER_SECOND
        val digest = MessageDigest.getInstance("SHA-256").digest("$ticks$TOKEN".toByteArray(Charsets.US_ASCII))
        return digest.joinToString("") { "%02X".format(it) }
    }

    /** The timestamp the messages carry, in the browser's own wording. */
    fun timestamp(nowMillis: Long): String {
        val format = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(nowMillis)) + " GMT+0000 (Coordinated Universal Time)"
    }

    fun configMessage(timestamp: String): String =
        "X-Timestamp:$timestamp\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\"," +
            "\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"$OUTPUT_FORMAT\"}}}}\r\n"

    fun ssmlMessage(requestId: String, timestamp: String, ssml: String): String =
        "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp}Z\r\nPath:ssml\r\n\r\n$ssml"

    /** True for the text message that says the answer is complete. */
    fun isTurnEnd(message: String): Boolean = message.contains("Path:turn.end")

    /** The audio in one binary message (a two-byte header length, the header, then the bytes), or null for others. */
    fun audioIn(frame: ByteArray): ByteArray? {
        if (frame.size < HEADER_LENGTH_BYTES) return null
        val length = ((frame[0].toInt() and BYTE_MASK) shl BITS_PER_BYTE) or (frame[1].toInt() and BYTE_MASK)
        val end = HEADER_LENGTH_BYTES + length
        if (end > frame.size) return null
        val header = String(frame, HEADER_LENGTH_BYTES, length, Charsets.UTF_8)
        val isAudio = header.split("\r\n").any { it.trim() == "Path:audio" }
        return if (isAudio) frame.copyOfRange(end, frame.size) else null
    }

    /** How far the phone's clock is from the service's, from the `Date` header of a refusal; null when unreadable. */
    fun skewMillis(serverDate: String?, nowMillis: Long): Long? =
        serverDate?.let {
            runCatching {
                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis
            }.getOrNull()
        }

    private const val HOST = "speech.platform.bing.com"
    private const val PATH = "/consumer/speech/synthesize/readaloud/edge/v1"
    private const val TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val CHROMIUM = "130.0.2849.68"
    private const val CHROMIUM_MAJOR = "130"
    private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"
    private const val WINDOWS_EPOCH_SECONDS = 11_644_473_600L
    private const val SLOT_SECONDS = 300L
    private const val TICKS_PER_SECOND = 10_000_000L
    private const val MILLIS = 1_000L
    private const val HEADER_LENGTH_BYTES = 2
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

    /** Hebrew voice to English voice, per personality. */
    private val CAST: Map<String, Pair<String, String>> =
        mapOf(
            "cynical" to ("he-IL-AvriNeural" to "en-US-AndrewMultilingualNeural"),
            "dramatic" to ("he-IL-HilaNeural" to "en-US-EmmaMultilingualNeural"),
            "confused" to ("he-IL-HilaNeural" to "en-US-AvaMultilingualNeural"),
            "judgmental" to ("he-IL-HilaNeural" to "en-US-AriaNeural"),
            "bureaucratic" to ("he-IL-AvriNeural" to "en-US-GuyNeural"),
            "indifferent" to ("he-IL-AvriNeural" to "en-US-BrianMultilingualNeural"),
        )
    private val DEFAULT = "he-IL-AvriNeural" to "en-US-AndrewMultilingualNeural"
}

/** [SpeechSynthesizer] on the free channel: one socket per line, the clip collected until the service is done. */
class EdgeSynthesizer : SpeechSynthesizer {
    override val voices: List<String> get() = EdgeSpeech.VOICES

    @Volatile
    private var skewMillis = 0L

    override suspend fun attempt(
        text: String,
        profile: VoiceProfile,
        languageTag: String,
        choice: String?,
    ): VoiceAttempt {
        val voice = EdgeSpeech.voice(profile, languageTag, choice)
        val ssml = AzureSpeech.ssml(text, voice, languageTag, profile)
        var reply = request(ssml)
        if (reply.attempt is VoiceAttempt.Failed && reply.attempt.status == FORBIDDEN) {
            // The proof is built from the clock; a phone whose clock is off is told the real time and tries once more.
            EdgeSpeech.skewMillis(reply.serverDate, System.currentTimeMillis())?.let { skewMillis = it }
            reply = request(ssml)
        }
        (reply.attempt as? VoiceAttempt.Failed)?.let { Log.w(TAG, "Edge speech ${it.summary()}") }
        return reply.attempt
    }

    private class Reply(val attempt: VoiceAttempt, val serverDate: String? = null)

    private suspend fun request(ssml: String): Reply =
        suspendCancellableCoroutine { continuation ->
            val id = UUID.randomUUID().toString().replace("-", "")
            val request =
                Request
                    .Builder()
                    .url(EdgeSpeech.url(System.currentTimeMillis(), skewMillis, id))
                    .header("Pragma", "no-cache")
                    .header("Cache-Control", "no-cache")
                    .header("Origin", EdgeSpeech.ORIGIN)
                    .header("User-Agent", EdgeSpeech.USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
            val audio = ByteArrayOutputStream()
            val done = AtomicBoolean(false)

            fun finish(reply: Reply) {
                if (done.compareAndSet(false, true) && continuation.isActive) continuation.resume(reply)
            }
            val socket =
                client.newWebSocket(
                    request,
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            val timestamp = EdgeSpeech.timestamp(System.currentTimeMillis())
                            webSocket.send(EdgeSpeech.configMessage(timestamp))
                            webSocket.send(EdgeSpeech.ssmlMessage(id, timestamp, ssml))
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (EdgeSpeech.isTurnEnd(text)) {
                                webSocket.close(NORMAL_CLOSE, null)
                                val bytes = audio.toByteArray()
                                finish(
                                    Reply(
                                        if (bytes.isEmpty()) {
                                            VoiceAttempt.Failed(
                                                0,
                                                "empty audio",
                                            )
                                        } else {
                                            VoiceAttempt.Clip(bytes)
                                        },
                                    ),
                                )
                            }
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            EdgeSpeech.audioIn(bytes.toByteArray())?.let { audio.write(it) }
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(NORMAL_CLOSE, null)
                            finish(Reply(VoiceAttempt.Failed(0, "closed $code $reason".trim())))
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            val status = response?.code ?: 0
                            val detail = response?.message?.takeIf { it.isNotBlank() } ?: t.toString()
                            finish(
                                Reply(VoiceAttempt.Failed(status, detail.take(DETAIL_CHARS)), response?.header("Date")),
                            )
                        }
                    },
                )
            continuation.invokeOnCancellation { socket.cancel() }
        }

    private companion object {
        const val TAG = "Rikavon"
        const val FORBIDDEN = 403
        const val NORMAL_CLOSE = 1000
        const val DETAIL_CHARS = 300
        const val CONNECT_TIMEOUT_SECONDS = 6L
        const val READ_TIMEOUT_SECONDS = 12L
        val client: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }
}
