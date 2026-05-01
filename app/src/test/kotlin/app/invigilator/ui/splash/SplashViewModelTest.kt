package app.invigilator.ui.splash

import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.auth.AuthState
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import app.invigilator.util.MainDispatcherRule
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()

    // UnconfinedTestDispatcher runs init eagerly — read .value directly after construction.
    private fun viewModel() = SplashViewModel(authRepository, userRepository)

    // ── Signed-out ─────────────────────────────────────────────────────────────

    @Test
    fun `signed out routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedOut)
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    // ── Active accounts ────────────────────────────────────────────────────────

    @Test
    fun `active parent routes to ParentHome`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid1"))
        coEvery { userRepository.getUser("uid1") } returns Result.success(
            userDoc(UserRole.PARENT, AccountStatus.ACTIVE)
        )
        assertEquals(SplashDestination.ParentHome, viewModel().uiState.value.destination)
    }

    @Test
    fun `active student routes to StudentHome`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid2"))
        coEvery { userRepository.getUser("uid2") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.ACTIVE, adultDob())
        )
        assertEquals(SplashDestination.StudentHome, viewModel().uiState.value.destination)
    }

    // ── Pending consent — the 3 new routing branches ──────────────────────────

    @Test
    fun `pending consent adult student routes to AdultStudentConsent`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid3"))
        coEvery { userRepository.getUser("uid3") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.PENDING_CONSENT, adultDob())
        )
        assertEquals(
            SplashDestination.AdultStudentConsent,
            viewModel().uiState.value.destination,
        )
    }

    @Test
    fun `pending consent minor student routes to StudentShareCodeResume`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid4"))
        coEvery { userRepository.getUser("uid4") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.PENDING_CONSENT, minorDob())
        )
        assertEquals(
            SplashDestination.StudentShareCodeResume,
            viewModel().uiState.value.destination,
        )
    }

    @Test
    fun `pending consent parent routes to ParentConsentResume`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid5"))
        coEvery { userRepository.getUser("uid5") } returns Result.success(
            userDoc(UserRole.PARENT, AccountStatus.PENDING_CONSENT)
        )
        assertEquals(
            SplashDestination.ParentConsentResume,
            viewModel().uiState.value.destination,
        )
    }

    // ── Suspended ─────────────────────────────────────────────────────────────

    @Test
    fun `suspended account routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid6"))
        coEvery { userRepository.getUser("uid6") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.SUSPENDED, adultDob())
        )
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    fun `user fetch failure routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid7"))
        coEvery { userRepository.getUser("uid7") } returns Result.failure(RuntimeException("network"))
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    @Test
    fun `null user doc (incomplete signup) routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid8"))
        coEvery { userRepository.getUser("uid8") } returns Result.success(null)
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun userDoc(
        role: UserRole,
        status: AccountStatus,
        dateOfBirth: Timestamp? = null,
    ) = UserDoc(
        uid = "uid",
        role = role.firestoreValue,
        displayName = "Test",
        phoneNumber = "+919999999991",
        dateOfBirth = dateOfBirth,
        createdAt = Timestamp.now(),
        accountStatus = status.firestoreValue,
    )

    /** 20 years ago — always an adult. */
    private fun adultDob() =
        Timestamp(Date(System.currentTimeMillis() - 20L * 365 * 24 * 3600 * 1000))

    /** 15 years ago — always a minor. */
    private fun minorDob() =
        Timestamp(Date(System.currentTimeMillis() - 15L * 365 * 24 * 3600 * 1000))
}
