package app.invigilator.core.session

import kotlinx.coroutines.flow.StateFlow

data class ActiveSession(
    val sessionId: String,
    val sessionType: SessionType,
    val studentUid: String,
    val startedAtMillis: Long,
)

interface SessionStateRepository {
    /** Emits the current active session, or null if no session is active. */
    val activeSession: StateFlow<ActiveSession?>

    fun startSession(sessionType: SessionType, studentUid: String)
    fun endSession()
}
