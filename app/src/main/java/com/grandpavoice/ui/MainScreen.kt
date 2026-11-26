package com.grandpavoice.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grandpavoice.R
import com.grandpavoice.state.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import com.grandpavoice.settings.AppLanguage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class Motion { Breathe, Ripple, Dots, None }

private data class Look(
    val fill: Color,
    val iconTint: Color,
    @DrawableRes val icon: Int?,
    val motion: Motion,
    @StringRes val title: Int,
    @StringRes val hint: Int?,
)

private fun lookFor(state: UiState): Look = when (state) {
    UiState.Standby -> Look(Palette.Surface2, Palette.Accent, R.drawable.ic_mic, Motion.Breathe, R.string.state_standby, R.string.hint_standby)
    UiState.Connecting -> Look(Palette.Thinking, Palette.OnAccent, null, Motion.Dots, R.string.state_connecting, R.string.hint_connecting)
    UiState.Listening -> Look(Palette.Listening, Palette.OnAccent, R.drawable.ic_mic, Motion.Ripple, R.string.state_listening, R.string.hint_listening)
    UiState.Thinking -> Look(Palette.Thinking, Palette.OnAccent, null, Motion.Dots, R.string.state_thinking, null)
    UiState.Speaking -> Look(Palette.Speaking, Palette.OnAccent, R.drawable.ic_speaking, Motion.Ripple, R.string.state_speaking, null)
    UiState.Goodbye -> Look(Palette.Goodbye, Palette.OnAccent, R.drawable.ic_heart, Motion.None, R.string.state_goodbye, R.string.hint_goodbye)
    is UiState.Error -> Look(Palette.Error, Color.White, R.drawable.ic_error, Motion.None, state.messageRes, R.string.hint_try_again)
}

private const val SECRET_PRESS_MS = 3_000L

@Composable
fun MainScreen(
    state: UiState,
    userName: String,
    language: AppLanguage,
    configured: Boolean,
    micGranted: Boolean,
    onMicTap: () -> Unit,
    onOpenSettings: () -> Unit,
    onGrantMic: () -> Unit,
    onAnyTouch: () -> Unit,
) {
    val look = lookFor(state)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val touched by rememberUpdatedState(onAnyTouch)
    val needsSetup = !configured && state == UiState.Standby

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touched()
                }
            }
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hidden door to the settings: press and hold the greeting for 3 seconds.
        Text(
            text = if (userName.isBlank()) stringResource(R.string.greeting)
            else stringResource(R.string.greeting_named, userName),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            lineHeight = 46.sp,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var released = false
                        withTimeoutOrNull(SECRET_PRESS_MS) {
                            waitForUpOrCancellation()
                            released = true
                        }
                        if (!released) openSettings()
                    }
                },
        )
        Clock(language, Modifier.padding(top = 4.dp))

        Spacer(Modifier.weight(1f))
        Orb(look = look, onTap = onMicTap, enabled = configured && micGranted)
        Spacer(Modifier.weight(1f))

        StatusCard(
            title = stringResource(if (needsSetup) R.string.setup_needed_title else look.title),
            hint = when {
                needsSetup -> stringResource(R.string.setup_needed_hint)
                else -> look.hint?.let { stringResource(it) }
            },
            footnote = if (state == UiState.Standby && configured && micGranted) stringResource(R.string.tap_to_talk) else null,
        ) {
            when {
                needsSetup -> PillButton(stringResource(R.string.setup_button), Palette.Accent, Palette.OnAccent, onOpenSettings)
                !micGranted -> PillButton(stringResource(R.string.grant_microphone), Palette.Error, Color.White, onGrantMic)
            }
        }
    }
}

@Composable
private fun Clock(language: AppLanguage, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L * (60 - now.second).coerceAtLeast(1))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = DateTimeFormatter.ofPattern("HH:mm", language.locale).format(now),
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = Palette.Ink,
            lineHeight = 70.sp,
        )
        Text(
            text = DateTimeFormatter.ofPattern("EEEE, d MMMM", language.locale).format(now)
                .replaceFirstChar { it.titlecase(language.locale) },
            fontSize = 24.sp,
            color = Palette.Secondary,
        )
    }
}

@Composable
private fun Orb(look: Look, onTap: () -> Unit, enabled: Boolean) {
    val fill by animateColorAsState(look.fill, tween(400), label = "fill")
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ripple",
    )

    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        if (look.motion == Motion.Ripple) {
            Canvas(Modifier.fillMaxSize()) {
                val base = size.minDimension / 2 * (260f / 320f)
                for (i in 0 until 2) {
                    val p = (ripple + i * 0.5f) % 1f
                    drawCircle(
                        color = fill.copy(alpha = 0.45f * (1f - p)),
                        radius = base * (1f + 0.23f * p),
                        style = Stroke(width = 6.dp.toPx()),
                    )
                }
            }
        }
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(if (look.motion == Motion.Breathe) breathe else 1f)
                .clip(CircleShape)
                .background(fill)
                .then(
                    if (look.motion == Motion.Breathe) Modifier.border(5.dp, Palette.Accent, CircleShape)
                    else Modifier,
                )
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cd_mic),
                    onClick = onTap,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (look.motion == Motion.Dots) {
                ThinkingDots(look.iconTint, ripple)
            } else if (look.icon != null) {
                Icon(
                    painter = painterResource(look.icon),
                    contentDescription = stringResource(R.string.cd_mic),
                    tint = look.iconTint,
                    modifier = Modifier.size(128.dp),
                )
            }
        }
    }
}

@Composable
private fun ThinkingDots(color: Color, phase: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until 3) {
            val p = ((phase * 2f) - i * 0.18f).let { it - kotlin.math.floor(it) }
            val lift = kotlin.math.sin(p * Math.PI).toFloat().coerceAtLeast(0f)
            Box(
                Modifier
                    .size(30.dp)
                    .scale(0.75f + 0.35f * lift)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.55f + 0.45f * lift)),
            )
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    hint: String?,
    footnote: String?,
    actions: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp) // same size in every state, so nothing jumps around
            .clip(RoundedCornerShape(32.dp))
            .background(Palette.Surface)
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp,
        )
        if (hint != null) {
            Text(
                text = hint,
                fontSize = 26.sp,
                color = Palette.Secondary,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (footnote != null) {
            Text(
                text = footnote,
                fontSize = 20.sp,
                color = Palette.Tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        actions()
    }
}

@Composable
private fun PillButton(text: String, container: Color, content: Color, onClick: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
    ) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
    }
}
