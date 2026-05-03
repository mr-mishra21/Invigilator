package app.invigilator.ui.session

import androidx.lifecycle.ViewModel
import app.invigilator.core.session.SessionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class SessionMode { TIMED, OPEN }

data class StartSessionUiState(
    val mode: SessionMode = SessionMode.TIMED,
    val selectedDurationMinutes: Int = 25,
)

sealed interface StartSessionEvent {
    data class ModeChanged(val mode: SessionMode) : StartSessionEvent
    data class DurationChanged(val minutes: Int) : StartSessionEvent
    data object StartClicked : StartSessionEvent
}

@HiltViewModel
class StartSessionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(StartSessionUiState())
    val state: StateFlow<StartSessionUiState> = _state.asStateFlow()

    fun onEvent(event: StartSessionEvent) {
        when (event) {
            is StartSessionEvent.ModeChanged ->
                _state.update { it.copy(mode = event.mode) }
            is StartSessionEvent.DurationChanged ->
                _state.update { it.copy(selectedDurationMinutes = event.minutes) }
            StartSessionEvent.StartClicked -> { /* handled by Route */ }
        }
    }

    fun buildSessionType(): SessionType = when (_state.value.mode) {
        SessionMode.TIMED -> SessionType.Timed(_state.value.selectedDurationMinutes)
        SessionMode.OPEN -> SessionType.OpenEnded
    }
}
