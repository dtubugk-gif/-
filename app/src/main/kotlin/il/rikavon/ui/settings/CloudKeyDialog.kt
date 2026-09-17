package il.rikavon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.data.repo.CloudKey
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.mascot.ui.UiLanguage

/**
 * Where the user pastes (or removes) their own API key for a cloud feature, with what that means spelled out.
 * The realistic voice can be tried right here, out loud, and a refusal is shown in the service's own words.
 */
@Composable
fun CloudKeyDialog(
    kind: CloudKey,
    configured: Boolean,
    onDismiss: () -> Unit,
    viewModel: CloudKeyViewModel = hiltViewModel(),
) {
    var key by rememberSaveable(kind) { mutableStateOf("") }
    val test by viewModel.test.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val language = UiLanguage.current()
    val testLine = stringResource(R.string.settings_voice_test_line)
    val title =
        when (kind) {
            CloudKey.BRAIN -> R.string.settings_ai_dialog_title
            CloudKey.VOICE -> R.string.settings_voice_dialog_title
        }
    val body =
        when (kind) {
            CloudKey.BRAIN -> R.string.settings_ai_dialog_body
            CloudKey.VOICE -> R.string.settings_voice_dialog_body
        }
    val hint =
        when (kind) {
            CloudKey.BRAIN -> R.string.settings_ai_key_hint
            CloudKey.VOICE -> R.string.settings_voice_key_hint
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    placeholder = { Text(stringResource(hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (configured && kind == CloudKey.VOICE) {
                    val automatic = stringResource(R.string.settings_voice_auto)
                    val chosen = voices.firstOrNull { it.id == choice }?.name ?: automatic
                    Box {
                        TextButton(onClick = { picking = true }, enabled = voices.isNotEmpty()) {
                            Text(stringResource(R.string.settings_voice_pick, chosen))
                        }
                        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            DropdownMenuItem(
                                text = { Text(automatic) },
                                onClick = {
                                    viewModel.chooseVoice(null)
                                    picking = false
                                },
                            )
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(voice.name)
                                            if (voice.description.isNotEmpty()) {
                                                Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.chooseVoice(voice.id)
                                        picking = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { viewModel.testVoice(testLine, language) },
                        enabled = test != VoiceTest.Running,
                    ) { Text(stringResource(R.string.settings_voice_test)) }
                    test?.let { outcome ->
                        Text(
                            text =
                                when (outcome) {
                                    VoiceTest.Running -> stringResource(R.string.settings_voice_test_running)
                                    VoiceTest.Ok -> stringResource(R.string.settings_voice_test_ok)
                                    VoiceTest.NoVoice -> stringResource(R.string.settings_voice_test_none)
                                    is VoiceTest.Failed ->
                                        stringResource(R.string.settings_voice_test_failed, outcome.detail)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (outcome is VoiceTest.Failed || outcome == VoiceTest.NoVoice) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
                if (configured) {
                    TextButton(
                        onClick = {
                            viewModel.remove(kind)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.settings_ai_remove)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.save(kind, key)
                    onDismiss()
                },
                enabled = key.isNotBlank(),
            ) { Text(stringResource(R.string.settings_ai_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ai_cancel)) } },
    )
}
