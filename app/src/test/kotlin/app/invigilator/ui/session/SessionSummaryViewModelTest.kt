package app.invigilator.ui.session

import androidx.lifecycle.SavedStateHandle
import app.invigilator.core.session.DistractionRecord
import app.invigilator.core.session.SessionDoc
import app.invigilator.core.session.SessionSummaryRepository
import app.invigilator.core.util.AppNameResolver
import app.invigilator.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sessionId = "session-abc"
    private val studentUid = "student-123"

    private fun savedState() = SavedStateHandle(
        mapOf("sessionId" to sessionId, "studentUid" to studentUid)
    )

    private fun fakeRepo(sessions: List<SessionDoc>): SessionSummaryRepository =
        object : SessionSummaryRepository {
            override fun saveSession(doc: SessionDoc) {}
            override fun observeRecentSessions(studentUid: String, limit: Int): Flow<List<SessionDoc>> =
                flowOf(sessions)
        }

    private fun fakeAppNameResolver(mapping: Map<String, String> = emptyMap()): AppNameResolver {
        val resolver = mockk<AppNameResolver>()
        every { resolver.resolveDisplayName(any()) } answers {
            mapping[firstArg()] ?: firstArg<String>().substringAfterLast('.').replaceFirstChar { it.uppercaseChar() }
        }
        return resolver
    }

    private fun makeDoc(distractionCount: Int, distractions: List<DistractionRecord> = emptyList()) =
        SessionDoc(
            sessionId = sessionId,
            studentUid = studentUid,
            durationSeconds = 1500L,
            distractionCount = distractionCount,
            distractions = distractions,
        )

    @Test
    fun loading_state_shown_when_session_not_yet_in_observed_results() {
        val vm = SessionSummaryViewModel(savedState(), fakeRepo(emptyList()), fakeAppNameResolver())
        assertTrue(vm.state.value.loading)
    }

    @Test
    fun verdict_excellent_when_zero_distractions() {
        val vm = SessionSummaryViewModel(savedState(), fakeRepo(listOf(makeDoc(0))), fakeAppNameResolver())
        assertFalse(vm.state.value.loading)
        assertEquals(Verdict.EXCELLENT, vm.state.value.verdict)
    }

    @Test
    fun verdict_good_when_one_or_two() {
        val vm1 = SessionSummaryViewModel(savedState(), fakeRepo(listOf(makeDoc(1))), fakeAppNameResolver())
        assertEquals(Verdict.GOOD, vm1.state.value.verdict)

        val vm2 = SessionSummaryViewModel(savedState(), fakeRepo(listOf(makeDoc(2))), fakeAppNameResolver())
        assertEquals(Verdict.GOOD, vm2.state.value.verdict)
    }

    @Test
    fun verdict_some_when_three_to_five() {
        listOf(3, 4, 5).forEach { count ->
            val vm = SessionSummaryViewModel(savedState(), fakeRepo(listOf(makeDoc(count))), fakeAppNameResolver())
            assertEquals("expected SOME for count=$count", Verdict.SOME, vm.state.value.verdict)
        }
    }

    @Test
    fun verdict_lots_when_six_or_more() {
        listOf(6, 10, 99).forEach { count ->
            val vm = SessionSummaryViewModel(savedState(), fakeRepo(listOf(makeDoc(count))), fakeAppNameResolver())
            assertEquals("expected LOTS for count=$count", Verdict.LOTS, vm.state.value.verdict)
        }
    }

    @Test
    fun breakdown_resolves_app_names() {
        val distractions = listOf(
            DistractionRecord(packageName = "com.google.android.youtube", category = "DISTRACTING", dwellSeconds = 60),
            DistractionRecord(packageName = "com.instagram.android", category = "DISTRACTING", dwellSeconds = 45),
        )
        val nameMap = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.instagram.android" to "Instagram",
        )
        val vm = SessionSummaryViewModel(
            savedState(),
            fakeRepo(listOf(makeDoc(2, distractions))),
            fakeAppNameResolver(nameMap),
        )

        val breakdown = vm.state.value.breakdown
        assertEquals(2, breakdown.size)
        assertEquals("YouTube", breakdown[0].displayName)
        assertEquals(60L, breakdown[0].dwellSeconds)
        assertEquals("Instagram", breakdown[1].displayName)
        assertEquals(45L, breakdown[1].dwellSeconds)
    }
}
