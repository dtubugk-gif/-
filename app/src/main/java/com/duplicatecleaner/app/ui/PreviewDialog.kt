package com.duplicatecleaner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.duplicatecleaner.app.R
import com.duplicatecleaner.app.model.DuplicateGroup
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.util.displayFolder
import com.duplicatecleaner.app.util.formatBytes
import com.duplicatecleaner.app.util.formatDate

/**
 * Full-screen look at a duplicate group before anything is deleted: the content itself
 * (zoomable photo, video frame, or a file icon), then every copy with its location and
 * keep / delete controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewDialog(
    group: DuplicateGroup,
    initialKey: String,
    keeperKey: String,
    selected: Set<String>,
    onClose: () -> Unit,
    onSetKeeper: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onDeleteOne: (FileEntry) -> Unit,
    onDeleteGroupDuplicates: () -> Unit,
    onOpenExternal: (FileEntry) -> Unit,
) {
    var focusedKey by rememberSaveable(group.id) { mutableStateOf(initialKey) }
    val focused = group.files.firstOrNull { it.key == focusedKey } ?: group.files.first()
    val redundant = group.files.filter { it.key != keeperKey }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(focused.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                        }
                    },
                    actions = {
                        IconButton(onClick = { onOpenExternal(focused) }) {
                            Icon(Icons.Outlined.OpenInNew, contentDescription = stringResource(R.string.open_file))
                        }
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val model = previewModel(focused)
                    when {
                        focused.kind == MediaKind.IMAGE && model != null -> ZoomableImage(model)
                        focused.kind == MediaKind.VIDEO && model != null -> {
                            AsyncImage(
                                model = model,
                                contentDescription = stringResource(R.string.cd_thumbnail),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            IconButton(
                                onClick = { onOpenExternal(focused) },
                                modifier = Modifier.size(88.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = stringResource(R.string.cd_video),
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = kindIcon(focused.kind),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(96.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(focused.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            TextButton(onClick = { onOpenExternal(focused) }) {
                                Text(stringResource(R.string.open_file), color = Color.White)
                            }
                        }
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.preview_copies_title, group.count) + " · " +
                            formatBytes(group.fileSize),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(group.files, key = { it.key }) { entry ->
                        CopyRow(
                            entry = entry,
                            isKeeper = entry.key == keeperKey,
                            isSelected = entry.key in selected,
                            isFocused = entry.key == focused.key,
                            onFocus = { focusedKey = entry.key },
                            onKeep = { onSetKeeper(entry.key) },
                            onToggleSelected = { onToggleSelected(entry.key) },
                            onDelete = { onDeleteOne(entry) },
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                Button(
                    onClick = onDeleteGroupDuplicates,
                    enabled = redundant.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .height(50.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            R.string.delete_group_duplicates,
                            redundant.size,
                            formatBytes(redundant.sumOf { it.size }),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyRow(
    entry: FileEntry,
    isKeeper: Boolean,
    isSelected: Boolean,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onKeep: () -> Unit,
    onToggleSelected: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else Color.Transparent,
            )
            .clickable(onClick = onFocus)
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onKeep) {
            Icon(
                imageVector = if (isKeeper) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.keep),
                tint = if (isKeeper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = displayFolder(entry.path).ifEmpty { entry.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isKeeper) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(entry.name)
                    append(" · ")
                    append(formatDate(entry.modifiedMillis))
                    if (isKeeper) {
                        append(" · ")
                        append(stringResource(R.string.kept))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isKeeper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isKeeper) {
            Spacer(Modifier.width(48.dp))
        } else {
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelected() })
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete_this),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ZoomableImage(model: Any) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }
    AsyncImage(
        model = model,
        contentDescription = stringResource(R.string.cd_thumbnail),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .transformable(transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            },
    )
}
