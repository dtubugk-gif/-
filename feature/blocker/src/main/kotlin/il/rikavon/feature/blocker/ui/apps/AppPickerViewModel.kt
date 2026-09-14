package il.rikavon.feature.blocker.ui.apps

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AppLimit
import il.rikavon.core.data.model.InstalledApp
import il.rikavon.core.data.permissions.PermissionChecker
import il.rikavon.core.data.repo.LimitsRepository
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.data.repo.UsageRepository
import il.rikavon.core.data.usage.InstalledAppsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppSort { USAGE, NAME }

data class AppRow(val app: InstalledApp, val weekMinutes: Int, val limit: AppLimit?)

data class AppPickerUiState(
    val rows: List<AppRow> = emptyList(),
    val query: String = "",
    val sort: AppSort = AppSort.USAGE,
    val showSystem: Boolean = false,
    val hasUsagePermission: Boolean = true,
    val tier: Tier = Tier.FREE,
    val limitedCount: Int = 0,
    val loading: Boolean = true,
    val showFreeCap: Boolean = false,
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    private val installed: InstalledAppsSource,
    private val limits: LimitsRepository,
    usage: UsageRepository,
    settings: SettingsRepository,
    permissions: PermissionChecker,
) : ViewModel() {
    private val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(AppSort.USAGE)
    private val showSystem = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)
    private val freeCap = MutableStateFlow(false)
    private val hasUsagePermission = permissions.hasUsageAccess()

    val state: StateFlow<AppPickerUiState> =
        combine(
            combine(apps, query, sort, showSystem, loading) { a, q, s, sys, l -> Filters(a, q, s, sys, l) },
            limits.limits,
            usage.observeHistory(WEEK_DAYS),
            settings.settings,
            freeCap,
        ) { filters, limitList, history, prefs, cap ->
            val minutesByPackage =
                history.groupBy { it.packageName }.mapValues { (_, rows) ->
                    rows.sumOf { it.minutes }
                }
            val limitByPackage = limitList.associateBy { it.packageName }
            val rows =
                filters.apps
                    .asSequence()
                    .filter { filters.showSystem || !it.isSystem || it.packageName in limitByPackage }
                    .filter { filters.query.isBlank() || it.label.contains(filters.query, ignoreCase = true) }
                    .map { AppRow(it, minutesByPackage[it.packageName] ?: 0, limitByPackage[it.packageName]) }
                    .sortedWith(
                        when (filters.sort) {
                            AppSort.USAGE ->
                                compareByDescending<AppRow> { it.limit != null }
                                    .thenByDescending { it.weekMinutes }
                                    .thenBy { it.app.label.lowercase() }
                            AppSort.NAME -> compareBy { it.app.label.lowercase() }
                        },
                    ).toList()
            AppPickerUiState(
                rows = rows,
                query = filters.query,
                sort = filters.sort,
                showSystem = filters.showSystem,
                hasUsagePermission = hasUsagePermission,
                tier = Tier.of(prefs.premium),
                limitedCount = limitList.size,
                loading = filters.loading,
                showFreeCap = cap,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), AppPickerUiState())

    init {
        viewModelScope.launch {
            apps.value = installed.launchableApps()
            loading.value = false
        }
    }

    fun icon(packageName: String): Drawable? = installed.icon(packageName)

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: AppSort) {
        sort.value = value
    }

    fun setShowSystem(value: Boolean) {
        showSystem.value = value
    }

    fun dismissFreeCap() {
        freeCap.value = false
    }

    /**
     * Toggles a limit for [app]. Returns true when the caller should open the limit editor
     * (a new limit was created). Enforces the tier cap gently.
     */
    fun toggle(app: InstalledApp, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val existing = limits.byPackage(app.packageName)
            if (existing != null) {
                limits.remove(app.packageName)
                return@launch
            }
            val current = state.value
            if (!current.tier.canAddLimit(current.limitedCount)) {
                freeCap.value = true
                return@launch
            }
            limits.save(
                AppLimit(
                    packageName = app.packageName,
                    limitMinutes = AppLimit.DEFAULT_MINUTES,
                    fullBlock = false,
                    enabled = true,
                    createdAt = 0L,
                ),
            )
            onCreated(app.packageName)
        }
    }

    private data class Filters(
        val apps: List<InstalledApp>,
        val query: String,
        val sort: AppSort,
        val showSystem: Boolean,
        val loading: Boolean,
    )

    companion object {
        private const val WEEK_DAYS = 7
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
