package il.rikavon.feature.blocker.contact

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.feature.blocker.R
import il.rikavon.feature.blocker.overlay.OverlayController
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.render.MascotBitmapRenderer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pet's two ways of reaching out: a heads-up message in its own voice, and an incoming call that takes
 * over the screen like a real one. Everything is local; the "caller" is the mascot.
 *
 * The ring itself is a window of this app over whatever is open (the same mechanism as the block screen,
 * which the system cannot refuse once "display over other apps" is granted); answering opens the real,
 * two-way call in [PetCallActivity]. Without that permission, or with the phone locked, the call activity
 * is started directly and a call-style notification with a full-screen intent does the ringing.
 */
@Singleton
class PetContactNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionChecker,
    private val texts: MascotTexts,
    private val renderer: MascotBitmapRenderer,
    private val overlay: OverlayController,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ringer = CallRinger(context)
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** Runs when the current ring ends without an answer (declined, timed out); cleared once answered. */
    private var onUnanswered: (() -> Unit)? = null

    fun message(skin: MascotSkin, stage: MascotStage, contact: PetContact.Message, appLabel: String, language: String) {
        val body =
            when (contact.kind) {
                MessageKind.NEAR_LIMIT ->
                    context.getString(
                        R.string.contact_message_near,
                        appLabel,
                        contact.minutesLeft,
                    )
                MessageKind.AT_LIMIT -> context.getString(R.string.contact_message_limit, appLabel)
                MessageKind.PLEAD -> context.getString(R.string.contact_message_stop, appLabel)
            }
        post(skin, stage, contact.packageName, body, language)
    }

    /** The nudge inside an app: "20 minutes in Instagram. Just saying." */
    fun nudge(skin: MascotSkin, stage: MascotStage, contact: PetContact.Nudge, appLabel: String, language: String) {
        post(
            skin,
            stage,
            contact.packageName,
            context.getString(R.string.contact_message_nudge, contact.minutes, appLabel),
            language,
        )
    }

    private fun post(skin: MascotSkin, stage: MascotStage, packageName: String, line: String, language: String) {
        if (!permissions.hasNotifications()) return
        ensureChannels()
        val body = line + "\n" + texts.stageText(skin, stage, language)
        val notification =
            NotificationCompat
                .Builder(context, MESSAGES_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_rikavon)
                .setLargeIcon(renderer.render(skin, stage, ICON_PX))
                .setContentTitle(texts.name(skin, language))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        runCatching { manager.notify(MESSAGE_ID_BASE + (packageName.hashCode() and ID_MASK), notification) }
    }

    /**
     * Rings about [contact]. The ring ends by itself after [AnimationSpecs.CALL_RING_MILLIS]; [onUnanswered]
     * runs when it ends without an answer (declined, timed out), so a block can put its screen up instead.
     * A message asking to stop is posted alongside, since the call leaves the shade when it stops ringing.
     */
    fun call(
        skin: MascotSkin,
        stage: MascotStage,
        contact: PetContact.Call,
        appLabel: String,
        language: String,
        reduceMotion: ReduceMotionMode = ReduceMotionMode.SYSTEM,
        onUnanswered: () -> Unit = {},
    ) {
        cancelCall()
        this.onUnanswered = onUnanswered
        val petName = texts.name(skin, language)
        val lines = callLines(skin, stage, contact, petName, appLabel, language)
        val ringing = callActivity(petName, appLabel, lines, answered = false)
        val answered = callActivity(petName, appLabel, lines, answered = true)
        val ringsHere = permissions.state().overlay && !locked()
        if (ringsHere) {
            ringer.start()
            overlay.showCall(
                OverlayController.CallRequest(
                    packageName = contact.packageName,
                    skin = skin,
                    petName = petName,
                    appLabel = appLabel,
                    appearance = overlay.appearance(reduceMotion),
                    onAnswer = {
                        this.onUnanswered = null
                        runCatching { context.startActivity(answered) }
                        cancelCall()
                    },
                    onDecline = ::decline,
                ),
            )
        } else {
            runCatching { context.startActivity(ringing) }
        }
        mainHandler.postDelayed({ decline() }, RING_TOKEN, AnimationSpecs.CALL_RING_MILLIS)
        if (!permissions.hasNotifications()) return
        ensureChannels()
        // The call goes away when it stops ringing; the message stays in the shade and says what it wanted.
        post(skin, stage, contact.packageName, context.getString(R.string.contact_message_stop, appLabel), language)
        notifyCall(skin, stage, petName, appLabel, ringing, answered, silent = ringsHere)
    }

    /** The ring ended without an answer: everything down, then whoever asked for the call decides what next. */
    fun decline() {
        val callback = onUnanswered
        cancelCall()
        callback?.invoke()
    }

    /** Stops ringing everywhere (answered, hung up, or superseded) without telling the caller. */
    fun cancelCall() {
        mainHandler.removeCallbacksAndMessages(RING_TOKEN)
        onUnanswered = null
        ringer.stop()
        overlay.hideIf(OverlayController.Kind.CALL)
        runCatching { manager.cancel(CALL_ID) }
    }

    /**
     * The call in the shade: Answer / Decline, and a full-screen intent so it covers a locked screen. Silent
     * when the ring already plays from the overlay, so the phone rings once, not twice.
     */
    private fun notifyCall(
        skin: MascotSkin,
        stage: MascotStage,
        petName: String,
        appLabel: String,
        ringing: Intent,
        answered: Intent,
        silent: Boolean,
    ) {
        val fullScreen = PendingIntent.getActivity(context, REQUEST_RING, ringing, PENDING_FLAGS)
        val answer = PendingIntent.getActivity(context, REQUEST_ANSWER, answered, PENDING_FLAGS)
        val decline =
            PendingIntent.getBroadcast(
                context,
                REQUEST_DECLINE,
                Intent(context, PetCallReceiver::class.java),
                PENDING_FLAGS,
            )
        val caller =
            Person
                .Builder()
                .setName(petName)
                .setIcon(IconCompat.createWithBitmap(renderer.render(skin, stage, ICON_PX)))
                .setImportant(true)
                .build()
        val notification =
            NotificationCompat
                .Builder(context, CALLS_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_rikavon)
                .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer))
                .setContentTitle(petName)
                .setContentText(context.getString(R.string.call_about, appLabel))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setSilent(silent)
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .build()
        runCatching { manager.notify(CALL_ID, notification) }
    }

    private fun locked(): Boolean = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** The pet's script for the call: who it is, why, one line in its own voice, and the ask. */
    private fun callLines(
        skin: MascotSkin,
        stage: MascotStage,
        contact: PetContact.Call,
        petName: String,
        appLabel: String,
        language: String,
    ): List<String> {
        val reason =
            when (contact.reason) {
                CallReason.NEAR_LIMIT ->
                    listOf(
                        context.getString(R.string.contact_call_near, appLabel, contact.minutesLeft),
                    )
                CallReason.BLOCKED -> listOf(context.getString(R.string.contact_call_blocked, appLabel))
                CallReason.PLEAD ->
                    listOf(
                        context.getString(R.string.contact_call_plead_no),
                        context.getString(R.string.contact_call_plead_rot, appLabel),
                    )
            }
        return (
            listOf(context.getString(R.string.contact_call_intro, petName)) +
                reason +
                texts.stageText(skin, stage, language) +
                context.getString(R.string.contact_call_outro)
        ).filter { it.isNotBlank() }
    }

    private fun callActivity(petName: String, appLabel: String, lines: List<String>, answered: Boolean): Intent =
        Intent(context, PetCallActivity::class.java)
            .putExtra(PetCallActivity.EXTRA_PET_NAME, petName)
            .putExtra(PetCallActivity.EXTRA_APP_LABEL, appLabel)
            .putExtra(PetCallActivity.EXTRA_LINES, lines.toTypedArray())
            .putExtra(PetCallActivity.EXTRA_ANSWERED, answered)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, REQUEST_OPEN, it, PENDING_FLAGS)
        }

    private fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGES_CHANNEL_ID,
                context.getString(R.string.contact_messages_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.contact_messages_channel_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CALLS_CHANNEL_ID,
                context.getString(R.string.contact_calls_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.contact_calls_channel_description)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = RING_VIBRATION
            },
        )
    }

    companion object {
        const val MESSAGES_CHANNEL_ID = "rikavon_pet_messages"
        const val CALLS_CHANNEL_ID = "rikavon_pet_calls"
        private const val CALL_ID = 3001
        private const val MESSAGE_ID_BASE = 3100
        private const val ID_MASK = 0x3ff
        private const val ICON_PX = 192
        private const val REQUEST_RING = 31
        private const val REQUEST_ANSWER = 32
        private const val REQUEST_DECLINE = 33
        private const val REQUEST_OPEN = 34
        private const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        private val RING_TOKEN = Any()
        private val RING_VIBRATION = longArrayOf(0, 600, 400, 600, 400, 600)
    }
}
