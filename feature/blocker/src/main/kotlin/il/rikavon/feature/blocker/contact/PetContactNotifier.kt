package il.rikavon.feature.blocker.contact

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
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.feature.blocker.R
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.registry.MascotTexts
import il.rikavon.feature.mascot.render.MascotBitmapRenderer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The pet's two ways of reaching out: a heads-up message in its own voice, and an incoming call that takes
 * over the screen like a real one (call-style notification with a full-screen intent, answered in
 * [PetCallActivity]). Everything is local; the "caller" is the mascot.
 */
@Singleton
class PetContactNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: PermissionChecker,
    private val texts: MascotTexts,
    private val renderer: MascotBitmapRenderer,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    fun message(skin: MascotSkin, stage: MascotStage, contact: PetContact.Message, appLabel: String, language: String) {
        if (!permissions.hasNotifications()) return
        ensureChannels()
        val body =
            when (contact.kind) {
                MessageKind.NEAR_LIMIT ->
                    context.getString(
                        R.string.contact_message_near,
                        appLabel,
                        contact.minutesLeft,
                    )
                MessageKind.AT_LIMIT -> context.getString(R.string.contact_message_limit, appLabel)
            } + "\n" + texts.stageText(skin, stage, language)
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
        runCatching { manager.notify(MESSAGE_ID_BASE + (contact.packageName.hashCode() and ID_MASK), notification) }
    }

    /**
     * Rings. The call screen is started directly (allowed in the background because the user granted
     * "display over other apps", the same grant the block screen needs) and, so the ring survives a locked
     * screen and shows Answer / Decline in the shade, posted as a call-style notification as well.
     */
    fun call(skin: MascotSkin, stage: MascotStage, contact: PetContact.Call, appLabel: String, language: String) {
        val petName = texts.name(skin, language)
        val lines = callLines(skin, stage, contact, petName, appLabel, language)
        val ringing = callActivity(petName, appLabel, lines, answered = false)
        runCatching { context.startActivity(ringing) }
        if (!permissions.hasNotifications()) return
        ensureChannels()
        val answered = callActivity(petName, appLabel, lines, answered = true)
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
                .setFullScreenIntent(fullScreen, true)
                .setContentIntent(fullScreen)
                .build()
        runCatching { manager.notify(CALL_ID, notification) }
        mainHandler.removeCallbacksAndMessages(RING_TOKEN)
        mainHandler.postDelayed({ cancelCall() }, RING_TOKEN, AnimationSpecs.CALL_RING_MILLIS)
    }

    fun cancelCall() {
        mainHandler.removeCallbacksAndMessages(RING_TOKEN)
        runCatching { manager.cancel(CALL_ID) }
    }

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
                CallReason.NEAR_LIMIT -> context.getString(R.string.contact_call_near, appLabel, contact.minutesLeft)
                CallReason.BLOCKED -> context.getString(R.string.contact_call_blocked, appLabel)
            }
        return listOf(
            context.getString(R.string.contact_call_intro, petName),
            reason,
            texts.stageText(skin, stage, language),
            context.getString(R.string.contact_call_outro),
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
