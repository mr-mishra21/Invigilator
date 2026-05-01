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

class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()

    // UnconfinedTestDispatcher runs init eagerly — read .value directly after construction.
    private fun viewModel() = SplashViewModel(authRepository, userRepository)

    @Test
    fun `signed out routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedOut)
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    @Test
    fun `signed in active parent routes to parent home`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid1"))
        coEvery { userRepository.getUser("uid1") } returns Result.success(
            userDoc(UserRole.PARENT, AccountStatus.ACTIVE)
        )
        assertEquals(SplashDestination.ParentHome, viewModel().uiState.value.destination)
    }

    @Test
    fun `signed in active student routes to student home`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid2"))
        coEvery { userRepository.getUser("uid2") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.ACTIVE)
        )
        assertEquals(SplashDestination.StudentHome, viewModel().uiState.value.destination)
    }

    @Test
    fun `signed in pending consent routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid3"))
        coEvery { userRepository.getUser("uid3") } returns Result.success(
            userDoc(UserRole.STUDENT, AccountStatus.PENDING_CONSENT)
        )
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    @Test
    fun `user fetch failure routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid4"))
        coEvery { userRepository.getUser("uid4") } returns Result.failure(RuntimeException("network"))
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    @Test
    fun `null user doc (incomplete signup) routes to onboarding`() = runTest {
        every { authRepository.authState } returns flowOf(AuthState.SignedIn("uid5"))
        coEvery { userRepository.getUser("uid5") } returns Result.success(null)
        assertEquals(SplashDestination.Onboarding, viewModel().uiState.value.destination)
    }

    private fun userDoc(role: UserRole, status: AccountStatus) = UserDoc(
        uid = "uid",
        role = role.firestoreValue,
        displayName = "Test",
        phoneNumber = "+919999999991",
        createdAt = Timestamp.now(),
        accountStatus = status.firestoreValue,
    )
}
