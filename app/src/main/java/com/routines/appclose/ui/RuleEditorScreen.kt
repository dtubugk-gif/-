package com.routines.appclose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.routines.appclose.data.ActionType
import com.routines.appclose.data.Rule
import com.routines.appclose.data.RuleAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    viewModel: RulesViewModel,
    ruleId: Long,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    // rememberSaveable — כדי שסיבוב מסך או הריגת תהליך לא ימחקו קלט באמצע עריכה.
    var loaded by rememberSaveable { mutableStateOf(ruleId == 0L) }
    var name by rememberSaveable { mutableStateOf("") }
    var watchedPackage by rememberSaveable { mutableStateOf("") }
    var watchedLabel by rememberSaveable { mutableStateOf("") }
    var enabled by rememberSaveable { mutableStateOf(true) }
    val actions = rememberSaveable(
        saver = listSaver<SnapshotStateList<RuleAction>, ArrayList<Any?>>(
            save = { list -> list.map { arrayListOf<Any?>(it.type.name, it.intValue, it.stringValue) } },
            restore = { saved ->
                saved.map {
                    RuleAction(
                        ruleId = 0,
                        type = ActionType.valueOf(it[0] as String),
                        intValue = it[1] as Int?,
                        stringValue = it[2] as String?,
                    )
                }.toMutableStateList()
            },
        ),
    ) { mutableStateListOf<RuleAction>() }

    var showWatchedPicker by remember { mutableStateOf(false) }
    var showOpenAppPickerFor by remember { mutableStateOf<Int?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ruleId) {
        // נטען מה-DB רק פעם אחת — אחרי שחזור state (סיבוב מסך) לא דורסים עריכות.
        if (ruleId != 0L && !loaded) {
            viewModel.getRule(ruleId)?.let { existing ->
                name = existing.rule.name
                watchedPackage = existing.rule.watchedPackage
                watchedLabel = existing.rule.watchedAppLabel
                enabled = existing.rule.enabled
                actions.clear()
                actions.addAll(existing.actions)
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ruleId == 0L) "שגרה חדשה" else "עריכת שגרה") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                    }
                },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // האפליקציה שסגירתה מפעילה את השגרה
            Text("כשיוצאים מהאפליקציה:", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showWatchedPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (watchedLabel.isBlank()) "בחר אפליקציה…" else watchedLabel)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("שם השגרה") },
                placeholder = { Text(if (watchedLabel.isBlank()) "" else "ביציאה מ־$watchedLabel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("הפעולות שיופעלו:", style = MaterialTheme.typography.titleMedium)

            actions.forEachIndexed { index, action ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                action.type.hebrewName(),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { actions.removeAt(index) }) {
                                Icon(Icons.Filled.Close, contentDescription = "הסרת פעולה")
                            }
                        }
                        ActionEditor(
                            action = action,
                            onChange = { actions[index] = it },
                            onPickApp = { showOpenAppPickerFor = index },
                        )
                    }
                }
            }

            // Box עוגן — כדי שהתפריט ייפתח צמוד לכפתור ולא ביחס לכל המסך.
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showAddMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("הוספת פעולה")
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    ActionType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.hebrewName()) },
                            onClick = {
                                showAddMenu = false
                                actions.add(defaultActionFor(type))
                            },
                        )
                    }
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = {
                    when {
                        watchedPackage.isBlank() -> error = "צריך לבחור אפליקציה שהיציאה ממנה תפעיל את השגרה."
                        actions.isEmpty() -> error = "צריך להוסיף לפחות פעולה אחת."
                        actions.any { it.type == ActionType.OPEN_APP && it.stringValue.isNullOrBlank() } ->
                            error = "בפעולת \"פתיחת אפליקציה\" צריך לבחור איזו אפליקציה לפתוח."
                        else -> {
                            val finalName = name.ifBlank { "ביציאה מ־$watchedLabel" }
                            viewModel.saveRule(
                                Rule(
                                    id = ruleId,
                                    name = finalName,
                                    watchedPackage = watchedPackage,
                                    watchedAppLabel = watchedLabel,
                                    enabled = enabled,
                                ),
                                actions.toList(),
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("שמירה")
            }
        }
    }

    if (showWatchedPicker) {
        AppPickerDialog(
            title = "בחר אפליקציה למעקב",
            onDismiss = { showWatchedPicker = false },
            onPick = { app ->
                watchedPackage = app.packageName
                watchedLabel = app.label
                showWatchedPicker = false
            },
        )
    }
    showOpenAppPickerFor?.let { index ->
        AppPickerDialog(
            title = "איזו אפליקציה לפתוח?",
            onDismiss = { showOpenAppPickerFor = null },
            onPick = { app ->
                actions[index] = actions[index].copy(stringValue = app.packageName)
                showOpenAppPickerFor = null
            },
        )
    }
}

private fun defaultActionFor(type: ActionType): RuleAction = when (type) {
    ActionType.SOUND_MODE -> RuleAction(ruleId = 0, type = type, intValue = 2)
    ActionType.MEDIA_VOLUME -> RuleAction(ruleId = 0, type = type, intValue = 50)
    ActionType.DND -> RuleAction(ruleId = 0, type = type, intValue = 0)
    ActionType.OPEN_APP -> RuleAction(ruleId = 0, type = type, stringValue = null)
    ActionType.NOTIFY -> RuleAction(ruleId = 0, type = type, stringValue = "")
    ActionType.BRIGHTNESS -> RuleAction(ruleId = 0, type = type, intValue = 50)
    ActionType.WIFI_PANEL -> RuleAction(ruleId = 0, type = type)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionEditor(
    action: RuleAction,
    onChange: (RuleAction) -> Unit,
    onPickApp: () -> Unit,
) {
    val context = LocalContext.current
    when (action.type) {
        ActionType.SOUND_MODE -> {
            val labels = listOf("שקט", "רטט", "צלצול")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = (action.intValue ?: 2) == i,
                        onClick = { onChange(action.copy(intValue = i)) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    ) { Text(label) }
                }
            }
        }
        ActionType.MEDIA_VOLUME, ActionType.BRIGHTNESS -> {
            val value = action.intValue ?: 50
            Column {
                Text("$value%")
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onChange(action.copy(intValue = it.toInt())) },
                    valueRange = 0f..100f,
                )
            }
        }
        ActionType.DND -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (action.intValue == 1) "הפעלת נא לא להפריע" else "כיבוי נא לא להפריע",
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = action.intValue == 1,
                    onCheckedChange = { onChange(action.copy(intValue = if (it) 1 else 0)) },
                )
            }
        }
        ActionType.OPEN_APP -> {
            OutlinedButton(onClick = onPickApp, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (action.stringValue.isNullOrBlank()) "בחר אפליקציה לפתיחה…"
                    else action.describe(context)
                )
            }
        }
        ActionType.NOTIFY -> {
            OutlinedTextField(
                value = action.stringValue.orEmpty(),
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = { Text("טקסט התזכורת") },
                placeholder = { Text("למשל: סגרת את יוטיוב — זמן לחזור ללמוד") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActionType.WIFI_PANEL -> {
            Text(
                "ייפתח פאנל ה-Wi-Fi של המערכת (אנדרואיד לא מאפשר כיבוי/הדלקה ישירים).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
