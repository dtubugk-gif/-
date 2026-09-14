package il.rikavon.core.data.repo

import il.rikavon.core.data.di.IoDispatcher
import il.rikavon.core.data.model.Achievement
import il.rikavon.core.data.model.AchievementId
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.DailyAppUsage
import il.rikavon.core.data.model.DailySummary
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val settings: SettingsDto,
    val limits: List<LimitDto>,
    val schedules: List<ScheduleDto>,
    val summaries: List<SummaryDto>,
    val achievements: List<AchievementDto>,
    val usage: List<UsageDto>,
)

@Serializable
data class SettingsDto(
    val mascotId: String,
    val strictMode: Boolean,
    val dailySummaryEnabled: Boolean,
    val dailySummaryHour: Int,
    val dynamicColor: Boolean,
    val language: String,
    val onboardingDone: Boolean,
    val lastRolloverDate: String?,
    val currentStreak: Int,
    val bestStreak: Int,
    val reduceMotion: String,
    val trackingEnabled: Boolean,
    val soundsEnabled: Boolean = true,
    val voiceEnabled: Boolean = true,
)

@Serializable
data class LimitDto(
    val packageName: String,
    val limitMinutes: Int,
    val fullBlock: Boolean,
    val enabled: Boolean,
    val createdAt: Long,
)

@Serializable
data class ScheduleDto(
    val id: Long,
    val name: String,
    val type: String,
    val days: List<String>,
    val startMinute: Int,
    val endMinute: Int,
    val packages: List<String>,
    val enabled: Boolean,
)

@Serializable
data class SummaryDto(
    val date: String,
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

@Serializable
data class AchievementDto(val id: String, val unlockedAt: Long)

@Serializable
data class UsageDto(val date: String, val packageName: String, val minutes: Int, val opens: Int)

/** Local JSON export/import. Nothing ever leaves the device unless the user picks a file location. */
@Singleton
class BackupRepository @Inject constructor(
    private val json: Json,
    private val settings: SettingsRepository,
    private val limits: LimitsRepository,
    private val schedules: ScheduleRepository,
    private val summaries: DailySummaryRepository,
    private val achievements: AchievementRepository,
    private val usage: UsageRepository,
    private val time: TimeSource,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    suspend fun export(out: OutputStream) =
        withContext(io) {
            val payload = buildPayload()
            out.bufferedWriter().use { it.write(json.encodeToString(BackupPayload.serializer(), payload)) }
        }

    suspend fun import(input: InputStream): Result<Unit> =
        withContext(io) {
            runCatching {
                val text = input.bufferedReader().use { it.readText() }
                val payload = json.decodeFromString(BackupPayload.serializer(), text)
                require(payload.version in 1..FORMAT_VERSION) { "Unsupported backup version ${payload.version}" }
                apply(payload)
            }
        }

    private suspend fun buildPayload(): BackupPayload {
        val s = settings.current()
        return BackupPayload(
            version = FORMAT_VERSION,
            exportedAt = time.nowMillis(),
            settings =
                SettingsDto(
                    mascotId = s.selectedMascotId,
                    strictMode = s.strictMode,
                    dailySummaryEnabled = s.dailySummaryEnabled,
                    dailySummaryHour = s.dailySummaryHour,
                    dynamicColor = s.dynamicColor,
                    language = s.language.tag,
                    onboardingDone = s.onboardingDone,
                    lastRolloverDate = s.lastRolloverDate?.toString(),
                    currentStreak = s.currentStreak,
                    bestStreak = s.bestStreak,
                    reduceMotion = s.reduceMotion.name,
                    trackingEnabled = s.trackingEnabled,
                    soundsEnabled = s.soundsEnabled,
                    voiceEnabled = s.voiceEnabled,
                ),
            limits =
                limits.all().map {
                    LimitDto(it.packageName, it.limitMinutes, it.fullBlock, it.enabled, it.createdAt)
                },
            schedules =
                schedules.all().map {
                    ScheduleDto(
                        id = it.id,
                        name = it.name,
                        type = it.type.name,
                        days = it.days.map { d -> d.name },
                        startMinute = it.startMinute,
                        endMinute = it.endMinute,
                        packages = it.packages.toList(),
                        enabled = it.enabled,
                    )
                },
            summaries =
                summaries.all().map {
                    SummaryDto(
                        date = it.date.toString(),
                        score = it.score,
                        allUnderLimit = it.allUnderLimit,
                        totalMinutes = it.totalMinutes,
                        totalOpens = it.totalOpens,
                        firstOpenMinute = it.firstOpenMinute,
                        nightOpens = it.nightOpens,
                        blocksTriggered = it.blocksTriggered,
                        minScore = it.minScore,
                        recoveredFromLow = it.recoveredFromLow,
                        schedulesKept = it.schedulesKept,
                    )
                },
            achievements =
                achievements.exportAll().mapNotNull { a ->
                    a.unlockedAt?.let { AchievementDto(a.id.key, it) }
                },
            usage =
                usage.exportAll().map {
                    UsageDto(
                        it.date.toString(),
                        it.packageName,
                        it.minutes,
                        it.opens,
                    )
                },
        )
    }

    private suspend fun apply(payload: BackupPayload) {
        val dto = payload.settings
        settings.restore(
            Settings.DEFAULT.copy(
                selectedMascotId = dto.mascotId,
                strictMode = dto.strictMode,
                dailySummaryEnabled = dto.dailySummaryEnabled,
                dailySummaryHour = dto.dailySummaryHour,
                dynamicColor = dto.dynamicColor,
                language = AppLanguage.fromTag(dto.language),
                onboardingDone = dto.onboardingDone,
                lastRolloverDate = dto.lastRolloverDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                currentStreak = dto.currentStreak,
                bestStreak = dto.bestStreak,
                reduceMotion =
                    runCatching { ReduceMotionMode.valueOf(dto.reduceMotion) }
                        .getOrDefault(ReduceMotionMode.SYSTEM),
                trackingEnabled = dto.trackingEnabled,
                soundsEnabled = dto.soundsEnabled,
                voiceEnabled = dto.voiceEnabled,
            ),
        )
        limits.replaceAll(
            payload.limits.map {
                AppLimit(
                    packageName = it.packageName,
                    limitMinutes = it.limitMinutes.coerceIn(AppLimit.MIN_MINUTES, AppLimit.MAX_MINUTES),
                    fullBlock = it.fullBlock,
                    enabled = it.enabled,
                    createdAt = it.createdAt,
                )
            },
        )
        schedules.replaceAll(
            payload.schedules.map {
                Schedule(
                    id = it.id,
                    name = it.name,
                    type = runCatching { ScheduleType.valueOf(it.type) }.getOrDefault(ScheduleType.CUSTOM),
                    days = it.days.mapNotNull { d -> runCatching { DayOfWeek.valueOf(d) }.getOrNull() }.toSet(),
                    startMinute = it.startMinute,
                    endMinute = it.endMinute,
                    packages = it.packages.toSet(),
                    enabled = it.enabled,
                )
            },
        )
        summaries.importAll(
            payload.summaries.mapNotNull {
                val date = runCatching { LocalDate.parse(it.date) }.getOrNull() ?: return@mapNotNull null
                DailySummary(
                    date = date,
                    score = it.score,
                    allUnderLimit = it.allUnderLimit,
                    totalMinutes = it.totalMinutes,
                    totalOpens = it.totalOpens,
                    firstOpenMinute = it.firstOpenMinute,
                    nightOpens = it.nightOpens,
                    blocksTriggered = it.blocksTriggered,
                    minScore = it.minScore,
                    recoveredFromLow = it.recoveredFromLow,
                    schedulesKept = it.schedulesKept,
                )
            },
        )
        achievements.importAll(
            payload.achievements.mapNotNull { a ->
                AchievementId.fromKey(a.id)?.let { Achievement(it, a.unlockedAt) }
            },
        )
        usage.importAll(
            payload.usage.mapNotNull {
                val date = runCatching { LocalDate.parse(it.date) }.getOrNull() ?: return@mapNotNull null
                DailyAppUsage(date, it.packageName, it.minutes, it.opens)
            },
        )
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_NAME_PREFIX = "rikavon-backup-"
        const val MIME_TYPE = "application/json"
    }
}
