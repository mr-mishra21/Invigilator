package app.invigilator.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionStateRepositoryImpl @Inject constructor() : SessionStateRepository {

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    override val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    private val _lastEndReason = MutableStateFlow<SessionEndReason?>(null)
    override val lastEndReason: StateFlow<SessionEndReason?> = _lastEndReason.asStateFlow()

    override fun startSession(sessionType: SessionType, studentUid: String) {
        _activeSession.value = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            sessionType = sessionType,
            studentUid = studentUid,
            startedAtMillis = System.currentTimeMillis(),
            plannedDurationMinutes = when (sessionType) {
                is SessionType.Timed -> sessionType.durationMinutes
                SessionType.OpenEnded -> 0
            },
        )
        _lastEndReason.value = null
    }

    override fun endSession(reason: SessionEndReason) {
        _activeSession.value = null
        _lastEndReason.value = reason
    }

    override fun clearLastEndReason() {
        _lastEndReason.value = null
    }
}
