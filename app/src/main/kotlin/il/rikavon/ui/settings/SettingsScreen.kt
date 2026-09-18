package il.rikavon.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import il.rikavon.core.data.model.Settings
import il.rikavon.core.data.repo.BackupRepository
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.ui.components.ChoiceOption
import il.rikavon.core.ui.components.ChoiceSheet
import il.rikavon.core.ui.components.GroupCard
import il.rikavon.core.ui.components.LinkButton
import il.rikavon.core.ui.components.ListRow
import il.rikavon.core.ui.components.PinDialog
import il.rikavon.core.ui.components.RikavonLargeTopBar
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SectionLabel
import il.rikavon.core.ui.components.SegmentPills
import il.rikavon.core.ui.components.SettingNavRow
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.SkeletonList
import il.rikavon.core.ui.components.rememberLargeTopBarBehavior
import il.rikavon.core.ui.components.rememberPinnedTopBarBehavior
import il.rikavon.core.ui.components.rikavonSliderColors
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.blocker.contact.CallDiagnosis
import il.rikavon.feature.blocker.contact.RingPath
import il.rikavon.feature.blocker.ui.common.PinSetup
import il.rikavon.feature.mascot.sound.Speaker
import il.rikavon.feature.mascot.ui.UiLanguage
import java.time.LocalDate
import il.rikavon.feature.blocker.R as BlockerR

private enum class Sheet { REDUCE_MOTION, LANGUAGE, SYSTEM_APPS }

/** Android 14+: the system page where the user lets the pet's calls take over the screen. */
private fun Context.openFullScreenIntentSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent =
        Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
            .setData(Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

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
    onOpenSites: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenScore: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lock by viewModel.lock.state.collectAsStateWithLifecycle()
    val language = UiLanguage.current()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var profileHelp by remember { mutableStateOf(false) }
    var keyDialog by remember { mutableStateOf<CloudKey?>(null) }
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
                        onCheckedChange = { viewModel.setAudio(sounds = it) },
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_voice),
                        subtitle = voiceHint(state),
                        checked = prefs.voiceEnabled,
                        onCheckedChange = { viewModel.setAudio(voice = it) },
                        enabled = prefs.soundsEnabled,
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_ai),
                        subtitle =
                            stringResource(
                                if (state.aiConfigured) R.string.settings_ai_on else R.string.settings_ai_off,
                            ),
                        onClick = { keyDialog = CloudKey.BRAIN },
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_realistic_voice),
                        subtitle =
                            stringResource(
                                if (state.voiceConfigured) {
                                    R.string.settings_realistic_voice_on
                                } else {
                                    R.string.settings_realistic_voice_off
                                },
                            ),
                        onClick = { keyDialog = CloudKey.VOICE },
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
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_breathing),
                        subtitle = stringResource(R.string.settings_breathing_hint),
                        checked = prefs.breathingGateEnabled,
                        onCheckedChange = viewModel::setBreathingGate,
                    )
                    SettingNavRow(
                        title = stringResource(R.string.settings_pin),
                        subtitle =
                            stringResource(
                                if (prefs.locked) R.string.settings_pin_on_hint else R.string.settings_pin_off_hint,
                            ),
                        onClick = { if (prefs.locked) viewModel.lock.removePin() else viewModel.lock.startSetup() },
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
                        title = stringResource(R.string.settings_sites),
                        subtitle = stringResource(R.string.settings_sites_hint),
                        onClick = onOpenSites,
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
                    stringResource(R.string.settings_section_contact),
                    modifier = Modifier.padding(top = Spacing.md),
                )
                GroupCard {
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_pet_messages),
                        subtitle = stringResource(R.string.settings_pet_messages_hint),
                        checked = prefs.petMessagesEnabled,
                        onCheckedChange = viewModel::setPetMessages,
                    )
                    ListRow(
                        title = stringResource(R.string.settings_reminder),
                        subtitle = stringResource(R.string.settings_reminder_hint),
                    )
                    SegmentPills(
                        options = Settings.REMINDER_OPTIONS,
                        selected = prefs.reminderMinutes,
                        onSelect = viewModel::setReminder,
                        label = {
                            if (it == 0) {
                                stringResource(R.string.settings_reminder_off)
                            } else {
                                stringResource(R.string.settings_reminder_min, it)
                            }
                        },
                        modifier =
                            Modifier
                                .padding(
                                    start = ScreenPadding,
                                    end = ScreenPadding,
                                    bottom = Spacing.md,
                                ).fillMaxWidth(),
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.settings_pet_calls),
                        subtitle = stringResource(R.string.settings_pet_calls_hint),
                        checked = prefs.petCallsEnabled,
                        onCheckedChange = viewModel::setPetCalls,
                    )
                    val diagnosis = state.callDiagnosis
                    if (prefs.petCallsEnabled && diagnosis != null) {
                        SettingNavRow(
                            title = stringResource(R.string.settings_call_test),
                            subtitle = callDiagnosisText(diagnosis),
                            onClick = viewModel::testCall,
                        )
                    }
                    val fullScreenOk = state.permissions?.fullScreenIntent != false
                    if (prefs.petCallsEnabled && !fullScreenOk) {
                        val context = LocalContext.current
                        SettingNavRow(
                            title = stringResource(R.string.settings_full_screen),
                            subtitle = stringResource(R.string.settings_full_screen_hint),
                            onClick = { context.openFullScreenIntentSettings() },
                        )
                    }
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
                        profile.insideProfile -> {
                            ListRow(
                                title = stringResource(R.string.settings_profile_inside),
                                subtitle = stringResource(R.string.settings_profile_inside_hint),
                            )
                            val hasCandidates = state.systemApps.isNotEmpty()
                            val systemHint =
                                if (hasCandidates) {
                                    R.string.settings_profile_system_hint
                                } else {
                                    R.string.settings_profile_system_none
                                }
                            SettingNavRow(
                                title = stringResource(R.string.settings_profile_system),
                                subtitle = stringResource(systemHint),
                                onClick = { if (hasCandidates) sheet = Sheet.SYSTEM_APPS },
                            )
                        }
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
            Sheet.SYSTEM_APPS ->
                ChoiceSheet(
                    title = stringResource(R.string.settings_profile_system_sheet),
                    options = state.systemApps.map { ChoiceOption(it.packageName, it.label, it.packageName) },
                    selected = "",
                    onSelect = { packageName ->
                        viewModel.enableSystemApp(packageName)
                        sheet = null
                    },
                    onDismiss = { sheet = null },
                )
            null -> Unit
        }
    }

    if (lock.asking || lock.setup != null) {
        val (titleRes, bodyRes) =
            when (lock.setup) {
                PinSetup.CHOOSE -> BlockerR.string.pin_set_title to BlockerR.string.pin_set_body
                PinSetup.CONFIRM -> BlockerR.string.pin_confirm_title to BlockerR.string.pin_confirm_body
                null -> BlockerR.string.pin_enter_title to BlockerR.string.pin_enter_body
            }
        val errorRes =
            when (lock.setup) {
                PinSetup.CHOOSE -> BlockerR.string.pin_set_body
                PinSetup.CONFIRM -> BlockerR.string.pin_mismatch
                null -> BlockerR.string.pin_wrong
            }
        PinDialog(
            title = stringResource(titleRes),
            body = stringResource(bodyRes),
            confirmText = stringResource(BlockerR.string.pin_ok),
            cancelText = stringResource(BlockerR.string.pin_cancel),
            error = if (lock.wrong) stringResource(errorRes) else null,
            onSubmit = viewModel.lock::submit,
            onDismiss = viewModel.lock::cancel,
        )
    }
    keyDialog?.let { kind ->
        CloudKeyDialog(
            kind = kind,
            configured = if (kind == CloudKey.BRAIN) state.aiConfigured else state.voiceConfigured,
            onDismiss = { keyDialog = null },
        )
    }
    state.strictCountdown?.let { seconds ->
        AlertDialog(
            onDismissRequest = viewModel.countdown::cancel,
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
                    onClick = viewModel.countdown::cancel,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
        )
    }
}

/**
 * Under the voice switch: which engine really spoke the pet's last line. With a cloud key that is the honest
 * answer to "is the realistic voice on", since the device engine steps in silently whenever the cloud refuses.
 */
@Composable
private fun voiceHint(state: SettingsUiState): String =
    when (val speaker = state.speaker) {
        Speaker.Cloud -> stringResource(R.string.settings_voice_hint_cloud)
        is Speaker.DeviceAfterRefusal -> stringResource(R.string.settings_voice_hint_refused, speaker.reason)
        Speaker.Device, null ->
            stringResource(
                if (state.voiceConfigured) R.string.settings_voice_hint_pending else R.string.settings_voice_hint,
            )
    }

/** One sentence on why the pet is not calling, or what its call machinery last did when it can. */
@Composable
private fun callDiagnosisText(diagnosis: CallDiagnosis): String =
    when (diagnosis) {
        CallDiagnosis.TrackingOff -> stringResource(R.string.settings_call_tracking_off)
        CallDiagnosis.NoUsageAccess -> stringResource(R.string.settings_call_no_usage)
        CallDiagnosis.CallsOff -> stringResource(R.string.settings_call_off)
        is CallDiagnosis.ServiceAsleep ->
            stringResource(
                R.string.settings_call_service_asleep,
                if (diagnosis.lastPollAt == 0L) {
                    stringResource(R.string.settings_call_never)
                } else {
                    ago(diagnosis.lastPollAt)
                },
            )
        CallDiagnosis.NothingToCallAbout -> stringResource(R.string.settings_call_nothing)
        is CallDiagnosis.NoOverlay ->
            stringResource(
                if (diagnosis.notifications) R.string.settings_call_no_overlay else R.string.settings_call_no_way,
            )
        is CallDiagnosis.Ready -> {
            val ring = diagnosis.health.lastRing
            val open = diagnosis.health.lastOpen
            val lastRing =
                if (ring == null) {
                    stringResource(R.string.settings_call_none_yet)
                } else {
                    stringResource(
                        R.string.settings_call_last_ring,
                        diagnosis.ringLabel ?: ring.packageName,
                        ago(ring.atMillis),
                        stringResource(
                            when (ring.path) {
                                RingPath.OVERLAY -> R.string.settings_call_path_screen
                                RingPath.NOTIFICATION -> R.string.settings_call_path_notification
                                RingPath.NOTHING -> R.string.settings_call_path_nothing
                            },
                        ),
                    )
                }
            val lastOpen =
                open?.let {
                    stringResource(
                        R.string.settings_call_last_open,
                        diagnosis.openLabel ?: it.packageName,
                        ago(it.atMillis),
                    )
                }
            listOfNotNull(stringResource(R.string.settings_call_ready), lastRing, lastOpen).joinToString(" ")
        }
    }

/** "just now" or "N min ago", for the diagnosis. */
@Composable
private fun ago(atMillis: Long): String {
    val minutes = ((System.currentTimeMillis() - atMillis) / MILLIS_PER_MINUTE).toInt()
    return if (minutes < 1) {
        stringResource(R.string.settings_call_just_now)
    } else {
        stringResource(R.string.settings_call_ago, minutes)
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
private const val MILLIS_PER_MINUTE = 60_000L
