package app.invigilator.ui.auth

import androidx.lifecycle.SavedStateHandle
import app.invigilator.core.auth.AuthError
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import app.invigilator.ui.nav.AuthFlow
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
    private val userRepository: UserRepository = mockk()

    private fun savedState(flow: AuthFlow = AuthFlow.NEW_USER) = SavedStateHandle(
        mapOf("flow" to flow.name, "phoneE164" to "+919876543210")
    )

    private fun viewModel(flow: AuthFlow = AuthFlow.NEW_USER) =
        OtpEntryViewModel(authRepository, userRepository, savedState(flow))

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
        val vm = viewModel()
        vm.onEvent(OtpEntryEvent.OtpChanged("123"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertNull(vm.uiState.value.nextDestination)
    }

    // ── New-user flow ──────────────────────────────────────────────────────────

    @Test
    fun `new_user flow routes to ProceedToCreateUser after otp success`() = runTest {
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        val vm = viewModel(AuthFlow.NEW_USER)
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertEquals(OtpDestination.ProceedToCreateUser, vm.uiState.value.nextDestination)
    }

    // ── Sign-in flow ───────────────────────────────────────────────────────────

    @Test
    fun `sign_in flow with active user routes to GoToHome`() = runTest {
        val userDoc = UserDoc(
            uid = "uid-abc",
            role = UserRole.STUDENT.firestoreValue,
            accountStatus = AccountStatus.ACTIVE.firestoreValue,
        )
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        coEvery { userRepository.getUser("uid-abc") } returns Result.success(userDoc)
        val vm = viewModel(AuthFlow.SIGN_IN)
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        val dest = vm.uiState.value.nextDestination
        assertTrue(dest is OtpDestination.GoToHome)
        assertEquals(UserRole.STUDENT, (dest as OtpDestination.GoToHome).role)
    }

    @Test
    fun `sign_in flow with pending_consent user routes to ResumeConsent`() = runTest {
        val userDoc = UserDoc(
            uid = "uid-abc",
            role = UserRole.STUDENT.firestoreValue,
            accountStatus = AccountStatus.PENDING_CONSENT.firestoreValue,
        )
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        coEvery { userRepository.getUser("uid-abc") } returns Result.success(userDoc)
        val vm = viewModel(AuthFlow.SIGN_IN)
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertTrue(vm.uiState.value.nextDestination is OtpDestination.ResumeConsent)
    }

    @Test
    fun `sign_in flow with no user doc signs out and shows error`() = runTest {
        coEvery { authRepository.verifyOtp("123456") } returns Result.success("uid-abc")
        coEvery { userRepository.getUser("uid-abc") } returns Result.success(null)
        coEvery { authRepository.signOut() } returns Result.success(Unit)
        val vm = viewModel(AuthFlow.SIGN_IN)
        vm.onEvent(OtpEntryEvent.OtpChanged("123456"))
        vm.onEvent(OtpEntryEvent.Submit)
        assertEquals(OtpDestination.UnknownNumber, vm.uiState.value.nextDestination)
        assertNotNull(vm.uiState.value.error)
    }
}
