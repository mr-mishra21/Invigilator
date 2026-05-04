package app.invigilator.core.session

import kotlinx.coroutines.flow.StateFlow

data class ActiveSession(
    val sessionId: String,
    val sessionType: SessionType,
    val studentUid: String,
    val startedAtMillis: Long,
)

enum class SessionEndReason {
    USER_ENDED,
    TIMER_EXPIRED,
}

interface SessionStateRepository {
    /** Emits the current active session, or null if no session is active. */
    val activeSession: StateFlow<ActiveSession?>

    /**
     * Set when a session ended. Cleared when a new session starts.
     * Used by Route composables to know how to navigate after end.
     */
    val lastEndReason: StateFlow<SessionEndReason?>

    fun startSession(sessionType: SessionType, studentUid: String)
    fun endSession(reason: SessionEndReason)
    fun clearLastEndReason()
}
