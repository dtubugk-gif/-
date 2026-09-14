package il.rikavon.feature.blocker.ui.schedules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.InstalledApp
import il.rikavon.core.data.model.Schedule
import il.rikavon.core.data.model.ScheduleType
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.ScheduleRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

data class SchedulesUiState(
    val schedules: List<Schedule> = emptyList(),
    val tier: Tier = Tier.FREE,
    val showFreeCap: Boolean = false,
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(private val repository: ScheduleRepository, settings: SettingsRepository) :
    ViewModel() {
        private val freeCap = MutableStateFlow(false)

        val state: StateFlow<SchedulesUiState> =
            combine(repository.schedules, settings.settings, freeCap) {
                list,
                prefs,
                cap,
                ->
                SchedulesUiState(list, Tier.of(prefs.premium), cap)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SchedulesUiState())

        /** Invokes [onAllowed] when the tier permits another schedule; otherwise shows the free-cap note. */
        fun requestNew(onAllowed: () -> Unit) {
            val current = state.value
            if (current.tier.canAddSchedule(current.schedules.size)) onAllowed() else freeCap.value = true
        }

        fun dismissFreeCap() {
            freeCap.value = false
        }

        fun setEnabled(schedule: Schedule, enabled: Boolean) {
            viewModelScope.launch { repository.save(schedule.copy(enabled = enabled)) }
        }

        companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

data class ScheduleEditorUiState(
    val id: Long = Schedule.NEW_ID,
    val name: String = "",
    val type: ScheduleType = ScheduleType.CUSTOM,
    val days: Set<DayOfWeek> = emptySet(),
    val startMinute: Int = DEFAULT_START,
    val endMinute: Int = DEFAULT_END,
    val packages: Set<String> = emptySet(),
    val enabled: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val trackedPackages: Set<String> = emptySet(),
    val loaded: Boolean = false,
) {
    val crossesMidnight: Boolean get() = endMinute <= startMinute

    companion object {
        const val DEFAULT_START = 9 * 60
        const val DEFAULT_END = 17 * 60
    }
}

@HiltViewModel
class ScheduleEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: ScheduleRepository,
    private val installed: InstalledAppsSource,
    limits: LimitsRepository,
) : ViewModel() {
    private val id: Long = savedState.get<String>(ARG_ID)?.toLongOrNull() ?: Schedule.NEW_ID
    private val draft = MutableStateFlow(ScheduleEditorUiState(id = id))
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    val state: StateFlow<ScheduleEditorUiState> =
        combine(draft, apps, limits.limits) { d, list, limitList ->
            val tracked = limitList.map { it.packageName }.toSet()
            d.copy(
                apps =
                    list.sortedWith(
                        compareByDescending<InstalledApp> {
                            it.packageName in tracked
                        }.thenBy { it.label.lowercase() },
                    ),
                trackedPackages = tracked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), draft.value)

    init {
        viewModelScope.launch {
            val existing = if (id != Schedule.NEW_ID) repository.byId(id) else null
            draft.value =
                if (existing != null) {
                    ScheduleEditorUiState(
                        id = existing.id,
                        name = existing.name,
                        type = existing.type,
                        days = existing.days,
                        startMinute = existing.startMinute,
                        endMinute = existing.endMinute,
                        packages = existing.packages,
                        enabled = existing.enabled,
                        loaded = true,
                    )
                } else {
                    draft.value.copy(days = WEEKDAYS, loaded = true)
                }
            apps.value = installed.launchableApps().filter { !it.isSystem }
        }
    }

    fun icon(packageName: String) = installed.icon(packageName)

    fun setName(value: String) {
        draft.value = draft.value.copy(name = value)
    }

    /** Applies a preset: days and hours typical for the type. The user can still change everything. */
    fun setType(type: ScheduleType) {
        val preset =
            when (type) {
                ScheduleType.WORK -> Preset(WEEKDAYS, WORK_START, WORK_END)
                ScheduleType.STUDY -> Preset(WEEKDAYS, STUDY_START, STUDY_END)
                ScheduleType.SLEEP -> Preset(DayOfWeek.entries.toSet(), SLEEP_START, SLEEP_END)
                ScheduleType.CUSTOM -> null
            }
        draft.value =
            draft.value.copy(
                type = type,
                days = preset?.days ?: draft.value.days,
                startMinute = preset?.start ?: draft.value.startMinute,
                endMinute = preset?.end ?: draft.value.endMinute,
            )
    }

    fun toggleDay(day: DayOfWeek) {
        val days = draft.value.days
        draft.value = draft.value.copy(days = if (day in days) days - day else days + day)
    }

    fun setStart(minute: Int) {
        draft.value = draft.value.copy(startMinute = minute)
    }

    fun setEnd(minute: Int) {
        draft.value = draft.value.copy(endMinute = minute)
    }

    fun togglePackage(packageName: String) {
        val packages = draft.value.packages
        draft.value =
            draft.value.copy(
                packages =
                    if (packageName in
                        packages
                    ) {
                        packages - packageName
                    } else {
                        packages + packageName
                    },
            )
    }

    fun save(defaultName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val d = draft.value
            repository.save(
                Schedule(
                    id = d.id,
                    name = d.name.ifBlank { defaultName },
                    type = d.type,
                    days = d.days,
                    startMinute = d.startMinute,
                    endMinute = d.endMinute,
                    packages = d.packages,
                    enabled = d.enabled,
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (id != Schedule.NEW_ID) repository.delete(id)
            onDone()
        }
    }

    private data class Preset(val days: Set<DayOfWeek>, val start: Int, val end: Int)

    companion object {
        const val ARG_ID = "scheduleId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private val WEEKDAYS =
            setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        private const val WORK_START = 9 * 60
        private const val WORK_END = 17 * 60
        private const val STUDY_START = 8 * 60
        private const val STUDY_END = 14 * 60
        private const val SLEEP_START = 23 * 60
        private const val SLEEP_END = 7 * 60
    }
}
