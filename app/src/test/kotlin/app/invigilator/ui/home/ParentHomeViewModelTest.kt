package app.invigilator.ui.home

import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.session.DistractionRecord
import app.invigilator.core.session.SessionDoc
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.user.LinkedStudentDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.util.MainDispatcherRule
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ParentHomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val parentUid = "parent-1"
    private val studentUid = "student-1"
    private val studentDoc = LinkedStudentDoc(studentUid = studentUid, studentDisplayName = "Aarav")

    private fun mockAuth() = mockk<AuthRepository> {
        every { currentUserId } returns parentUid
        coEvery { signOut() } returns Result.success(Unit)
    }

    private fun mockLinking(students: List<LinkedStudentDoc>) = mockk<LinkingRepository> {
        every { observeLinkedStudents(parentUid) } returns flowOf(students)
    }

    private fun mockUser(status: String = "active") = mockk<UserRepository> {
        val userDoc = app.invigilator.core.user.UserDoc(accountStatus = status)
        coEvery { getUser(any()) } returns Result.success(userDoc)
    }

    private fun fakeSummaryRepo(sessions: List<SessionDoc>): SessionSummaryRepository =
        object : SessionSummaryRepository {
            override fun saveSession(doc: SessionDoc) {}
            override fun observeRecentSessions(studentUid: String, limit: Int): Flow<List<SessionDoc>> =
                flowOf(sessions)
        }

    private fun makeSession(
        endedAtMillis: Long,
        durationSeconds: Long = 1500,
        nudgeCount: Int = 0,
        nagCount: Int = 0,
    ) = SessionDoc(
        sessionId = "sid-${endedAtMillis}",
        studentUid = studentUid,
        endedAt = Timestamp(endedAtMillis / 1000, 0),
        durationSeconds = durationSeconds,
        nudgeCount = nudgeCount,
        nagCount = nagCount,
    )

    private fun makeVm(sessions: List<SessionDoc>) = ParentHomeViewModel(
        linkingRepository = mockLinking(listOf(studentDoc)),
        userRepository = mockUser(),
        authRepository = mockAuth(),
        sessionSummaryRepo = fakeSummaryRepo(sessions),
    )

    @Test
    fun linkedStudentRow_shows_zero_sessions_when_history_empty() {
        val vm = makeVm(emptyList())

        val rows = vm.uiState.value.linkedStudents
        assertEquals(1, rows.size)
        assertEquals(0, rows[0].sessionsToday)
        assertNull(rows[0].lastSessionMinutesAgo)
    }

    @Test
    fun linkedStudentRow_counts_only_today() {
        val nowMs = System.currentTimeMillis()
        // One session today, one session 2 days ago
        val twoDaysAgoMs = nowMs - (2 * 24 * 60 * 60 * 1000L)
        val sessions = listOf(
            makeSession(nowMs),
            makeSession(twoDaysAgoMs),
        )

        val vm = makeVm(sessions)

        val row = vm.uiState.value.linkedStudents[0]
        assertEquals(1, row.sessionsToday)
    }

    @Test
    fun linkedStudentRow_computes_minutes_ago_correctly() {
        val nowMs = System.currentTimeMillis()
        val tenMinutesAgoMs = nowMs - (10 * 60 * 1000L)
        val sessions = listOf(makeSession(tenMinutesAgoMs))

        val vm = makeVm(sessions)

        val minutesAgo = vm.uiState.value.linkedStudents[0].lastSessionMinutesAgo
        // Allow ±1 minute for test execution time
        assertEquals(10.0, minutesAgo!!.toDouble(), 1.0)
    }

    @Test
    fun linkedStudentRow_sums_nudgeCount_across_today_sessions() {
        val nowMs = System.currentTimeMillis()
        val sessions = listOf(
            makeSession(nowMs - 1_000, nudgeCount = 2),
            makeSession(nowMs - 2_000, nudgeCount = 1),
            makeSession(nowMs - 3_000, nudgeCount = 0),
        )

        val vm = makeVm(sessions)

        assertEquals(3, vm.uiState.value.linkedStudents[0].nudgesToday)
    }

    @Test
    fun linkedStudentRow_sums_nagCount_across_today_sessions() {
        val nowMs = System.currentTimeMillis()
        val sessions = listOf(
            makeSession(nowMs - 1_000, nagCount = 1),
            makeSession(nowMs - 2_000, nagCount = 1),
        )

        val vm = makeVm(sessions)

        assertEquals(2, vm.uiState.value.linkedStudents[0].nagsToday)
    }

    @Test
    fun linkedStudentRow_excludes_sessions_from_other_days() {
        val nowMs = System.currentTimeMillis()
        val twoDaysAgoMs = nowMs - (2 * 24 * 60 * 60 * 1000L)
        val sessions = listOf(
            makeSession(nowMs - 1_000, nudgeCount = 2),
            makeSession(twoDaysAgoMs, nudgeCount = 5),
        )

        val vm = makeVm(sessions)

        assertEquals(2, vm.uiState.value.linkedStudents[0].nudgesToday)
    }

    @Test
    fun linkedStudentRow_with_no_sessions_today_returns_zero_interventions() {
        val twoDaysAgoMs = System.currentTimeMillis() - (2 * 24 * 60 * 60 * 1000L)
        val sessions = listOf(makeSession(twoDaysAgoMs, nudgeCount = 3, nagCount = 1))

        val vm = makeVm(sessions)

        val row = vm.uiState.value.linkedStudents[0]
        assertEquals(0, row.nudgesToday)
        assertEquals(0, row.nagsToday)
    }

    @Test
    fun linkedStudentRow_handles_old_session_docs_without_intervention_fields() {
        val nowMs = System.currentTimeMillis()
        // Old session docs default nudgeCount=0, nagCount=0 — should not crash
        val sessions = listOf(makeSession(nowMs - 1_000))

        val vm = makeVm(sessions)

        val row = vm.uiState.value.linkedStudents[0]
        assertEquals(0, row.nudgesToday)
        assertEquals(0, row.nagsToday)
    }
}
