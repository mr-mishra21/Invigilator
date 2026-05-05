package app.invigilator.ui.onboarding

import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import app.invigilator.util.MainDispatcherRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val userRepository: UserRepository = mockk()
    private val mockFirebaseUser: FirebaseUser = mockk { every { uid } returns "uid" }
    private val mockFirebaseAuth: FirebaseAuth = mockk { every { currentUser } returns mockFirebaseUser }

    private fun viewModel() = OnboardingViewModel(userRepository, mockFirebaseAuth)

    @Test
    fun `role selected event updates state`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected("parent"))
        assertEquals("parent", vm.uiState.value.role)
    }

    @Test
    fun `dob selected event updates dobMillis`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.DobSelected(1_000_000L))
        assertEquals(1_000_000L, vm.uiState.value.dobMillis)
    }

    @Test
    fun `uid received event updates uid`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.UidReceived("uid-x"))
        assertEquals("uid-x", vm.uiState.value.uid)
    }

    @Test
    fun `name changed event updates name and clears error`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NameChanged("Aarav"))
        assertEquals("Aarav", vm.uiState.value.name)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `age computed correctly for 20-year-old`() = runTest {
        val vm = viewModel()
        val dob = LocalDate.now().minusYears(20)
        val millis = dob.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        vm.onEvent(OnboardingEvent.DobSelected(millis))
        assertEquals(20, vm.uiState.value.age)
        assertTrue(vm.uiState.value.isAdult)
    }

    @Test
    fun `age computed correctly for 15-year-old`() = runTest {
        val vm = viewModel()
        val dob = LocalDate.now().minusYears(15)
        val millis = dob.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        vm.onEvent(OnboardingEvent.DobSelected(millis))
        assertEquals(15, vm.uiState.value.age)
        assertTrue(!vm.uiState.value.isAdult)
    }

    @Test
    fun `submit name with blank name sets error`() = runTest {
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.NameChanged("   "))
        vm.onEvent(OnboardingEvent.SubmitName)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `submit name for adult student routes to adult consent`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(false)
        coEvery { userRepository.createUser(any()) } returns Result.success(Unit)
        val vm = viewModel()
        val dob = LocalDate.now().minusYears(20).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.STUDENT.firestoreValue))
        vm.onEvent(OnboardingEvent.DobSelected(dob))
        vm.onEvent(OnboardingEvent.NameChanged("Aarav"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertEquals(OnboardingDestination.ConsentAdultStudent, vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `submit name for minor student routes to share code`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(false)
        coEvery { userRepository.createUser(any()) } returns Result.success(Unit)
        val vm = viewModel()
        val dob = LocalDate.now().minusYears(15).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.STUDENT.firestoreValue))
        vm.onEvent(OnboardingEvent.DobSelected(dob))
        vm.onEvent(OnboardingEvent.NameChanged("Ritam"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertEquals(OnboardingDestination.StudentShareCode, vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `submit name for parent routes to parent ToS`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(false)
        coEvery { userRepository.createUser(any()) } returns Result.success(Unit)
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.PARENT.firestoreValue))
        vm.onEvent(OnboardingEvent.NameChanged("Priya"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertEquals(OnboardingDestination.ConsentParentToS, vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `submit name sets pending_consent account status in created doc`() = runTest {
        var capturedDoc: app.invigilator.core.user.UserDoc? = null
        coEvery { userRepository.userDocExists(any()) } returns Result.success(false)
        coEvery { userRepository.createUser(any()) } answers {
            capturedDoc = firstArg()
            Result.success(Unit)
        }
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.STUDENT.firestoreValue))
        vm.onEvent(OnboardingEvent.NameChanged("Test"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertEquals(AccountStatus.PENDING_CONSENT.firestoreValue, capturedDoc?.accountStatus)
    }

    @Test
    fun `createUser failure shows error`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(false)
        coEvery { userRepository.createUser(any()) } returns Result.failure(RuntimeException("db error"))
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.STUDENT.firestoreValue))
        vm.onEvent(OnboardingEvent.NameChanged("Test"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `existing user doc does not throw error and routes correctly`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(true)
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.PARENT.firestoreValue))
        vm.onEvent(OnboardingEvent.NameChanged("Priya"))
        vm.onEvent(OnboardingEvent.SubmitName)

        assertNull(vm.uiState.value.error)
        assertEquals(OnboardingDestination.ConsentParentToS, vm.uiState.value.nameSubmitDone)
    }

    @Test
    fun `createUser only called when doc does not exist`() = runTest {
        coEvery { userRepository.userDocExists(any()) } returns Result.success(true)
        val vm = viewModel()
        vm.onEvent(OnboardingEvent.RoleSelected(UserRole.STUDENT.firestoreValue))
        vm.onEvent(OnboardingEvent.NameChanged("Test"))
        vm.onEvent(OnboardingEvent.SubmitName)

        coVerify(exactly = 0) { userRepository.createUser(any()) }
    }
}
