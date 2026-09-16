package il.rikavon.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import il.rikavon.R
import il.rikavon.core.ui.theme.Spacing

/** Where the user pastes (or removes) their own API key for the AI brain, with what that means spelled out. */
@Composable
fun AiKeyDialog(configured: Boolean, onSave: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_ai_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.settings_ai_dialog_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_ai_key_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (configured) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_ai_remove)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(key) }, enabled = key.isNotBlank()) {
                Text(stringResource(R.string.settings_ai_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ai_cancel)) } },
    )
}
