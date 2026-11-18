package com.grandpavoice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandpavoice.R
import com.grandpavoice.settings.Defaults
import com.grandpavoice.settings.Settings
import com.grandpavoice.state.CompanionState
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/** Actions that need an Activity; implemented in [MainActivity]. */
interface SettingsActions {
    val isDeviceOwner: Boolean
    val isInLockTask: Boolean
    val hasBatteryExemption: Boolean
    val appVersion: String
    fun requestBatteryExemption()
    fun openHomeSettings()
    fun openAppSettings()
    fun exitKiosk()
    fun removeDeviceOwner()

    /** Asks OpenAI (or the server) for a short-lived key with these settings. Free; nothing is spoken. */
    suspend fun testConnection(settings: Settings): TestResult
}

data class TestResult(val ok: Boolean, val message: String)

/** Hidden settings for the family member who sets the phone up. Normal-size UI on purpose. */
@Composable
fun SettingsScreen(
    initial: Settings,
    actions: SettingsActions,
    onSave: (Settings) -> Unit,
    onClose: () -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    var showSecret by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    val scope = rememberCoroutineScope()
    val liveG by CompanionState.liveShakeG.collectAsStateWithLifecycle()
    val shakes by CompanionState.shakeTriggers.collectAsStateWithLifecycle()

    // While settings are open, shakes are only counted (for tuning) and do not start conversations.
    DisposableEffect(Unit) {
        CompanionState.testMode = true
        onDispose { CompanionState.testMode = false }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Настройки", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Palette.Surface2)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_close), "Затвори", tint = Palette.Ink, modifier = Modifier.size(24.dp))
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Note("За близкия, който подготвя телефона. Отваря се, като задържите поздрава 3 секунди.")

            Section("Връзка с OpenAI") {
                Segmented(
                    options = listOf(Settings.MODE_OPENAI_KEY to "Ключ от OpenAI", Settings.MODE_SERVER to "Собствен сървър"),
                    selected = s.connectionMode,
                ) { s = s.copy(connectionMode = it); testResult = null }

                if (s.connectionMode == Settings.MODE_OPENAI_KEY) {
                    SecretField("API ключ (sk-…)", s.openAiKey, showSecret) { s = s.copy(openAiKey = it); testResult = null }
                    Note(
                        "Създайте ключ на platform.openai.com → API keys и задайте месечен лимит в Billing. " +
                            "Ключът се пази шифрован само на този телефон.",
                    )
                } else {
                    Field("Адрес на сървъра (https://…)", s.backendUrl, KeyboardType.Uri) { s = s.copy(backendUrl = it); testResult = null }
                    SecretField("Ключ на устройството (DEVICE_TOKEN)", s.deviceToken, showSecret) { s = s.copy(deviceToken = it); testResult = null }
                    Note("Сървърът пази ключа от OpenAI. Вижте backend/README.md.")
                }
                SwitchRow("Покажи ключа", showSecret) { showSecret = it }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            testing = true
                            testResult = null
                            scope.launch {
                                testResult = actions.testConnection(s)
                                testing = false
                            }
                        },
                        enabled = !testing && s.isConfigured,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Palette.Surface3, contentColor = Palette.Ink),
                    ) { Text(if (testing) "Проверявам…" else "Провери връзката", fontSize = 16.sp) }
                }
                testResult?.let {
                    Text(
                        (if (it.ok) "✓ " else "✕ ") + it.message,
                        color = if (it.ok) Palette.Accent else Palette.Error,
                        fontSize = 16.sp,
                    )
                }
            }

            Section("Човекът") {
                Segmented(
                    options = listOf(Settings.GENDER_FEMALE to "Жена", Settings.GENDER_MALE to "Мъж"),
                    selected = s.userGender,
                ) { gender ->
                    // An unedited default prompt switches along with the gender.
                    val prompt = if (Defaults.isDefaultPrompt(s.systemPrompt)) Defaults.systemPrompt(gender) else s.systemPrompt
                    s = s.copy(userGender = gender, systemPrompt = prompt)
                }
                Note("Българският има род: асистентът ще казва „уморена ли си“ на жена и „уморен ли си“ на мъж.")
                Field("Име, с което да се обръща (по желание)", s.userName) { s = s.copy(userName = it) }
            }

            Section("Глас") {
                Label("Модел")
                RadioGroup(
                    listOf(
                        Defaults.MODEL to "Най-естествен глас (${Defaults.MODEL})",
                        Defaults.MODEL_ECONOMY to "По-евтин (${Defaults.MODEL_ECONOMY})",
                    ),
                    s.model,
                ) { s = s.copy(model = it) }
                Field("Друг модел (по желание)", s.model) { s = s.copy(model = it) }
                Label("Глас")
                Chips(Defaults.VOICES, s.voice) { s = s.copy(voice = it) }
                Note("marin и cedar звучат най-естествено.")
                SliderRow(
                    label = "Скорост на говорене",
                    valueText = String.format(Locale.US, "%.2f×", s.speechSpeed),
                    value = s.speechSpeed,
                    range = 0.7f..1.2f,
                    steps = 9,
                ) { s = s.copy(speechSpeed = (it * 20).roundToInt() / 20f) }
            }

            Section("Разговор") {
                Label("Кога асистентът да реши, че човекът е спрял да говори")
                RadioGroup(
                    listOf(
                        Settings.VAD_SEMANTIC_LOW to "Търпеливо – изчаква паузите (препоръчително)",
                        Settings.VAD_SEMANTIC_MEDIUM to "Нормално",
                        Settings.VAD_SERVER_PATIENT to "По тишина – след около 1,2 секунди мълчание",
                    ),
                    s.vadMode,
                ) { s = s.copy(vadMode = it) }
                SwitchRow("Може да прекъсва асистента, докато говори", s.allowInterrupt) { s = s.copy(allowInterrupt = it) }
                Note("Изключете само ако асистентът сам се прекъсва, защото микрофонът чува високоговорителя.")
                SliderRow("Край след мълчание", "${s.inactivitySeconds} сек", s.inactivitySeconds.toFloat(), 30f..600f, 18) {
                    s = s.copy(inactivitySeconds = ((it / 30f).roundToInt() * 30).coerceIn(30, 600))
                }
                SliderRow("Най-дълъг разговор", "${s.maxSessionMinutes} мин", s.maxSessionMinutes.toFloat(), 5f..60f, 10) {
                    s = s.copy(maxSessionMinutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 60))
                }
                SwitchRow("Максимална сила на звука при разговор", s.maxVolume) { s = s.copy(maxVolume = it) }
            }

            Section("Разклащане") {
                ShakeMeter(liveG, s.shakeThresholdG, shakes)
                Note("Докато тази страница е отворена, разклащането само се отчита и не започва разговор.")
                SliderRow(
                    label = "Праг (по-ниско = по-чувствително)",
                    valueText = String.format(Locale.US, "%.1f g", s.shakeThresholdG),
                    value = s.shakeThresholdG,
                    range = 1.5f..3.5f,
                    steps = 19,
                ) { s = s.copy(shakeThresholdG = (it * 10).roundToInt() / 10f) }
                SliderRow("Нужни тласъци", "${s.shakeCount}", s.shakeCount.toFloat(), 2f..8f, 5) {
                    s = s.copy(shakeCount = it.roundToInt())
                }
                SliderRow("В рамките на", "${s.shakeWindowMs} мс", s.shakeWindowMs.toFloat(), 800f..3000f, 21) {
                    s = s.copy(shakeWindowMs = ((it / 100f).roundToInt() * 100))
                }
                SliderRow("Пауза след разклащане", "${s.cooldownMs / 1000.0} сек", s.cooldownMs.toFloat(), 1000f..10000f, 17) {
                    s = s.copy(cooldownMs = ((it / 500f).roundToInt() * 500))
                }
                SwitchRow("Разпознавай и при изгасен екран (харчи батерия)", s.detectWhenScreenOff) {
                    s = s.copy(detectWhenScreenOff = it)
                }
            }

            Section("Екран") {
                RadioGroup(
                    listOf(
                        Settings.SCREEN_ON_CHARGING to "Свети, докато телефонът се зарежда",
                        Settings.SCREEN_ON_ALWAYS to "Свети винаги",
                        Settings.SCREEN_ON_NEVER to "Нормално поведение на телефона",
                    ),
                    s.keepScreenOn,
                ) { s = s.copy(keepScreenOn = it) }
                SwitchRow("Затъмни екрана, когато не се използва", s.dimWhenIdle) { s = s.copy(dimWhenIdle = it) }
            }

            Section("Телефон") {
                Status(
                    actions.hasBatteryExemption,
                    "Оптимизацията на батерията е изключена.",
                    "Оптимизацията на батерията е включена – Android може да спре приложението.",
                )
                SecondaryButton("Изключи оптимизацията на батерията", actions::requestBatteryExemption)
                SecondaryButton("Направи го начален екран (Home)", actions::openHomeSettings)
                SecondaryButton("Разрешения на приложението", actions::openAppSettings)
                Note(
                    when {
                        actions.isInLockTask -> "Киоск режимът е включен."
                        actions.isDeviceOwner -> "Приложението е собственик на устройството (киоск режимът е на пауза)."
                        else -> "Киоск режимът не е настроен (по желание, вижте инструкциите)."
                    },
                )
                if (actions.isInLockTask) SecondaryButton("Излез от киоск режим до рестарт", actions::exitKiosk)
                if (actions.isDeviceOwner) SecondaryButton("Премахни киоск режима напълно", actions::removeDeviceOwner)
            }

            Section("Инструкции към асистента") {
                OutlinedTextField(
                    value = Defaults.effectivePrompt(s),
                    onValueChange = { s = s.copy(systemPrompt = it) },
                    minLines = 6,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Palette.Ink),
                    colors = fieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton("Върни инструкциите по подразбиране") { s = s.copy(systemPrompt = Defaults.systemPrompt(s.userGender)) }
                Note("Правилата за български език и за край на разговора се добавят автоматично и винаги важат.")
            }

            Note("Версия ${actions.appVersion}")
            Spacer(Modifier.height(16.dp))
        }

        // Sticky save bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { onSave(s) },
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Accent, contentColor = Palette.OnAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) { Text("Запази", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(Locale("bg")),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Palette.Secondary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 8.dp, top = 18.dp),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Palette.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 14.sp, color = Palette.Secondary, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Ink)
}

@Composable
private fun Status(ok: Boolean, okText: String, badText: String) {
    Text(
        if (ok) "✓ $okText" else "! $badText",
        fontSize = 16.sp,
        color = if (ok) Palette.Accent else Palette.Thinking,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Accent,
    unfocusedBorderColor = Palette.Surface3,
    focusedLabelColor = Palette.Accent,
    unfocusedLabelColor = Palette.Secondary,
    cursorColor = Palette.Accent,
    focusedContainerColor = Palette.Surface2,
    unfocusedContainerColor = Palette.Surface2,
)

@Composable
private fun Field(label: String, value: String, keyboardType: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecretField(label: String, value: String, visible: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        colors = fieldColors(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Surface2, contentColor = Palette.Ink),
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text, fontSize = 16.sp) }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Palette.Accent,
                checkedThumbColor = Palette.OnAccent,
                uncheckedTrackColor = Palette.Surface3,
                uncheckedThumbColor = Palette.Secondary,
                uncheckedBorderColor = Palette.Surface3,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun <T> RadioGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = value == selected, role = Role.RadioButton, onClick = { onSelect(value) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = value == selected, onClick = null)
                Text(label, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface2)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Palette.Accent else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(value) })
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) Palette.OnAccent else Palette.Secondary,
                )
            }
        }
    }
}

@Composable
private fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    val on = option == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (on) Palette.Accent else Palette.Surface2)
                            .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(option, fontSize = 15.sp, color = if (on) Palette.OnAccent else Palette.Ink)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Live bar: how hard the phone is moving right now vs. the trigger threshold. */
@Composable
private fun ShakeMeter(liveG: Float, threshold: Float, recognised: Int) {
    val max = 4f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(String.format(Locale.US, "Сега: %.1f g", liveG), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Разпознати: $recognised", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Palette.Accent)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Palette.Surface3),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((liveG / max).coerceIn(0f, 1f))
                    .background(if (liveG >= threshold) Palette.Accent else Palette.Speaking),
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((threshold / max).coerceIn(0f, 1f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.fillMaxHeight().size(3.dp).background(Palette.Ink))
            }
        }
    }
}
