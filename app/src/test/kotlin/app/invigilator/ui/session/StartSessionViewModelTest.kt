package app.invigilator.ui.session

import app.invigilator.core.intervention.AppLanguage
import app.invigilator.core.intervention.AppLanguageRepository
import app.invigilator.core.intervention.NudgePhraseLibrary
import app.invigilator.core.intervention.TtsManager
import app.invigilator.core.session.SessionType
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val languageRepo: AppLanguageRepository = mockk {
        coEvery { isTtsAvailable(any()) } returns false
    }
    private val nudgePhraseLibrary: NudgePhraseLibrary = NudgePhraseLibrary()

    private fun viewModel() = StartSessionViewModel(ttsManager, languageRepo, nudgePhraseLibrary)

    @Test
    fun `default mode is TIMED with 25 minute duration`() {
        val vm = viewModel()

        assertEquals(SessionMode.TIMED, vm.state.value.mode)
        assertEquals(25, vm.state.value.selectedDurationMinutes)
    }

    @Test
    fun `mode change to OPEN updates state`() {
        val vm = viewModel()

        vm.onEvent(StartSessionEvent.ModeChanged(SessionMode.OPEN))

        assertEquals(SessionMode.OPEN, vm.state.value.mode)
    }

    @Test
    fun `buildSessionType returns Timed with correct duration`() {
        val vm = viewModel()
        vm.onEvent(StartSessionEvent.DurationChanged(45))

        val result = vm.buildSessionType()

        assertTrue(result is SessionType.Timed)
        assertEquals(45, (result as SessionType.Timed).durationMinutes)
    }

    @Test
    fun `buildSessionType returns OpenEnded when mode is OPEN`() {
        val vm = viewModel()
        vm.onEvent(StartSessionEvent.ModeChanged(SessionMode.OPEN))

        val result = vm.buildSessionType()

        assertTrue(result is SessionType.OpenEnded)
    }
}
