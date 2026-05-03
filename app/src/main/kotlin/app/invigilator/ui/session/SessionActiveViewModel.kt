package app.invigilator.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.session.SessionStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionActiveViewModel @Inject constructor(
    private val sessionState: SessionStateRepository,
) : ViewModel() {

    private val _elapsed = MutableStateFlow(0L)

    val state: StateFlow<SessionActiveUiState> = combine(
        sessionState.activeSession,
        _elapsed,
    ) { session, elapsed ->
        SessionActiveUiState(
            isActive = session != null,
            elapsedSeconds = elapsed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionActiveUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val started = sessionState.activeSession.value?.startedAtMillis ?: continue
                _elapsed.value = (System.currentTimeMillis() - started) / 1000
            }
        }
    }
}

data class SessionActiveUiState(
    val isActive: Boolean = false,
    val elapsedSeconds: Long = 0,
)
