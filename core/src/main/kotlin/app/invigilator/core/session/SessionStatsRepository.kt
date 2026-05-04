package app.invigilator.core.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks distraction events during the current session.
 * Cleared when the session ends.
 */
interface SessionStatsRepository {
    val stats: StateFlow<SessionStats>

    /** Record that a distraction event has completed (student left the distracting app). */
    fun recordDistractionEvent(event: DistractionEvent)

    /** Reset stats to zero. Called at session start and end. */
    fun reset()
}
