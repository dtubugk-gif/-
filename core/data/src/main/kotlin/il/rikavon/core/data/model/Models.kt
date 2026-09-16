package il.rikavon.core.data.model

import java.time.DayOfWeek
import java.time.LocalDate

/** A per-app daily limit configured by the user. */
data class AppLimit(
    val packageName: String,
    val limitMinutes: Int,
    val fullBlock: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
    /** Opens allowed per day; 0 means no open limit. */
    val maxOpens: Int = 0,
    /** Longest single sitting in minutes before a forced break; 0 means no session limit. */
    val sessionMinutes: Int = 0,
    /** The pet calls and begs the moment this app opens, whatever the limit says. On unless switched off. */
    val callOnOpen: Boolean = true,
) {
    companion object {
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 240
        const val DEFAULT_MINUTES = 30
        const val MIN_OPENS = 1
        const val MAX_OPENS = 60
        const val DEFAULT_OPENS = 10
        const val MIN_SESSION_MINUTES = 5
        const val MAX_SESSION_MINUTES = 120
        const val DEFAULT_SESSION_MINUTES = 20

        /** How long the break after a session limit lasts. */
        const val BREAK_MINUTES = 10
    }
}

/** Live usage of one app for the current day. */
data class AppUsage(val packageName: String, val minutes: Int, val opens: Int, val openTimestamps: List<Long>) {
    companion object {
        fun empty(packageName: String) = AppUsage(packageName, minutes = 0, opens = 0, openTimestamps = emptyList())
    }
}

/** Snapshot of everything we know about today's usage. */
data class DayUsageSnapshot(
    val date: LocalDate,
    val computedAt: Long,
    val perApp: Map<String, AppUsage>,
    val foregroundPackage: String?,
    val screenOn: Boolean,
) {
    fun usageOf(packageName: String): AppUsage = perApp[packageName] ?: AppUsage.empty(packageName)

    companion object {
        fun empty(date: LocalDate, now: Long) =
            DayUsageSnapshot(date, now, perApp = emptyMap(), foregroundPackage = null, screenOn = true)
    }
}

/** Persisted usage of one app on one day. */
data class DailyAppUsage(val date: LocalDate, val packageName: String, val minutes: Int, val opens: Int)

enum class ScheduleType { WORK, STUDY, SLEEP, CUSTOM }

/**
 * A recurring block window. When [endMinute] <= [startMinute] the window crosses midnight
 * (e.g. sleep 23:00 -> 07:00).
 */
data class Schedule(
    val id: Long,
    val name: String,
    val type: ScheduleType,
    val days: Set<DayOfWeek>,
    val startMinute: Int,
    val endMinute: Int,
    val packages: Set<String>,
    val enabled: Boolean,
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    companion object {
        const val NEW_ID = 0L
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** End-of-day facts used for streaks, achievements and statistics. */
data class DailySummary(
    val date: LocalDate,
    val score: Int,
    val allUnderLimit: Boolean,
    val totalMinutes: Int,
    val totalOpens: Int,
    val firstOpenMinute: Int?,
    val nightOpens: Int,
    val blocksTriggered: Int,
    val minScore: Int,
    val recoveredFromLow: Boolean,
    val schedulesKept: Int,
)

enum class AchievementId(val key: String) {
    FIRST_LIMIT("first_limit"),
    FIRST_CLEAN_DAY("first_clean_day"),
    STREAK_3("streak_3"),
    STREAK_7("streak_7"),
    STREAK_30("streak_30"),
    FEWER_OPENS_50("fewer_opens_50"),
    SCORE_90("score_90"),
    PERFECT_100("perfect_100"),
    NIGHT_QUIET_3("night_quiet_3"),
    SCHEDULE_KEPT_5("schedule_kept_5"),
    LATE_START_5("late_start_5"),
    RECOVERY("recovery"),
    ;

    companion object {
        fun fromKey(key: String): AchievementId? = entries.firstOrNull { it.key == key }
    }
}

data class Achievement(val id: AchievementId, val unlockedAt: Long?) {
    val unlocked: Boolean get() = unlockedAt != null
}

/** A launchable installed application. */
data class InstalledApp(val packageName: String, val label: String, val isSystem: Boolean)

data class BlockEvent(val timestamp: Long, val packageName: String, val reason: BlockReason)

enum class BlockReason {
    LIMIT_REACHED,
    FULL_BLOCK,
    SCHEDULE,

    /** The user hit "block now" for a while. */
    PAUSED,

    /** The app was opened more times than its daily open count allows. */
    OPENS_REACHED,

    /** One sitting ran past the session length; this is the break. */
    SESSION,

    /** A website on the block list came up in the browser. */
    WEBSITE,

    /** A blocked feed inside an app (Shorts, Reels) came up. */
    FEED,
}

/** The short-video feeds that can be blocked inside their app without blocking the app. */
enum class FeedFeature(val packageName: String) {
    YOUTUBE_SHORTS("com.google.android.youtube"),
    INSTAGRAM_REELS("com.instagram.android"),
}

/** English is the default UI language; [HEBREW] switches to the RTL resources; [SYSTEM] follows the device. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    HEBREW("he"),
    ENGLISH("en"),
    ;

    companion object {
        /** Absent (fresh install) → [ENGLISH]; an empty stored tag → [SYSTEM]. */
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

enum class ReduceMotionMode { SYSTEM, ON, OFF }

data class Settings(
    val selectedMascotId: String,
    val strictMode: Boolean,
    val dailySummaryEnabled: Boolean,
    val dailySummaryHour: Int,
    val dynamicColor: Boolean,
    val language: AppLanguage,
    val onboardingDone: Boolean,
    val lastRolloverDate: LocalDate?,
    val currentStreak: Int,
    val bestStreak: Int,
    val premium: Boolean,
    val reduceMotion: ReduceMotionMode,
    val trackingEnabled: Boolean,
    val freeTierNoticeShown: Boolean,
    val soundsEnabled: Boolean,
    /** The pet reads its lines aloud through the device's text-to-speech engine. */
    val voiceEnabled: Boolean,
    /** Heads-up messages from the pet when an app nears or reaches its limit. */
    val petMessagesEnabled: Boolean,
    /** Incoming-call style interruptions from the pet when the user keeps going anyway. */
    val petCallsEnabled: Boolean,
    /** After a limit changes, the next open of that app waits behind a ten-second breathing pause. */
    val breathingGateEnabled: Boolean,
    /** Inside a limited app the pet nudges every this many minutes of one sitting; 0 is off. */
    val reminderMinutes: Int,
    /** Salted hash of the settings PIN; null when settings are not locked. Never exported. */
    val pinHash: String?,
) {
    /** Settings are locked behind a PIN. */
    val locked: Boolean get() = pinHash != null

    companion object {
        const val DEFAULT_MASCOT_ID = "potato"
        const val DEFAULT_SUMMARY_HOUR = 21
        val REMINDER_OPTIONS = listOf(0, 10, 15, 30)
        val DEFAULT =
            Settings(
                selectedMascotId = DEFAULT_MASCOT_ID,
                strictMode = false,
                dailySummaryEnabled = false,
                dailySummaryHour = DEFAULT_SUMMARY_HOUR,
                dynamicColor = false,
                language = AppLanguage.ENGLISH,
                onboardingDone = false,
                lastRolloverDate = null,
                currentStreak = 0,
                bestStreak = 0,
                premium = false,
                reduceMotion = ReduceMotionMode.SYSTEM,
                trackingEnabled = true,
                freeTierNoticeShown = false,
                soundsEnabled = true,
                voiceEnabled = true,
                petMessagesEnabled = true,
                petCallsEnabled = true,
                breathingGateEnabled = true,
                reminderMinutes = 0,
                pinHash = null,
            )
    }
}
