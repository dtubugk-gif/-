package com.routines.appclose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routines.appclose.service.ServiceStatus
import com.routines.appclose.util.PermissionsHelper
import java.text.DateFormat
import java.util.Date

/**
 * מסך אבחון: מראה בזמן אמת אם שירות הזיהוי מחובר, ולוג של יציאות שזוהו
 * (וגם אם כלל תאם ורץ). זהו הכלי להבין למה "השגרות לא רצות".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val connected by ServiceStatus.connected.collectAsState()
    val detections by ServiceStatus.recentDetections.collectAsState()
    val formatter = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    // ההרשאה נבדקת מהמערכת ולא רק מהדגל הפנימי (למקרה שהשירות עוד לא התחבר).
    val accessibilityEnabled = PermissionsHelper.isAccessibilityEnabled(context)
    val batteryOk = PermissionsHelper.isIgnoringBatteryOptimizations(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("אבחון") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatusRow(
                    label = "שירות הזיהוי",
                    ok = connected,
                    okText = "מחובר ופעיל",
                    badText = if (accessibilityEnabled)
                        "מופעל בהגדרות אך לא מחובר כרגע — כנראה הוקפא. אשר ביטול אופטימיזציית סוללה, ואז כבה והדלק מחדש את השירות."
                    else
                        "כבוי. הפעל אותו במסך ההרשאות.",
                )
            }
            item {
                StatusRow(
                    label = "אופטימיזציית סוללה",
                    ok = batteryOk,
                    okText = "מבוטלת — טוב",
                    badText = "פעילה. בסמסונג זו הסיבה הנפוצה לכך שהשירות מוקפא. בטל אותה במסך ההרשאות.",
                )
            }

            item {
                Text(
                    "יציאות שזוהו לאחרונה",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (detections.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "עדיין לא זוהתה יציאה. פתח אפליקציה שהגדרת לה שגרה, השאר בה כ־2 שניות, ואז צא ממנה למסך הבית. אם כלום לא מופיע כאן — השירות מוקפא (בדוק סוללה) או כבוי.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                items(detections) { d ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (d.ruleRan)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "יציאה מ־${d.label}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                d.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                if (d.ruleRan) "✓ שגרה הותאמה והורצה · ${formatter.format(Date(d.timestamp))}"
                                else "לא נמצאה שגרה פעילה לאפליקציה הזו · ${formatter.format(Date(d.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, okText: String, badText: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = if (ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(14.dp),
            ) {}
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (ok) okText else badText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}
