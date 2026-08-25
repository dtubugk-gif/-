package com.routines.appclose.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.routines.appclose.util.PermissionsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // ריענון סטטוסים בכל חזרה מהגדרות המערכת.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var notificationGranted by remember(refresh) {
        mutableStateOf(PermissionsHelper.hasNotificationPermission(context))
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("הרשאות והגדרה") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "כדי שהשגרות יעבדו, צריך לאשר את ההרשאות הבאות. חובה רק את הראשונה; השאר לפי הפעולות שבחרת.",
                style = MaterialTheme.typography.bodyMedium,
            )

            androidx.compose.runtime.key(refresh) {
                PermissionCard(
                    title = "שירות נגישות (חובה)",
                    description = "מזהה מתי יוצאים מאפליקציה. בלעדיו שום שגרה לא תפעל. במסך שייפתח: שגרות סגירה ← הפעלה.",
                    granted = PermissionsHelper.isAccessibilityEnabled(context),
                    onClick = { context.startActivity(PermissionsHelper.accessibilitySettingsIntent()) },
                )
                PermissionCard(
                    title = "ביטול אופטימיזציית סוללה (חשוב בסמסונג)",
                    description = "בלי זה סמסונג עלולה להקפיא את השירות — הוא נראה \"מופעל\" אבל השגרות לא רצות. אשר, ובנוסף: הגדרות ← סוללה ← אפליקציות במצב שינה — ודא שהאפליקציה לא שם.",
                    granted = PermissionsHelper.isIgnoringBatteryOptimizations(context),
                    onClick = {
                        try {
                            context.startActivity(PermissionsHelper.batteryOptimizationIntent(context))
                        } catch (_: Exception) {
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    },
                )
                PermissionCard(
                    title = "התראות",
                    description = "נדרש לפעולת \"תזכורת\".",
                    granted = notificationGranted,
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
                PermissionCard(
                    title = "גישת נא לא להפריע",
                    description = "נדרש לפעולות \"נא לא להפריע\" ולמעבר למצב שקט.",
                    granted = PermissionsHelper.hasDndAccess(context),
                    onClick = { context.startActivity(PermissionsHelper.dndAccessIntent()) },
                )
                PermissionCard(
                    title = "שינוי הגדרות מערכת",
                    description = "נדרש לפעולת \"בהירות מסך\".",
                    granted = PermissionsHelper.canWriteSettings(context),
                    onClick = { context.startActivity(PermissionsHelper.writeSettingsIntent(context)) },
                )
                PermissionCard(
                    title = "תצוגה מעל אפליקציות אחרות",
                    description = "נדרש לפעולת \"פתיחת אפליקציה\" — בלעדיו אנדרואיד חוסם פתיחת אפליקציות מהרקע.",
                    granted = PermissionsHelper.canDrawOverlays(context),
                    onClick = { context.startActivity(PermissionsHelper.overlayIntent(context)) },
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (!granted) {
                Button(onClick = onClick) { Text("אישור") }
            }
        }
    }
}
