package il.rikavon.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.repo.BackupRepository
import il.rikavon.core.ui.components.ChoiceOption
import il.rikavon.core.ui.components.ChoiceSheet
import il.rikavon.core.ui.components.GroupCard
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.RikavonLargeTopBar
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SettingNavRow
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.rememberLargeTopBarBehavior
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.components.rikavonSliderColors
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.mascot.ui.UiLanguage
import java.time.LocalDate

private enum class Sheet { REDUCE_MOTION, LANGUAGE }

/** The system page where the user toggles the instant-blocking accessibility service. */
private fun Context.openAccessibilitySettings() {
    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

/**
 * Settings: large collapsing title, one rounded group per section, single-choice settings open a bottom
 * sheet instead of inline chips. Skeleton until the preferences flow emits.
 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onOpenGallery: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenScore: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val language = UiLanguage.current()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var profileHelp by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }
    if (profileHelp) {
        AlertDialog(
            onDismissRequest = { profileHelp = false },
            title = {
                Text(
                    stringResource(R.string.settings_profile_how),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(R.string.settings_profile_how_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                LinkButton(text = stringResource(R.string.settings_profile_got_it), onClick = { profileHelp = false })
            },
        )
    }
    val scrollBehavior = if (onBack == null) rememberLargeTopBarBehavior() else rememberPinnedTopBarBehavior()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE)) { uri ->
            uri?.let(viewModel::export)
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::import)
        }

    val exportDone = stringResource(R.string.settings_backup_export_done)
    val importDone = stringResource(R.string.settings_backup_import_done)
    val failed = stringResource(R.string.settings_backup_failed)
    LaunchedEffect(state.backupMessage) {
        val message =
            when (state.backupMessage) {
                BackupMessage.ExportDone -> exportDone
                BackupMessage.ImportDone -> importDone
                BackupMessage.Failed -> failed
                null -> return@LaunchedEffect
            }
        snackbar.showSnackbar(message)
        viewModel.clearBackupMessage()
    }

    Scaffold(
        topBar = {
            if (onBack == null) {
                RikavonLargeTopBar(title = stringResource(R.string.settings_title), scrollBehavior = scrollBehavior)
            } else {
                RikavonTopBar(
                    title = stringResource(R.string.settings_title),
                    onBack = onBack,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val prefs = state.settings
        if (prefs == null) {
            SkeletonList(modifier = Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + Spacing.xl,
                ),
        ) {
            item {
                SectionLabel(stringResource(R.string.settings_section_pet))
                GroupCard {
                    SettingNavRow(
                        title = stringResource(R.string.settings_pet),
                        subtitle = state.skin?.name?.resolve(language),
                        onClick = onOpenGallery,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_sounds),
                        subtitle = stringResource(R.string.settings_sounds_hint),
                        checked = prefs.soundsEnabled,
                        onCheckedChange = viewModel::setSounds,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_voice),
                        subtitle = stringResource(R.string.settings_voice_hint),
                        checked = prefs.voiceEnabled,
                        onCheckedChange = viewModel::setVoice,
                        enabled = prefs.soundsEnabled,
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_tracking),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_tracking),
                        subtitle = stringResource(R.string.settings_tracking_hint),
                        checked = prefs.trackingEnabled,
                        onCheckedChange = viewModel::setTracking,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_strict),
                        subtitle = stringResource(R.string.settings_strict_hint),
                        checked = prefs.strictMode,
                        onCheckedChange = viewModel::setStrictMode,
                    )
                    val permissionsOk = state.permissions?.coreGranted == true
                    val permissionsRes =
                        if (permissionsOk) R.string.settings_permissions_ok else R.string.settings_permissions_missing
                    SettingNavRow(
                        title = stringResource(R.string.settings_permissions),
                        subtitle = stringResource(permissionsRes),
                        onClick = onOpenOnboarding,
                    )
                    val context = LocalContext.current
                    val instantOn = state.permissions?.accessibility == true
                    SettingNavRow(
                        title = stringResource(R.string.settings_instant),
                        subtitle =
                            stringResource(
                                if (instantOn) R.string.settings_instant_on else R.string.settings_instant_off,
                            ),
                        onClick = { context.openAccessibilitySettings() },
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_battery),
                        subtitle = stringResource(R.string.settings_battery_hint),
                        onClick = onOpenBattery,
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_score),
                        subtitle = stringResource(R.string.settings_score_hint),
                        onClick = onOpenScore,
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_profile),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    val profile = state.profile
                    val provisioning =
                        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                            viewModel.refreshPermissions()
                        }
                    when {
                        profile.insideProfile ->
                            ListRow(
                                title = stringResource(R.string.settings_profile_inside),
                                subtitle = stringResource(R.string.settings_profile_inside_hint),
                            )
                        profile.hasProfile ->
                            SettingNavRow(
                                title = stringResource(R.string.settings_profile_open),
                                subtitle = stringResource(R.string.settings_profile_open_hint),
                                onClick = viewModel::openProfile,
                            )
                        profile.canCreate ->
                            SettingNavRow(
                                title = stringResource(R.string.settings_profile_create),
                                subtitle = stringResource(R.string.settings_profile_create_hint),
                                onClick = { provisioning.launch(viewModel.provisioningIntent()) },
                            )
                        else ->
                            ListRow(
                                title = stringResource(R.string.settings_profile_unsupported),
                                subtitle = stringResource(R.string.settings_profile_unsupported_hint),
                                enabled = false,
                            )
                    }
                    SettingNavRow(
                        title = stringResource(R.string.settings_profile_how),
                        subtitle = stringResource(R.string.settings_profile_how_hint),
                        onClick = { profileHelp = true },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_notifications),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_summary),
                        subtitle = stringResource(R.string.settings_summary_hint),
                        checked = prefs.dailySummaryEnabled,
                        onCheckedChange = { viewModel.setDailySummary(it, prefs.dailySummaryHour) },
                    )
                    if (prefs.dailySummaryEnabled) {
                        val hourLabel = stringResource(R.string.settings_summary_hour, prefs.dailySummaryHour)
                        Text(
                            text = hourLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalExtraColors.current.onSurfaceMuted,
                            modifier = Modifier.padding(horizontal = ScreenPadding),
                        )
                        Slider(
                            value = prefs.dailySummaryHour.toFloat(),
                            onValueChange = { viewModel.setDailySummary(true, it.toInt()) },
                            valueRange = SUMMARY_MIN_HOUR.toFloat()..SUMMARY_MAX_HOUR.toFloat(),
                            steps = SUMMARY_MAX_HOUR - SUMMARY_MIN_HOUR - 1,
                            colors = rikavonSliderColors(),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ScreenPadding)
                                    .heightIn(min = Sizes.touch)
                                    .semantics { contentDescription = hourLabel },
                        )
                    }
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_appearance),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_hint),
                        checked = prefs.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_reduce_motion),
                        subtitle = stringResource(prefs.reduceMotion.label()),
                        onClick = { sheet = Sheet.REDUCE_MOTION },
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_language),
                        subtitle = stringResource(prefs.language.label()),
                        onClick = { sheet = Sheet.LANGUAGE },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_data),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    SettingNavRow(
                        title = stringResource(R.string.settings_backup_export),
                        subtitle = stringResource(R.string.settings_backup_export_hint),
                        onClick = {
                            exportLauncher.launch(
                                "${BackupRepository.FILE_NAME_PREFIX}${LocalDate.now()}.json",
                            )
                        },
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_backup_import),
                        subtitle = stringResource(R.string.settings_backup_import_hint),
                        onClick = { importLauncher.launch(arrayOf(BackupRepository.MIME_TYPE, MIME_ANY)) },
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_privacy),
                        subtitle = stringResource(R.string.settings_privacy_hint),
                        onClick = onOpenPrivacy,
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_section_premium),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    val premium = state.tier == Tier.PREMIUM
                    val premiumRes = if (premium) R.string.settings_premium_active else R.string.settings_premium_free
                    SettingNavRow(
                        title = stringResource(R.string.settings_premium),
                        subtitle = stringResource(premiumRes),
                        onClick = onOpenPremium,
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.settings_version, state.versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalExtraColors.current.onSurfaceFaint,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
        }

        when (sheet) {
            Sheet.REDUCE_MOTION ->
                ChoiceSheet(
                    title = stringResource(R.string.settings_reduce_motion_sheet),
                    options =
                        ReduceMotionMode.entries.map {
                            ChoiceOption(it, stringResource(it.label()), stringResource(it.hint()))
                        },
                    selected = prefs.reduceMotion,
                    onSelect = viewModel::setReduceMotion,
                    onDismiss = { sheet = null },
                )
            Sheet.LANGUAGE ->
                ChoiceSheet(
                    title = stringResource(R.string.settings_language_sheet),
                    options = AppLanguage.entries.map { ChoiceOption(it, stringResource(it.label())) },
                    selected = prefs.language,
                    onSelect = viewModel::setLanguage,
                    onDismiss = { sheet = null },
                )
            null -> Unit
        }
    }

    state.strictCountdown?.let { seconds ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCountdown,
            title = {
                Text(
                    stringResource(R.string.settings_strict_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(R.string.settings_strict_dialog_body, seconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {},
            dismissButton = {
                LinkButton(
                    text = stringResource(R.string.settings_strict_dialog_cancel),
                    onClick = viewModel::cancelCountdown,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

private fun ReduceMotionMode.label(): Int =
    when (this) {
        ReduceMotionMode.SYSTEM -> R.string.settings_reduce_motion_system
        ReduceMotionMode.ON -> R.string.settings_reduce_motion_on
        ReduceMotionMode.OFF -> R.string.settings_reduce_motion_off
    }

private fun ReduceMotionMode.hint(): Int =
    when (this) {
        ReduceMotionMode.SYSTEM -> R.string.settings_reduce_motion_system_hint
        ReduceMotionMode.ON -> R.string.settings_reduce_motion_on_hint
        ReduceMotionMode.OFF -> R.string.settings_reduce_motion_off_hint
    }

private fun AppLanguage.label(): Int =
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.HEBREW -> R.string.settings_language_he
        AppLanguage.ENGLISH -> R.string.settings_language_en
    }

private const val SUMMARY_MIN_HOUR = 17
private const val SUMMARY_MAX_HOUR = 23
private const val MIME_ANY = "*/*"
