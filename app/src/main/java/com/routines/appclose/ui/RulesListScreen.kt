package com.routines.appclose.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.routines.appclose.engine.ActionExecutor
import com.routines.appclose.service.ServiceStatus
import com.routines.appclose.util.PermissionsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesListScreen(
    viewModel: RulesViewModel,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val rules by viewModel.rules.collectAsState()
    val serviceConnected by ServiceStatus.connected.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // רענון בדיקות ההרשאות בכל חזרה למסך (אחרי ביקור בהגדרות המערכת).
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val accessibilityOn = remember(refreshTick) { PermissionsHelper.isAccessibilityEnabled(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("שגרות סגירה") },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.MonitorHeart, contentDescription = "אבחון")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "היסטוריה")
                    }
                    IconButton(onClick = onOpenPermissions) {
                        Icon(Icons.Filled.Security, contentDescription = "הרשאות")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddRule,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("שגרה חדשה") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ServiceBanner(
                    accessibilityOn = accessibilityOn,
                    serviceConnected = serviceConnected,
                    onOpenPermissions = onOpenPermissions,
                    onOpenDiagnostics = onOpenDiagnostics,
                )
            }

            if (rules.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("אין עדיין שגרות", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "צור שגרה ראשונה: בחר אפליקציה, וכשתצא ממנה — הפעולות שתגדיר יופעלו אוטומטית.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            items(rules, key = { it.rule.id }) { ruleWithActions ->
                val rule = ruleWithActions.rule
                val missing = remember(ruleWithActions, refreshTick) {
                    PermissionsHelper.missingForActions(context, ruleWithActions.actions)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onEditRule(rule.id) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "כשיוצאים מ־${rule.watchedAppLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            // הרצת ניסיון: מפעיל את הפעולות עכשיו, בלי לחכות ליציאה —
                            // כך בודקים אם הבעיה בזיהוי או בפעולות עצמן.
                            IconButton(onClick = {
                                Toast.makeText(context, "מריץ את פעולות השגרה…", Toast.LENGTH_SHORT).show()
                                val actions = ruleWithActions.actions
                                val appContext = context.applicationContext
                                scope.launch(Dispatchers.Default) {
                                    ActionExecutor(appContext).execute(actions)
                                }
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "הרצת ניסיון")
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { viewModel.setEnabled(rule, it) },
                            )
                            IconButton(onClick = { viewModel.deleteRule(rule) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "מחיקה")
                            }
                        }
                        ruleWithActions.actions.forEach { action ->
                            Text(
                                "• ${action.describe(context)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (missing.isNotEmpty()) {
                            Text(
                                "⚠ חסרות הרשאות: ${missing.joinToString(", ")} — לחץ על אייקון המגן לאישור",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** באנר סטטוס חי: פעיל (ירוק) / מוקפא (כתום) / כבוי (אדום). */
@Composable
private fun ServiceBanner(
    accessibilityOn: Boolean,
    serviceConnected: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    when {
        !accessibilityOn -> BannerCard(
            title = "שירות הזיהוי כבוי",
            body = "בלי שירות הנגישות האפליקציה לא יכולה לדעת מתי יצאת מאפליקציה. לחץ כאן להפעלה.",
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onOpenPermissions,
        )
        !serviceConnected -> BannerCard(
            title = "השירות מסומן פעיל — אבל לא מחובר",
            body = "כנראה שהמערכת הקפיאה אותו (נפוץ בסמסונג). בטל אופטימיזציית סוללה במסך ההרשאות, ואז כבה והדלק מחדש את השירות בהגדרות הנגישות.",
            container = MaterialTheme.colorScheme.tertiaryContainer,
            content = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onOpenPermissions,
        )
        else -> BannerCard(
            title = "✓ שירות הזיהוי פעיל ומחובר",
            body = "לחץ כאן למסך האבחון כדי לראות אילו יציאות מזוהות.",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onOpenDiagnostics,
        )
    }
}

@Composable
private fun BannerCard(
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = content)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = content)
        }
    }
}
