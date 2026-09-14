package com.duplicatecleaner.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.duplicatecleaner.app.R
import com.duplicatecleaner.app.model.DuplicateGroup
import com.duplicatecleaner.app.model.FileEntry
import com.duplicatecleaner.app.model.MediaKind
import com.duplicatecleaner.app.util.displayFolder
import com.duplicatecleaner.app.util.formatBytes
import com.duplicatecleaner.app.util.formatDate
import java.io.File

fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.IMAGE -> Icons.Outlined.Image
    MediaKind.VIDEO -> Icons.Outlined.Videocam
    MediaKind.AUDIO -> Icons.Outlined.AudioFile
    MediaKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** What Coil should load for a visual preview, or null when only an icon makes sense. */
fun previewModel(entry: FileEntry): Any? = when (entry.kind) {
    MediaKind.IMAGE, MediaKind.VIDEO ->
        entry.uri?.let(Uri::parse) ?: entry.path.takeIf { it.isNotEmpty() }?.let(::File)
    else -> null
}

@Composable
fun Thumbnail(entry: FileEntry, modifier: Modifier = Modifier) {
    val model = previewModel(entry)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = stringResource(R.string.cd_thumbnail),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = kindIcon(entry.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.kind == MediaKind.VIDEO) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = stringResource(R.string.cd_video),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun FileRow(
    entry: FileEntry,
    isKeeper: Boolean,
    isSelected: Boolean,
    onPreview: () -> Unit,
    onKeep: () -> Unit,
    onToggleSelected: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPreview)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(entry, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isKeeper) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = displayFolder(entry.path),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    text = "${formatBytes(entry.size)} · ${formatDate(entry.modifiedMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isKeeper) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.kept),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        IconButton(onClick = onKeep) {
            Icon(
                imageVector = if (isKeeper) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.keep),
                tint = if (isKeeper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
fun GroupCard(
    group: DuplicateGroup,
    keeperKey: String,
    selected: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSetKeeper: (String) -> Unit,
    onToggleSelected: (String) -> Unit,
    onDelete: (FileEntry) -> Unit,
    onPreview: (FileEntry) -> Unit,
) {
    val first = group.files.first()
    Card(Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(first, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = first.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.copies_count, group.count) + " · " +
                            stringResource(R.string.each_size, formatBytes(group.fileSize)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.wasted, formatBytes(group.wastedBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                HorizontalDivider()
                group.files.forEachIndexed { index, entry ->
                    FileRow(
                        entry = entry,
                        isKeeper = entry.key == keeperKey,
                        isSelected = entry.key in selected,
                        onPreview = { onPreview(entry) },
                        onKeep = { onSetKeeper(entry.key) },
                        onToggleSelected = { onToggleSelected(entry.key) },
                        onDelete = { onDelete(entry) },
                    )
                    if (index < group.files.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
