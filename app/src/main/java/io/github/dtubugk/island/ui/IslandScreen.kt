package io.github.dtubugk.island.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.dtubugk.island.data.IslandColors
import io.github.dtubugk.island.data.IslandConfig
import io.github.dtubugk.island.data.TextSide
import io.github.dtubugk.island.island.BatteryState
import io.github.dtubugk.island.island.IslandView
import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Everything the screen reports back; MainActivity turns these into settings writes and intents. */
interface IslandActions {
    fun update(transform: (IslandConfig) -> IslandConfig)
    fun enableService()
    fun openAppInfo()
    fun previewExpanded()
    fun previewCharging()
}

@Composable
fun IslandScreen(
    config: IslandConfig,
    serviceOn: Boolean,
    battery: BatteryState,
    actions: IslandActions,
    clock: () -> LocalDateTime = { LocalDateTime.now() },
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("אי דינמי", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "האי של האייפון, סביב המצלמה של הגלקסי שלך",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            HeroPreview(config, battery, clock)
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = serviceOn,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "status",
            ) { on ->
                if (on) ActiveCard(config, actions) else SetupCard(actions)
            }
            Spacer(Modifier.height(16.dp))
            TextCard(config, actions)
            Spacer(Modifier.height(16.dp))
            SizeCard(config, actions)
            Spacer(Modifier.height(16.dp))
            BehaviorCard(config, actions)
            Spacer(Modifier.height(20.dp))
            Text(
                "לחיצה ארוכה על האי פותחת את המסך הזה. החלקה למטה מהאי פותחת את ההתראות.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// --- hero -------------------------------------------------------------------------------------

@Composable
private fun HeroPreview(config: IslandConfig, battery: BatteryState, clock: () -> LocalDateTime) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(184.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF263F86), Color(0xFF2F5BA6), Color(0xFF3D82C6))))
            .semantics { contentDescription = "תצוגה מקדימה של האי. הקישו כדי להרחיב." },
    ) {
        // A soft heart-pink glow, echoing the wallpaper this was designed on.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 8.dp)
                .size(120.dp)
                .background(Brush.radialGradient(listOf(Brand.Pink.copy(alpha = 0.35f), Color.Transparent)), CircleShape),
        )
        MockStatusBar(battery, clock)
        AndroidView(
            factory = { context ->
                IslandView(context, isOverlay = false).apply { drawLens = true }
            },
            update = { view ->
                view.clock = clock
                view.battery = battery
                view.setConfig(config.copy(visible = true))
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "הקישו על האי",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .background(Color.Black.copy(alpha = 0.22f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MockStatusBar(battery: BatteryState, clock: () -> LocalDateTime) {
    val now = clock()
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%d:%02d".format(now.hour, now.minute),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.weight(1f))
        Text("${battery.level}", color = Color.White, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier
                .size(width = 20.dp, height = 10.dp)
                .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                .padding(2.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(battery.level / 100f)
                    .height(6.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White, RoundedCornerShape(1.5.dp)),
            )
        }
    }
}

// --- status -----------------------------------------------------------------------------------

@Composable
private fun SetupCard(actions: IslandActions) {
    Column {
        SectionCard {
            Text("האי עוד לא פעיל", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "כדי שהאי יופיע מעל שורת הסטטוס צריך להפעיל אותו פעם אחת בהגדרות הנגישות. " +
                    "האפליקציה לא קוראת את מה שעל המסך, היא רק מציירת את האי.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = actions::enableService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("הפעלת האי", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(8.dp))
            Text(
                "בהגדרות: אפליקציות מותקנות ← אי דינמי ← הפעלה",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
            Text("מופיעה ההודעה \"הגדרה מוגבלת\"?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "זה נורמלי באפליקציה שהותקנה מקובץ. שלושה צעדים ופותרים:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Step(1, "לוחצים למטה על \"פרטי האפליקציה\".")
            Step(2, "בפינה העליונה לוחצים על ⋮ ובוחרים לאפשר הגדרות מוגבלות.")
            Step(3, "חוזרים לכאן ולוחצים שוב על \"הפעלת האי\".")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = actions::openAppInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("פרטי האפליקציה", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActiveCard(config: IslandConfig, actions: IslandActions) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val green = if (MaterialTheme.colorScheme.background.luminanceIsDark()) Brand.SuccessDark else Brand.Success
            Box(
                Modifier
                    .size(12.dp)
                    .background(if (config.visible) green else MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (config.visible) "האי פעיל" else "האי מוסתר", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (config.visible) "הוא מופיע עכשיו למעלה, סביב המצלמה" else "השירות פועל, האי פשוט לא מוצג",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = config.visible,
                onCheckedChange = { v -> actions.update { it.copy(visible = v) } },
                modifier = Modifier.semantics { contentDescription = "הצגת האי" },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = actions::previewExpanded,
                enabled = config.visible,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("נסו הרחבה") }
            FilledTonalButton(
                onClick = actions::previewCharging,
                enabled = config.visible,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("נסו טעינה") }
        }
    }
}

// --- customization ----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextCard(config: IslandConfig, actions: IslandActions) {
    SectionCard {
        SectionTitle("מה כתוב באי")
        OutlinedTextField(
            value = config.text,
            onValueChange = { v ->
                val clipped = v.replace("\n", "").take(IslandConfig.MAX_TEXT_LENGTH)
                actions.update { it.copy(text = clipped) }
            },
            singleLine = true,
            label = { Text("טקסט") },
            supportingText = { Text("עד ${IslandConfig.MAX_TEXT_LENGTH} תווים. אפשר גם אימוג׳י, או להשאיר ריק.") },
            keyboardOptions = KeyboardOptions.Default,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        FieldLabel("צבע")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            IslandColors.swatches.forEachIndexed { index, color ->
                Swatch(color, selected = index == config.colorIndex, index = index) {
                    actions.update { it.copy(colorIndex = index) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FieldLabel("באיזה צד של המצלמה")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(TextSide.RIGHT to "ימין", TextSide.LEFT to "שמאל")
            options.forEachIndexed { i, (side, label) ->
                SegmentedButton(
                    selected = config.textSide == side,
                    onClick = { actions.update { it.copy(textSide = side) } },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(label) }
            }
        }
    }
}

private val swatchNames = listOf("לבן", "ורוד", "תכלת", "זהב", "מנטה", "סגול", "מעבר צבע")

@Composable
private fun Swatch(color: Int, selected: Boolean, index: Int, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "ring")
    val fill = if (color == IslandColors.GRADIENT) {
        Brush.linearGradient(listOf(Color(IslandColors.GRADIENT_START), Color(IslandColors.GRADIENT_END)))
    } else {
        Brush.linearGradient(listOf(Color(color), Color(color)))
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = swatchNames.getOrElse(index) { "" } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .alpha(ring)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
        )
        Box(
            Modifier
                .size(30.dp)
                .background(Color.Black, CircleShape)
                .padding(3.dp)
                .background(fill, CircleShape),
        )
    }
}

@Composable
private fun SizeCard(config: IslandConfig, actions: IslandActions) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("גודל ומיקום", Modifier.weight(1f))
            TextButton(onClick = {
                val d = IslandConfig()
                actions.update { it.copy(widthDp = d.widthDp, heightDp = d.heightDp, offsetXDp = 0f, offsetYDp = 0f) }
            }) { Text("איפוס") }
        }
        LabeledSlider("רוחב", config.widthDp, IslandConfig.MIN_WIDTH_DP..IslandConfig.MAX_WIDTH_DP, suffix = "dp") { v ->
            actions.update { it.copy(widthDp = v) }
        }
        LabeledSlider("גובה", config.heightDp, IslandConfig.MIN_HEIGHT_DP..IslandConfig.MAX_HEIGHT_DP, suffix = "dp") { v ->
            actions.update { it.copy(heightDp = v) }
        }
        // Position sliders move the island the way the thumb moves, so they stay left-to-right.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column {
                LabeledSlider("הזזה לצדדים", config.offsetXDp, -IslandConfig.MAX_OFFSET_X_DP..IslandConfig.MAX_OFFSET_X_DP, signed = true) { v ->
                    actions.update { it.copy(offsetXDp = v) }
                }
                LabeledSlider("הזזה למעלה ולמטה", config.offsetYDp, -IslandConfig.MAX_OFFSET_Y_DP..IslandConfig.MAX_OFFSET_Y_DP, signed = true) { v ->
                    actions.update { it.copy(offsetYDp = v) }
                }
            }
        }
        Text(
            "האי תמיד נשאר גדול מספיק כדי להסתיר את המצלמה ואת הטקסט.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    signed: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        // Labels always read right-to-left, even inside the left-to-right position sliders.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val shown = value.roundToInt()
                val number = if (signed && shown > 0) "+$shown" else "$shown"
                Text(
                    "⁦$number$suffix⁩",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onChange((it * 2).roundToInt() / 2f) },
            valueRange = range,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun BehaviorCard(config: IslandConfig, actions: IslandActions) {
    SectionCard {
        SectionTitle("התנהגות")
        ToggleRow("הרחבה בלחיצה", "לחיצה פותחת כרטיס עם שעה, תאריך וסוללה", config.expandOnTap) { v ->
            actions.update { it.copy(expandOnTap = v) }
        }
        ToggleRow("אנימציית טעינה", "האי מתרחב לרגע כשמחברים מטען", config.chargingAnimation) { v ->
            actions.update { it.copy(chargingAnimation = v) }
        }
        ToggleRow("רטט", "רטט עדין כשנוגעים באי", config.haptics) { v ->
            actions.update { it.copy(haptics = v) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

// --- building blocks --------------------------------------------------------------------------

@Composable
private fun SectionCard(
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(color = container, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
