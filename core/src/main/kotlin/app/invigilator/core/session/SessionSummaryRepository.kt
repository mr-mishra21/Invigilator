package app.invigilator.core.session

import kotlinx.coroutines.flow.Flow

interface SessionSummaryRepository {
    /**
     * Best-effort write of session summary to Firestore. Failures are logged
     * but do not throw. The caller should not await this — it returns
     * immediately and the write happens in the background.
     */
    fun saveSession(doc: SessionDoc)

    /**
     * Observe the most recent N sessions for a given student.
     * Used by SessionSummaryScreen and ParentHome.
     */
    fun observeRecentSessions(studentUid: String, limit: Int = 10): Flow<List<SessionDoc>>
}
