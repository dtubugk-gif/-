package il.rikavon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import il.rikavon.feature.mascot.sound.NeuralSpeech
import il.rikavon.feature.mascot.sound.SpeechProvider
import il.rikavon.feature.mascot.ui.UiLanguage

/**
 * Where the user pastes (or removes) their own API key for a cloud feature, with what that means spelled out.
 * The realistic voice takes an Azure Speech key (with its region) or an OpenAI key, can be tried right here,
 * out loud, and a refusal is shown in the service's own words.
 */
@Composable
fun CloudKeyDialog(
    kind: CloudKey,
    configured: Boolean,
    onDismiss: () -> Unit,
    viewModel: CloudKeyViewModel = hiltViewModel(),
) {
    var key by rememberSaveable(kind) { mutableStateOf("") }
    var region by rememberSaveable(kind) { mutableStateOf("") }
    val test by viewModel.test.collectAsStateWithLifecycle()
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val language = UiLanguage.current()
    val testLine = stringResource(R.string.settings_voice_test_line)
    LaunchedEffect(setup?.region) { if (region.isEmpty()) region = setup?.region.orEmpty() }
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
    val typedProvider = key.takeIf { it.isNotBlank() }?.let { NeuralSpeech.provider(it) }
    val regionWanted = kind == CloudKey.VOICE && (typedProvider ?: setup?.provider) == SpeechProvider.AZURE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    placeholder = { Text(stringResource(hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                KeyWarning.of(kind, key)?.let { warning ->
                    Text(
                        text =
                            stringResource(
                                when (warning) {
                                    KeyWarning.ANTHROPIC_FOR_VOICE -> R.string.settings_key_anthropic_for_voice
                                    KeyWarning.NOT_ANTHROPIC_FOR_BRAIN -> R.string.settings_key_not_anthropic_for_brain
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (regionWanted) {
                    OutlinedTextField(
                        value = region,
                        onValueChange = { region = it.trim() },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.settings_voice_region_hint)) },
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrectEnabled = false,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val current = setup
                if (configured && kind == CloudKey.VOICE && current != null) {
                    Text(
                        text =
                            stringResource(
                                when (current.provider) {
                                    SpeechProvider.AZURE -> R.string.settings_voice_provider_azure
                                    SpeechProvider.OPENAI -> R.string.settings_voice_provider_openai
                                },
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val automatic = stringResource(R.string.settings_voice_auto)
                    Box {
                        TextButton(onClick = { picking = true }) {
                            Text(stringResource(R.string.settings_voice_pick, choice ?: automatic))
                        }
                        DropdownMenu(expanded = picking, onDismissRequest = { picking = false }) {
                            DropdownMenuItem(
                                text = { Text(automatic) },
                                onClick = {
                                    viewModel.chooseVoice(null)
                                    picking = false
                                },
                            )
                            current.voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice) },
                                    onClick = {
                                        viewModel.chooseVoice(voice)
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
                                    VoiceTest.WrongProvider ->
                                        stringResource(R.string.settings_voice_test_wrong_provider)
                                    VoiceTest.NoRegion -> stringResource(R.string.settings_voice_test_no_region)
                                    is VoiceTest.Failed ->
                                        stringResource(R.string.settings_voice_test_failed, outcome.detail)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (outcome is VoiceTest.Ok || outcome == VoiceTest.Running) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
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
                    viewModel.save(kind, key, region)
                    onDismiss()
                },
                enabled = key.isNotBlank() && (!regionWanted || region.isNotBlank()),
            ) { Text(stringResource(R.string.settings_ai_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ai_cancel)) } },
    )
}
