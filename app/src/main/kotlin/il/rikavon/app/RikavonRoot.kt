package il.rikavon.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import il.rikavon.R
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.repo.SettingsRepository
import il.rikavon.core.ui.anim.AnimationSpecs
import il.rikavon.core.ui.anim.rememberSystemReducedMotion
import il.rikavon.core.ui.components.BottomTab
import il.rikavon.core.ui.components.RikavonBottomBar
import il.rikavon.core.ui.theme.RikavonColors
import il.rikavon.core.ui.theme.RikavonTheme
import il.rikavon.feature.blocker.ui.apps.AppPickerScreen
import il.rikavon.feature.blocker.ui.limits.LimitEditorScreen
import il.rikavon.feature.blocker.ui.limits.LimitEditorViewModel
import il.rikavon.feature.blocker.ui.schedules.ScheduleEditorScreen
import il.rikavon.feature.blocker.ui.schedules.ScheduleEditorViewModel
import il.rikavon.feature.blocker.ui.schedules.SchedulesScreen
import il.rikavon.feature.blocker.ui.score.ScoreExplainerScreen
import il.rikavon.feature.blocker.ui.stats.StatsScreen
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.registry.SelectedMascot
import il.rikavon.feature.mascot.ui.LocalNavAnimatedVisibilityScope
import il.rikavon.feature.mascot.ui.LocalSharedTransitionScope
import il.rikavon.feature.mascot.ui.achievements.AchievementsScreen
import il.rikavon.feature.mascot.ui.gallery.MascotGalleryScreen
import il.rikavon.ui.battery.BatteryGuideScreen
import il.rikavon.ui.home.HomeScreen
import il.rikavon.ui.onboarding.OnboardingScreen
import il.rikavon.ui.premium.PremiumScreen
import il.rikavon.ui.privacy.PrivacyScreen
import il.rikavon.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val APPS = "apps"
    const val LIMIT = "limit/{${LimitEditorViewModel.ARG_PACKAGE}}"
    const val SCHEDULES = "schedules"
    const val SCHEDULE = "schedule/{${ScheduleEditorViewModel.ARG_ID}}"
    const val STATS = "stats"
    const val SCORE = "score"
    const val GALLERY = "gallery"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val BATTERY = "battery"
    const val PRIVACY = "privacy"
    const val PREMIUM = "premium"

    /** The four bottom-bar destinations, in bar order. */
    val TOP_LEVEL = listOf(HOME, GALLERY, STATS, SETTINGS)

    fun limit(packageName: String) = "limit/$packageName"

    fun schedule(id: Long) = "schedule/$id"
}

data class RootUiState(
    val settings: Settings? = null,
    val skin: MascotSkin? = null,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository,
    selectedMascot: SelectedMascot,
) : ViewModel() {
    val state: StateFlow<RootUiState> =
        combine(settings.settings, selectedMascot.skin) { s, skin -> RootUiState(s, skin) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RootUiState())

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

@Composable
fun RikavonRoot(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.settings ?: return
    val systemReduced = rememberSystemReducedMotion()
    val reduced =
        when (prefs.reduceMotion) {
            ReduceMotionMode.ON -> true
            ReduceMotionMode.OFF -> false
            ReduceMotionMode.SYSTEM -> systemReduced
        }
    val accent = state.skin?.let { Color(it.themeColorArgb) } ?: RikavonColors.DefaultAccent
    val surfaceTint = state.skin?.surfaceTintArgb?.let { Color(it) }

    RikavonTheme(
        accent = accent,
        surfaceTint = surfaceTint,
        dynamicColor = prefs.dynamicColor,
        reducedMotion = reduced,
    ) {
        RikavonNavHost(
            startDestination = if (prefs.onboardingDone) Routes.HOME else Routes.ONBOARDING,
            reduced = reduced,
        )
    }
}

@Composable
private fun RikavonNavHost(startDestination: String, reduced: Boolean) {
    val navController = rememberNavController()
    val duration = AnimationSpecs.SCREEN_TRANSITION_MILLIS
    val bottomBar: @Composable () -> Unit = { TopLevelBar(navController) }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    when {
                        reduced -> fadeIn(AnimationSpecs.Reduced)
                        isTabSwitch() -> tabEnter()
                        else ->
                            fadeIn(tween(duration)) +
                                slideIntoContainer(
                                    SlideDirection.Start,
                                    tween(duration, easing = AnimationSpecs.Emphasized),
                                ) {
                                    it / SLIDE_FRACTION
                                }
                    }
                },
                exitTransition = {
                    when {
                        reduced -> fadeOut(AnimationSpecs.Reduced)
                        isTabSwitch() -> tabExit()
                        else -> fadeOut(tween(AnimationSpecs.COMPONENT_MILLIS))
                    }
                },
                popEnterTransition = {
                    when {
                        reduced -> fadeIn(AnimationSpecs.Reduced)
                        isTabSwitch() -> tabEnter()
                        else -> fadeIn(tween(duration))
                    }
                },
                popExitTransition = {
                    when {
                        reduced -> fadeOut(AnimationSpecs.Reduced)
                        isTabSwitch() -> tabExit()
                        else ->
                            fadeOut(tween(AnimationSpecs.COMPONENT_MILLIS)) +
                                slideOutOfContainer(
                                    SlideDirection.End,
                                    tween(duration, easing = AnimationSpecs.Emphasized),
                                ) {
                                    it / SLIDE_FRACTION
                                }
                    }
                },
            ) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(
                        onDone = {
                            navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.HOME) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        HomeScreen(
                            onOpenApps = { navController.navigate(Routes.APPS) },
                            onOpenLimit = { navController.navigate(Routes.limit(it)) },
                            onOpenSchedules = { navController.navigate(Routes.SCHEDULES) },
                            onOpenAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                            onOpenSettings = { navController.navigateTopLevel(Routes.SETTINGS) },
                            onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                            bottomBar = bottomBar,
                        )
                    }
                }
                composable(Routes.APPS) {
                    AppPickerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLimit = { navController.navigate(Routes.limit(it)) },
                        onOpenPermissions = { navController.navigate(Routes.ONBOARDING) },
                    )
                }
                composable(
                    route = Routes.LIMIT,
                    arguments = listOf(navArgument(LimitEditorViewModel.ARG_PACKAGE) { type = NavType.StringType }),
                ) {
                    LimitEditorScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.SCHEDULES) {
                    SchedulesScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.schedule(it)) },
                    )
                }
                composable(
                    route = Routes.SCHEDULE,
                    arguments = listOf(navArgument(ScheduleEditorViewModel.ARG_ID) { type = NavType.StringType }),
                ) {
                    ScheduleEditorScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        onBack = null,
                        onOpenScore = { navController.navigate(Routes.SCORE) },
                        onOpenApps = { navController.navigate(Routes.APPS) },
                        onOpenPermissions = { navController.navigate(Routes.ONBOARDING) },
                        bottomBar = bottomBar,
                    )
                }
                composable(Routes.SCORE) { ScoreExplainerScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.GALLERY) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        MascotGalleryScreen(onBack = null, bottomBar = bottomBar)
                    }
                }
                composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = null,
                        onOpenGallery = { navController.navigateTopLevel(Routes.GALLERY) },
                        onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
                        onOpenBattery = { navController.navigate(Routes.BATTERY) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenPremium = { navController.navigate(Routes.PREMIUM) },
                        onOpenScore = { navController.navigate(Routes.SCORE) },
                        bottomBar = bottomBar,
                    )
                }
                composable(Routes.BATTERY) { BatteryGuideScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.PRIVACY) { PrivacyScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.PREMIUM) { PremiumScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun TopLevelBar(navController: NavHostController) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val selected = Routes.TOP_LEVEL.indexOf(route).coerceAtLeast(0)
    val tabs =
        listOf(
            BottomTab(NavIcons.Home, stringResource(R.string.nav_home)),
            BottomTab(NavIcons.Gallery, stringResource(R.string.nav_gallery)),
            BottomTab(NavIcons.Stats, stringResource(R.string.nav_stats)),
            BottomTab(NavIcons.Settings, stringResource(R.string.nav_settings)),
        )
    RikavonBottomBar(
        tabs = tabs,
        selected = selected,
        onSelect = { navController.navigateTopLevel(Routes.TOP_LEVEL[it]) },
    )
}

private fun NavHostController.navigateTopLevel(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private const val SLIDE_FRACTION = 6

/** Both ends of the transition are bottom-bar destinations: fade-through instead of a slide. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in Routes.TOP_LEVEL && targetState.destination.route in Routes.TOP_LEVEL

private fun tabEnter() =
    fadeIn(tween(AnimationSpecs.TAB_SWITCH_MILLIS, easing = AnimationSpecs.Emphasized)) +
        scaleIn(
            tween(AnimationSpecs.TAB_SWITCH_MILLIS, easing = AnimationSpecs.Emphasized),
            initialScale = AnimationSpecs.TAB_SWITCH_SCALE,
        )

private fun tabExit() =
    fadeOut(tween(AnimationSpecs.MICRO_MILLIS)) +
        scaleOut(tween(AnimationSpecs.MICRO_MILLIS), targetScale = AnimationSpecs.TAB_SWITCH_SCALE)
