package com.routines.appclose.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.routines.appclose.data.ActionType
import com.routines.appclose.data.RuleAction
import com.routines.appclose.util.InstalledApp
import com.routines.appclose.util.InstalledAppsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** שם קריא לסוג פעולה. */
fun ActionType.hebrewName(): String = when (this) {
    ActionType.SOUND_MODE -> "מצב צליל"
    ActionType.MEDIA_VOLUME -> "עוצמת מדיה"
    ActionType.DND -> "נא לא להפריע"
    ActionType.OPEN_APP -> "פתיחת אפליקציה"
    ActionType.NOTIFY -> "תזכורת"
    ActionType.BRIGHTNESS -> "בהירות מסך"
    ActionType.WIFI_PANEL -> "פתיחת פאנל Wi-Fi"
}

/** תיאור קצר של פעולה שמורה, לרשימת הכללים. */
fun RuleAction.describe(context: Context): String = when (type) {
    ActionType.SOUND_MODE -> when (intValue) {
        0 -> "מעבר למצב שקט"
        1 -> "מעבר לרטט"
        else -> "מעבר לצלצול"
    }
    ActionType.MEDIA_VOLUME -> "עוצמת מדיה ${intValue ?: 0}%"
    ActionType.DND -> if (intValue == 1) "הפעלת נא לא להפריע" else "כיבוי נא לא להפריע"
    ActionType.OPEN_APP -> "פתיחת ${appLabel(context, stringValue)}"
    ActionType.NOTIFY -> "תזכורת: ${stringValue.orEmpty()}"
    ActionType.BRIGHTNESS -> "בהירות ${intValue ?: 0}%"
    ActionType.WIFI_PANEL -> "פתיחת פאנל Wi-Fi"
}

private fun appLabel(context: Context, pkg: String?): String {
    if (pkg.isNullOrBlank()) return "?"
    return try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }
}

@Composable
fun AppIconImage(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable.toBitmapSafe() }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}

private fun Drawable.toBitmapSafe(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}

/** דיאלוג בחירת אפליקציה מתוך המותקנות במכשיר. */
@Composable
fun AppPickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledAppsProvider.launchableApps(context) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ביטול") }
        },
        text = {
            val list = apps
            if (list == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        ) {
                            AppIconImage(app.icon, Modifier.size(36.dp))
                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
