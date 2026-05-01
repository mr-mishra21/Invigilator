package app.invigilator.ui.auth

import app.cash.turbine.test
import app.invigilator.core.auth.AuthError
import app.invigilator.core.auth.AuthRepository
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PhoneEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private fun viewModel() = PhoneEntryViewModel(authRepository)

    @Test
    fun `phone changed event updates state`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.PhoneChanged("9876543210"))
        assertEquals("9876543210", vm.uiState.value.phone)
    }

    @Test
    fun `phone changed clears existing error`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.Submit) // triggers invalid-phone error
        vm.onEvent(PhoneEntryEvent.PhoneChanged("9876543210"))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `invalid phone shows error without calling repository`() = runTest {
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.PhoneChanged("123"))
        vm.onEvent(PhoneEntryEvent.Submit)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `valid phone triggers sendOtp and sets normalizedPhone on success`() = runTest {
        coEvery { authRepository.sendOtp("+919876543210") } returns Result.success(Unit)
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.PhoneChanged("9876543210"))

        vm.uiState.test {
            skipItems(1) // initial
            vm.onEvent(PhoneEntryEvent.Submit)
            // loading=true, then normalizedPhone set
            val loaded = awaitItem()
            assertTrue(loaded.isLoading || loaded.normalizedPhone != null)
            val done = if (loaded.normalizedPhone != null) loaded else awaitItem()
            assertEquals("+919876543210", done.normalizedPhone)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sendOtp failure shows user-readable error`() = runTest {
        coEvery { authRepository.sendOtp("+919876543210") } returns Result.failure(AuthError.Network(RuntimeException()))
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.PhoneChanged("9876543210"))
        vm.onEvent(PhoneEntryEvent.Submit)

        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.normalizedPhone)
    }

    @Test
    fun `clearNavigationFlag resets normalizedPhone`() = runTest {
        coEvery { authRepository.sendOtp(any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onEvent(PhoneEntryEvent.PhoneChanged("9876543210"))
        vm.onEvent(PhoneEntryEvent.Submit)
        vm.clearNavigationFlag()
        assertNull(vm.uiState.value.normalizedPhone)
    }
}
