package app.invigilator.ui.linking

import androidx.lifecycle.SavedStateHandle
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.linking.LinkingCodeDoc
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.core.user.UserDoc
import app.invigilator.core.user.UserRepository
import app.invigilator.util.MainDispatcherRule
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LinkingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val linkingRepository: LinkingRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val authRepository: AuthRepository = mockk()

    private fun viewModel() = LinkingViewModel(linkingRepository, userRepository, authRepository)

    private fun stubGenerate(code: String = "472913") {
        every { authRepository.currentUserId } returns "uid-student"
        coEvery { linkingRepository.generateCode("uid-student") } returns Result.success(code)
        every { linkingRepository.observeLinkingCode(code) } returns flowOf(null)
        every { userRepository.observeUser("uid-student") } returns flowOf(null)
    }

    @Test
    fun `generateCode is called on init and code is stored in state`() = runTest {
        stubGenerate("472913")
        val vm = viewModel()
        assertEquals("472913", vm.uiState.value.code)
        assertFalse(vm.uiState.value.isGenerating)
    }

    @Test
    fun `claimedBy populated — isClaimed becomes true`() = runTest {
        val code = "472913"
        val codeFlow = MutableSharedFlow<LinkingCodeDoc?>(replay = 1)
        every { authRepository.currentUserId } returns "uid-student"
        coEvery { linkingRepository.generateCode("uid-student") } returns Result.success(code)
        every { linkingRepository.observeLinkingCode(code) } returns codeFlow
        every { userRepository.observeUser("uid-student") } returns flowOf(null)

        val vm = viewModel()
        assertFalse(vm.uiState.value.isClaimed)

        codeFlow.emit(LinkingCodeDoc(code = code, claimedBy = "uid-parent"))
        assertTrue(vm.uiState.value.isClaimed)
    }

    @Test
    fun `accountStatus active — navigateToStudentHome becomes true`() = runTest {
        val code = "472913"
        val userFlow = MutableSharedFlow<UserDoc?>(replay = 1)
        every { authRepository.currentUserId } returns "uid-student"
        coEvery { linkingRepository.generateCode("uid-student") } returns Result.success(code)
        every { linkingRepository.observeLinkingCode(code) } returns flowOf(null)
        every { userRepository.observeUser("uid-student") } returns userFlow

        val vm = viewModel()
        assertFalse(vm.uiState.value.navigateToStudentHome)

        userFlow.emit(UserDoc(uid = "uid-student", accountStatus = "active"))
        assertTrue(vm.uiState.value.navigateToStudentHome)
    }

    @Test
    fun `getNewCode generates a second code replacing the first`() = runTest {
        val firstCode = "111111"
        val secondCode = "222222"
        every { authRepository.currentUserId } returns "uid-student"
        coEvery { linkingRepository.generateCode("uid-student") } returnsMany listOf(
            Result.success(firstCode),
            Result.success(secondCode),
        )
        every { linkingRepository.observeLinkingCode(any()) } returns flowOf(null)
        every { userRepository.observeUser("uid-student") } returns flowOf(null)

        val vm = viewModel()
        assertEquals(firstCode, vm.uiState.value.code)

        vm.getNewCode()
        assertEquals(secondCode, vm.uiState.value.code)
    }
}
