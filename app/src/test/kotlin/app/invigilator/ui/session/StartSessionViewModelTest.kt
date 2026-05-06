package app.invigilator.ui.session

import app.invigilator.core.session.SessionType
import app.invigilator.util.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartSessionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel() = StartSessionViewModel()

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
