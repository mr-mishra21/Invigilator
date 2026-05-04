package app.invigilator.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionStateRepositoryImplTest {

    private lateinit var repository: SessionStateRepositoryImpl

    @Before
    fun setUp() {
        repository = SessionStateRepositoryImpl()
    }

    @Test
    fun startSession_setsActiveSession() {
        val sessionType = SessionType.Timed(25)
        val studentUid = "student-123"

        repository.startSession(sessionType, studentUid)

        val session = repository.activeSession.value
        assertNotNull(session)
        assertEquals(sessionType, session!!.sessionType)
        assertEquals(studentUid, session.studentUid)
        assertNotNull(session.sessionId)
    }

    @Test
    fun endSession_clearsActiveSession() {
        repository.startSession(SessionType.OpenEnded, "student-456")
        repository.endSession(SessionEndReason.USER_ENDED)

        assertNull(repository.activeSession.value)
    }

    @Test
    fun startSession_overwritesExisting() {
        repository.startSession(SessionType.Timed(15), "student-111")
        val firstId = repository.activeSession.value!!.sessionId

        repository.startSession(SessionType.Timed(45), "student-222")
        val second = repository.activeSession.value!!

        assertNotEquals(firstId, second.sessionId)
        assertEquals(SessionType.Timed(45), second.sessionType)
        assertEquals("student-222", second.studentUid)
    }

    @Test
    fun sessionId_isUnique_acrossCalls() {
        repository.startSession(SessionType.OpenEnded, "student-a")
        val id1 = repository.activeSession.value!!.sessionId

        repository.startSession(SessionType.OpenEnded, "student-a")
        val id2 = repository.activeSession.value!!.sessionId

        assertNotEquals(id1, id2)
    }

    @Test
    fun endSession_userEnded_sets_lastEndReason() {
        repository.startSession(SessionType.OpenEnded, "student-a")
        repository.endSession(SessionEndReason.USER_ENDED)

        assertEquals(SessionEndReason.USER_ENDED, repository.lastEndReason.value)
    }

    @Test
    fun endSession_timerExpired_sets_lastEndReason() {
        repository.startSession(SessionType.Timed(25), "student-b")
        repository.endSession(SessionEndReason.TIMER_EXPIRED)

        assertEquals(SessionEndReason.TIMER_EXPIRED, repository.lastEndReason.value)
    }

    @Test
    fun startSession_clears_lastEndReason() {
        repository.startSession(SessionType.OpenEnded, "student-a")
        repository.endSession(SessionEndReason.USER_ENDED)

        repository.startSession(SessionType.OpenEnded, "student-a")

        assertNull(repository.lastEndReason.value)
    }

    @Test
    fun clearLastEndReason_sets_to_null() {
        repository.startSession(SessionType.OpenEnded, "student-a")
        repository.endSession(SessionEndReason.TIMER_EXPIRED)

        repository.clearLastEndReason()

        assertNull(repository.lastEndReason.value)
    }
}
