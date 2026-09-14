package il.rikavon.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import il.rikavon.core.ui.R
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Radius
import il.rikavon.core.ui.theme.Sizes
import il.rikavon.core.ui.theme.Spacing

/** One option in a [ChoiceSheet]. */
data class ChoiceOption<T>(val value: T, val label: String, val description: String? = null)

/**
 * Bottom sheet for a single choice: drag handle, 28dp top radius, one row per option with a check on the
 * selected one. Replaces dialogs for non-destructive decisions.
 */
@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { DragHandle() },
    ) {
        Column(modifier = Modifier.padding(bottom = Spacing.xl)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = Spacing.sm),
            )
            options.forEach { option ->
                val active = option.value == selected
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (option.description == null) Sizes.row else Sizes.rowTwoLine)
                            .selectable(selected = active, role = Role.RadioButton) {
                                onSelect(option.value)
                                onDismiss()
                            }.padding(horizontal = ScreenPadding, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.titleMedium)
                        if (option.description != null) {
                            Text(
                                option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalExtraColors.current.onSurfaceMuted,
                            )
                        }
                    }
                    if (active) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun DragHandle() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .width(Sizes.handleWidth)
                    .height(Sizes.handleHeight)
                    .background(LocalExtraColors.current.onSurfaceFaint, CircleShape),
        )
    }
}

/** Confirmation for destructive actions only. The confirm button is last and red. */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        dismissButton = { LinkButton(text = stringResource(R.string.core_ui_cancel), onClick = onDismiss) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = Sizes.touch),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor =
                            if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(confirmText, style = MaterialTheme.typography.labelLarge)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.sheet),
    )
}
