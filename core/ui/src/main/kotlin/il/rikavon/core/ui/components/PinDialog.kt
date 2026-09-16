package il.rikavon.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import il.rikavon.core.ui.theme.Spacing

/**
 * One PIN entry: digits only, hidden, with a message and an optional error under it. The same dialog
 * asks for a PIN (to loosen a limit) and sets one (twice), the caller only changes the words.
 */
@Composable
fun PinDialog(
    title: String,
    body: String,
    confirmText: String,
    cancelText: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    maxLength: Int = PIN_MAX_LENGTH,
) {
    var pin by rememberSaveable(title) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= maxLength &&
                            value.all { it.isDigit() }
                        ) {
                            pin = value
                        }
                    },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = error?.let { { Text(it) } },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            LinkButton(
                text = confirmText,
                onClick = { onSubmit(pin) },
                enabled =
                    pin.length >= PIN_MIN_LENGTH,
            )
        },
        dismissButton = { LinkButton(text = cancelText, onClick = onDismiss) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

const val PIN_MIN_LENGTH = 4
const val PIN_MAX_LENGTH = 8
