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

    override fun startSession(sessionType: SessionType, studentUid: String) {
        _activeSession.value = ActiveSession(
            sessionId = UUID.randomUUID().toString(),
            sessionType = sessionType,
            studentUid = studentUid,
            startedAtMillis = System.currentTimeMillis(),
        )
    }

    override fun endSession() {
        _activeSession.value = null
    }
}
