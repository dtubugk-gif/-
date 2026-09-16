package il.rikavon.ui.talk

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import il.rikavon.R
import il.rikavon.core.ui.anim.floatLoop
import il.rikavon.core.ui.components.RikavonTopBar
import il.rikavon.core.ui.components.ScreenPadding
import il.rikavon.core.ui.components.SecondaryButton
import il.rikavon.core.ui.components.SegmentPills
import il.rikavon.core.ui.components.SquareIconButton
import il.rikavon.core.ui.components.pressScale
import il.rikavon.core.ui.theme.LocalExtraColors
import il.rikavon.core.ui.theme.Spacing
import il.rikavon.feature.mascot.model.MascotSkin
import il.rikavon.feature.mascot.model.MascotStage
import il.rikavon.feature.mascot.ui.MascotView

/** Talk to the pet: hears the user through the device recogniser, answers in character, out loud. */
@Composable
fun TalkScreen(onBack: () -> Unit, viewModel: TalkViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.toggleListening()
        }
    TalkContent(
        state = state,
        onBack = onBack,
        onMic = {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.toggleListening() else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onSend = viewModel::send,
        onLanguage = viewModel::setLanguage,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
fun TalkContent(
    state: TalkUiState,
    onBack: () -> Unit,
    onMic: () -> Unit,
    onSend: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onDismissError: () -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    if (state.dialing) {
        state.skin?.let { Dialing(skin = it, petName = state.petName, onHangUp = onBack) }
        return
    }
    Scaffold(
        topBar = { RikavonTopBar(title = stringResource(R.string.talk_title, state.petName), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            state.skin?.let { skin ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MascotView(
                        skin = skin,
                        stage = state.stage,
                        interactive = true,
                        modifier = Modifier.size(PET_SIZE).then(if (state.speaking) Modifier.floatLoop() else Modifier),
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                itemsIndexed(state.messages) { _, message -> Bubble(message) }
            }
            StatusLine(state, onDismissError)
            SegmentPills(
                options = listOf("en", "he"),
                selected = state.language,
                onSelect = onLanguage,
                label = { stringResource(if (it == "he") R.string.language_hebrew else R.string.language_english) },
                modifier = Modifier.padding(horizontal = ScreenPadding).fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.talk_type_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                SquareIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.talk_send),
                    onClick = {
                        onSend(draft)
                        draft = ""
                    },
                )
            }
            MicButton(listening = state.listening, enabled = state.micAvailable, onClick = onMic)
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** Outgoing call: the pet in a pulsing ring, "Calling…", one hang-up button, until it picks up. */
@Composable
private fun Dialing(skin: MascotSkin, petName: String, onHangUp: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "dial")
    val ring by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = DIAL_PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(DIAL_PULSE_MILLIS), RepeatMode.Reverse),
            label = "dialRing",
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(horizontal = ScreenPadding, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = stringResource(R.string.talk_calling, petName),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.size(DIAL_RING), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = ring
                            scaleY = ring
                        }.background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
            MascotView(
                skin = skin,
                stage = MascotStage.PRISTINE,
                interactive = false,
                modifier = Modifier.size(PET_SIZE),
            )
        }
        Spacer(Modifier.weight(1f))
        SecondaryButton(
            text = stringResource(R.string.talk_hang_up),
            onClick = onHangUp,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Bubble(message: TalkMessage) {
    val pet = message.fromPet
    val scheme = MaterialTheme.colorScheme
    val bubble = if (pet) scheme.surfaceContainerHigh else scheme.primaryContainer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (pet) Arrangement.Start else Arrangement.End,
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (pet) scheme.onSurface else scheme.onPrimaryContainer,
            modifier =
                Modifier
                    .widthIn(max = BUBBLE_MAX_WIDTH)
                    .background(bubble, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun StatusLine(state: TalkUiState, onDismissError: () -> Unit) {
    val extras = LocalExtraColors.current
    val text =
        when {
            state.error != null ->
                stringResource(
                    when (state.error) {
                        TalkError.NO_PERMISSION -> R.string.talk_error_permission
                        TalkError.NETWORK -> R.string.talk_error_network
                        TalkError.UNAVAILABLE -> R.string.talk_error_unavailable
                        TalkError.NOTHING_HEARD -> R.string.talk_error_nothing
                    },
                )
            state.listening && state.partial.isNotBlank() -> state.partial
            state.listening -> stringResource(R.string.talk_listening)
            state.speaking -> stringResource(R.string.talk_speaking, state.petName)
            !state.micAvailable -> stringResource(R.string.talk_error_unavailable)
            else -> stringResource(R.string.talk_idle)
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.error != null) MaterialTheme.colorScheme.error else extras.onSurfaceMuted,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = Spacing.xs)
                .then(if (state.error != null) Modifier.clickable(onClick = onDismissError) else Modifier),
    )
}

@Composable
private fun MicButton(listening: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val ring by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = if (listening) MIC_PULSE_SCALE else 1f,
            animationSpec = infiniteRepeatable(tween(MIC_PULSE_MILLIS), RepeatMode.Reverse),
            label = "micRing",
        )
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(if (listening) R.string.talk_stop else R.string.talk_mic)
    val scheme = MaterialTheme.colorScheme
    val fill = if (enabled) scheme.primary else scheme.surfaceContainerHigh
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(MIC_SIZE)
                    .graphicsLayer {
                        scaleX = ring
                        scaleY = ring
                        alpha = if (listening) MIC_RING_ALPHA else 0f
                    }.background(scheme.primary, CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .size(MIC_SIZE)
                    .pressScale(interaction)
                    .background(fill, CircleShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    ).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MicIcon,
                contentDescription = null,
                tint = if (enabled) scheme.onPrimary else LocalExtraColors.current.onSurfaceMuted,
                modifier = Modifier.size(MIC_ICON),
            )
        }
    }
}

private val PET_SIZE = 140.dp
private val BUBBLE_MAX_WIDTH = 300.dp
private val MIC_SIZE = 72.dp
private val MIC_ICON = 32.dp
private const val MIC_PULSE_SCALE = 1.3f
private const val MIC_PULSE_MILLIS = 800
private const val MIC_RING_ALPHA = 0.25f
private val DIAL_RING = 220.dp
private const val DIAL_PULSE_SCALE = 1.08f
private const val DIAL_PULSE_MILLIS = 900
