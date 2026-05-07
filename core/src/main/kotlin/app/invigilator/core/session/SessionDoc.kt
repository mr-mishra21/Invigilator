package app.invigilator.core.session

import com.google.firebase.Timestamp

/**
 * Document persisted to /users/{studentUid}/sessions/{sessionId}
 * after a session ends.
 */
data class SessionDoc(
    val sessionId: String = "",
    val studentUid: String = "",
    val startedAt: Timestamp = Timestamp.now(),
    val endedAt: Timestamp = Timestamp.now(),
    val durationSeconds: Long = 0,
    val plannedDurationSeconds: Long = 0,    // 0 if OPEN_ENDED
    val sessionType: String = "",            // "TIMED" or "OPEN_ENDED"
    val endReason: String = "",              // "USER_ENDED" or "TIMER_EXPIRED"
    val distractionCount: Int = 0,
    val distractions: List<DistractionRecord> = emptyList(),
    val nudgeCount: Int = 0,
    val nagCount: Int = 0,
    val interventions: List<InterventionRecord> = emptyList(),
)

data class DistractionRecord(
    val packageName: String = "",
    val category: String = "",   // "DISTRACTING" or "UNKNOWN"
    val dwellSeconds: Long = 0,
)

data class InterventionRecord(
    val packageName: String = "",
    val type: String = "",               // "FIRST_NUDGE", "SECOND_NUDGE", "NAG"
    val atSecondsIntoSession: Long = 0,
)
