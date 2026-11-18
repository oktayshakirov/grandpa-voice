package com.grandpavoice.state

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the big screen shows. Owned by the service, observed by the UI. */
sealed interface UiState {
    data object Standby : UiState
    data object Connecting : UiState
    data object Listening : UiState
    data object Thinking : UiState
    data object Speaking : UiState
    data object Goodbye : UiState
    data class Error(@StringRes val messageRes: Int) : UiState
}

/** Process-wide state shared between [com.grandpavoice.service.CompanionService] and the UI. */
object CompanionState {
    private val _state = MutableStateFlow<UiState>(UiState.Standby)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun set(value: UiState) {
        _state.value = value
    }

    /** Live peak acceleration (in g), shown in the hidden settings to tune sensitivity. */
    val liveShakeG = MutableStateFlow(0f)

    /** Number of recognised shakes since app start (settings test display). */
    val shakeTriggers = MutableStateFlow(0)

    /** True while the settings screen is open: shakes are counted but do not start a conversation. */
    @Volatile var testMode: Boolean = false

    /** A shake arrived while the service could not use the microphone; the UI will start it when visible. */
    @Volatile var pendingStart: Boolean = false

    /** Kiosk mode temporarily turned off from settings (until the app restarts). */
    @Volatile var kioskSuspended: Boolean = false
}
