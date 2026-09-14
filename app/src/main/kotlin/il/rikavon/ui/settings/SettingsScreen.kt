package il.rikavon.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.data.domain.Tier
import il.rikavon.core.data.model.AppLanguage
import il.rikavon.core.data.model.ReduceMotionMode
import il.rikavon.core.data.repo.BackupRepository
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.ScreenTitle
import il.rikavon.core.ui.components.SectionHeader
import il.rikavon.core.ui.components.SettingNavRow
import il.rikavon.core.ui.components.SettingSwitchRow
import il.rikavon.core.ui.components.TouchTarget
import il.rikavon.feature.mascot.ui.UiLanguage
import java.time.LocalDate

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
    val prefs = state.settings ?: return
    val language = UiLanguage.current()
    val snackbar = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

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
            if (onBack !=
                null
            ) {
                RikavonTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = ScreenPadding),
        ) {
            if (onBack ==
                null
            ) {
                ScreenTitle(
                    title = stringResource(R.string.settings_title),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            SectionHeader(stringResource(R.string.settings_section_pet))
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

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_tracking))
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
            val perms = state.permissions
            SettingNavRow(
                title = stringResource(R.string.settings_permissions),
                subtitle =
                    stringResource(
                        if (perms?.coreGranted ==
                            true
                        ) {
                            R.string.settings_permissions_ok
                        } else {
                            R.string.settings_permissions_missing
                        },
                    ),
                onClick = onOpenOnboarding,
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

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_notifications))
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
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
                Slider(
                    value = prefs.dailySummaryHour.toFloat(),
                    onValueChange = { viewModel.setDailySummary(true, it.toInt()) },
                    valueRange = SUMMARY_MIN_HOUR.toFloat()..SUMMARY_MAX_HOUR.toFloat(),
                    steps = SUMMARY_MAX_HOUR - SUMMARY_MIN_HOUR - 1,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding)
                            .heightIn(min = TouchTarget)
                            .semantics { contentDescription = hourLabel },
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_appearance))
            SettingSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_hint),
                checked = prefs.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
            Text(
                text = stringResource(R.string.settings_reduce_motion),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReduceMotionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.reduceMotion == mode,
                        onClick = { viewModel.setReduceMotion(mode) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ReduceMotionMode.SYSTEM -> R.string.settings_reduce_motion_system
                                        ReduceMotionMode.ON -> R.string.settings_reduce_motion_on
                                        ReduceMotionMode.OFF -> R.string.settings_reduce_motion_off
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = TouchTarget),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = prefs.language == lang,
                        onClick = { viewModel.setLanguage(lang) },
                        label = {
                            Text(
                                stringResource(
                                    when (lang) {
                                        AppLanguage.SYSTEM -> R.string.settings_language_system
                                        AppLanguage.HEBREW -> R.string.settings_language_he
                                        AppLanguage.ENGLISH -> R.string.settings_language_en
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.heightIn(min = TouchTarget),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionHeader(stringResource(R.string.settings_section_data))
            SettingNavRow(
                title = stringResource(R.string.settings_backup_export),
                subtitle = stringResource(R.string.settings_backup_export_hint),
                onClick = { exportLauncher.launch("${BackupRepository.FILE_NAME_PREFIX}${LocalDate.now()}.json") },
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

            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_premium))
            SettingNavRow(
                title = stringResource(R.string.settings_premium),
                subtitle =
                    stringResource(
                        if (state.tier ==
                            Tier.PREMIUM
                        ) {
                            R.string.settings_premium_active
                        } else {
                            R.string.settings_premium_free
                        },
                    ),
                onClick = onOpenPremium,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_version, state.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenPadding),
            )
        }
    }

    state.strictCountdown?.let { seconds ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCountdown,
            title = { Text(stringResource(R.string.settings_strict_dialog_title)) },
            text = { Text(stringResource(R.string.settings_strict_dialog_body, seconds)) },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::cancelCountdown) {
                    Text(stringResource(R.string.settings_strict_dialog_cancel))
                }
            },
        )
    }
}

private const val SUMMARY_MIN_HOUR = 17
private const val SUMMARY_MAX_HOUR = 23
private const val MIME_ANY = "*/*"
