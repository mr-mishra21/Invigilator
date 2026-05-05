
package app.invigilator.ui.session

import app.invigilator.core.session.ActiveSession
import app.invigilator.core.session.SessionStateRepository
import app.invigilator.core.session.SessionType
import app.invigilator.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionActiveViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val activeSessionFlow = MutableStateFlow<ActiveSession?>(null)
    private val sessionState: SessionStateRepository = mockk {
        every { activeSession } returns activeSessionFlow
    }

    private fun makeSession(startedAtMillis: Long = System.currentTimeMillis()) =
        ActiveSession(
            sessionId = "test-id",
            sessionType = SessionType.Timed(25),
            studentUid = "student-1",
            startedAtMillis = startedAtMillis,
            plannedDurationMinutes = 25,
        )

    @Test
    fun state_isActive_false_when_no_active_session() {
        val viewModel = SessionActiveViewModel(sessionState)

        assertFalse(viewModel.state.value.isActive)
        assertEquals(0L, viewModel.state.value.elapsedSeconds)
    }

    @Test
    fun state_isActive_true_when_session_present() {
        activeSessionFlow.value = makeSession()
        val viewModel = SessionActiveViewModel(sessionState)

        assertTrue(viewModel.state.value.isActive)
    }

    @Test
    fun state_isActive_flips_back_to_false_when_session_ends() {
        activeSessionFlow.value = makeSession()
        val viewModel = SessionActiveViewModel(sessionState)
        assertTrue(viewModel.state.value.isActive)

        activeSessionFlow.value = null

        assertFalse(viewModel.state.value.isActive)
    }
}