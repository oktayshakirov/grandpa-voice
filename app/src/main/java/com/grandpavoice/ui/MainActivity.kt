package com.grandpavoice.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grandpavoice.BuildConfig
import com.grandpavoice.GrandpaVoiceApp
import com.grandpavoice.R
import com.grandpavoice.settings.AppLanguage
import com.grandpavoice.settings.forLanguage
import com.grandpavoice.conversation.Instructions
import com.grandpavoice.conversation.KeyException
import com.grandpavoice.conversation.KeyProvider
import com.grandpavoice.kiosk.Kiosk
import com.grandpavoice.service.CompanionService
import com.grandpavoice.settings.Settings
import com.grandpavoice.state.CompanionState
import com.grandpavoice.state.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import android.provider.Settings as AndroidSettings

class MainActivity : ComponentActivity() {

    private val app get() = application as GrandpaVoiceApp

    private var charging by mutableStateOf(false)
    private var micGranted by mutableStateOf(false)
    private var resumeCount by mutableIntStateOf(0)
    private var touchCount by mutableIntStateOf(0)
    private var askedForPermissions = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            micGranted = hasMicPermission()
            if (micGranted) startCompanionService(null)
        }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateCharging(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()
        Kiosk.applyPolicies(this)

        setContent {
            val state by CompanionState.state.collectAsStateWithLifecycle()
            val settings by app.settings.flow.collectAsStateWithLifecycle()
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var draftLanguage by rememberSaveable { mutableStateOf(settings.language.tag) }
            val onboarding = !settings.languageChosen
            val language = if (onboarding) AppLanguage.fromTag(draftLanguage) else settings.language
            val configuration = LocalConfiguration.current
            val localized = remember(language, configuration) { forLanguage(language) }
            val layoutDirection = if (localized.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            // Never leave the app with the Back button; Back only closes settings.
            BackHandler(enabled = true) { if (showSettings) showSettings = false }

            LaunchedEffect(onboarding) {
                if (!onboarding && !micGranted && !askedForPermissions) {
                    askedForPermissions = true
                    permissionLauncher.launch(requiredPermissions())
                }
            }

            LaunchedEffect(settings.keepScreenOn, charging, state) {
                applyKeepScreenOn(settings.keepScreenOn, charging, active = state != UiState.Standby)
            }
            LaunchedEffect(state, touchCount, resumeCount, settings.dimWhenIdle, showSettings, onboarding) {
                setDimmed(false)
                if (state == UiState.Standby && settings.dimWhenIdle && !showSettings && !onboarding) {
                    delay(DIM_AFTER_MS)
                    setDimmed(true)
                }
            }

            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalLayoutDirection provides layoutDirection,
            ) {
                GrandpaTheme {
                    if (onboarding) {
                        LanguageOnboarding(
                            selected = language,
                            onSelect = { draftLanguage = it.tag },
                            onContinue = {
                                app.settings.save(settings.copy(language = language, languageChosen = true))
                                if (micGranted) startCompanionService(null)
                                showSettings = !settings.isConfigured
                            },
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            initial = settings,
                            actions = settingsActions,
                            onSave = {
                                app.settings.save(it)
                                if (micGranted) startCompanionService(null)
                                showSettings = false
                            },
                            onClose = { showSettings = false },
                        )
                    } else {
                        MainScreen(
                            state = state,
                            userName = settings.userName,
                            language = language,
                            configured = settings.isConfigured,
                            micGranted = micGranted,
                            onMicTap = { startCompanionService(CompanionService.ACTION_TOGGLE) },
                            onOpenSettings = { showSettings = true },
                            onGrantMic = ::onGrantMicClicked,
                            onAnyTouch = { touchCount++ },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        hideSystemBars()
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::updateCharging)

        micGranted = hasMicPermission()
        if (micGranted && app.settings.current.languageChosen) {
            // Started while visible => the service may use the microphone later, even with the screen off.
            startCompanionService(null)
            if (CompanionState.pendingStart) startCompanionService(CompanionService.ACTION_START_PENDING)
        }

        if (!CompanionState.kioskSuspended) Kiosk.enterIfAllowed(this)
    }

    override fun onPause() {
        runCatching { unregisterReceiver(powerReceiver) }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------------------------------------------------------------- service

    private fun startCompanionService(action: String?) {
        val intent = Intent(this, CompanionService::class.java)
            .putExtra(CompanionService.EXTRA_FROM_UI, true)
        if (action != null) intent.action = action
        // We are in the foreground, so a plain startService is allowed; the service promotes itself.
        runCatching { startService(intent) }
    }

    // ---------------------------------------------------------------- permissions

    private fun hasMicPermission() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }

    private fun onGrantMicClicked() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) || !askedForPermissions) {
            askedForPermissions = true
            permissionLauncher.launch(requiredPermissions())
        } else {
            openAppSettings()
        }
    }

    // ---------------------------------------------------------------- screen

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateCharging(intent: Intent) {
        if (intent.action != Intent.ACTION_BATTERY_CHANGED) return
        charging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun applyKeepScreenOn(mode: String, charging: Boolean, active: Boolean) {
        val keepOn = active || when (mode) {
            Settings.SCREEN_ON_ALWAYS -> true
            Settings.SCREEN_ON_CHARGING -> charging
            else -> false
        }
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Dims an idle, always-on screen (saves power, reduces screen burn-in). Any touch or shake restores it. */
    private fun setDimmed(dimmed: Boolean) {
        val params = window.attributes
        val target = if (dimmed) DIM_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (params.screenBrightness != target) {
            params.screenBrightness = target
            window.attributes = params
        }
    }

    // ---------------------------------------------------------------- settings actions

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        }
    }

    private val settingsActions = object : SettingsActions {
        override val isDeviceOwner: Boolean
            get() {
                resumeCount // re-read after returning from system screens
                return Kiosk.isDeviceOwner(this@MainActivity)
            }
        override val isInLockTask: Boolean
            get() {
                resumeCount
                return Kiosk.isInLockTask(this@MainActivity)
            }
        override val hasBatteryExemption: Boolean
            get() {
                resumeCount
                return getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            }

        override fun requestBatteryExemption() {
            runCatching {
                @Suppress("BatteryLife")
                startActivity(
                    Intent(
                        AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
        }

        override fun openHomeSettings() {
            runCatching { startActivity(Intent(AndroidSettings.ACTION_HOME_SETTINGS)) }
        }

        override fun openAppSettings() = this@MainActivity.openAppSettings()

        override val appVersion: String = BuildConfig.VERSION_NAME

        override suspend fun testConnection(settings: Settings): TestResult = withContext(Dispatchers.IO) {
            val localized = forLanguage(settings.language)
            try {
                val ticket = KeyProvider(OkHttpClient()).fetch(
                    settings.copy(openAiKey = settings.openAiKey.trim(), model = settings.model.trim()),
                    Instructions.build(settings),
                )
                val server = if (ticket.callsUrl != KeyProvider.OPENAI_CALLS_URL) {
                    " (${localized.getString(R.string.test_server)})"
                } else ""
                TestResult(true, localized.getString(R.string.test_success, settings.model) + server)
            } catch (e: KeyException) {
                val message = when (e.reason) {
                    KeyException.Reason.NOT_CONFIGURED -> R.string.test_missing
                    KeyException.Reason.UNAUTHORIZED -> R.string.test_unauthorized
                    KeyException.Reason.NO_CREDIT -> R.string.test_no_credit
                    KeyException.Reason.RATE_LIMITED -> R.string.test_rate_limited
                    KeyException.Reason.NETWORK -> R.string.test_network
                    KeyException.Reason.SERVER -> R.string.test_error
                }
                TestResult(false, localized.getString(message))
            }
        }

        override fun exitKiosk() {
            CompanionState.kioskSuspended = true
            Kiosk.exitTemporarily(this@MainActivity)
            resumeCount++
        }

        override fun removeDeviceOwner() {
            CompanionState.kioskSuspended = true
            Kiosk.removeDeviceOwner(this@MainActivity)
            resumeCount++
        }
    }

    private companion object {
        const val DIM_AFTER_MS = 90_000L
        const val DIM_BRIGHTNESS = 0.06f
    }
}
