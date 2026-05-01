package app.invigilator.ui.auth

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.invigilator.core.auth.AuthError
import app.invigilator.core.auth.AuthRepository
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()

    private fun savedState() = SavedStateHandle(
        mapOf("role" to "student", "phone" to "+919876543210")
    )

    private fun viewModel() = OtpEntryViewModel(authRepository, savedState())

    @Test
    fun `otp changed updates state`() = runTest {
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        assertEquals("123456", vm.uiState.value.otp)
    }

    @Test
    fun `otp changed clears error`() = runTest {
        val vm = viewModel()
        coEvery { authRepository.verifyOtp(any()) } returns Result.failure(AuthError.InvalidOtp)
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        vm.onEvent(OtpEntryEvent.OtpChanged("654321"))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `successful verification sets verifiedUid`() = runTest {
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertEquals("uid-abc", vm.uiState.value.verifiedUid)
    }

    @Test
    fun `wrong OTP shows error and clears otp field`() = runTest {
        coEvery { authRepository.verifyOtp("000000") } returns Result.failure(AuthError.InvalidOtp)
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("000000"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertNotNull(vm.uiState.value.error)
        assertEquals("", vm.uiState.value.otp)
    }

    @Test
    fun `resend timer counts down to zero`() = runTest {
        val vm = viewModel()
        assertEquals(60, vm.uiState.value.resendSecondsRemaining)
        advanceTimeBy(61_000L)
        assertEquals(0, vm.uiState.value.resendSecondsRemaining)
    }

    @Test
    fun `resend restarts timer after success`() = runTest {
        coEvery { authRepository.sendOtp("+919876543210") } returns Result.success(Unit)
        val vm = viewModel()
        advanceTimeBy(61_000L)
        assertEquals(0, vm.uiState.value.resendSecondsRemaining)
        vm.onEvent(OtpEntryEvent.ResendOtp)
        assertEquals(OtpEntryViewModel.RESEND_TIMEOUT_SECONDS, vm.uiState.value.resendSecondsRemaining)
    }

    @Test
    fun `submit ignored when otp shorter than 6 digits`() = runTest {
        coEvery { authRepository.verifyOtp(any()) } returns Result.success("uid")
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("123"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertNull(vm.uiState.value.verifiedUid)
    }

    @Test
    fun `clearNavigationFlag resets verifiedUid`() = runTest {
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        vm.clearNavigationFlag()
        assertNull(vm.uiState.value.verifiedUid)
    }
}
