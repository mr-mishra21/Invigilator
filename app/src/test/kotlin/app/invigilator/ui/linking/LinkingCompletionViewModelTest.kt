package app.invigilator.ui.linking

import androidx.lifecycle.SavedStateHandle
import app.invigilator.core.linking.LinkingError
import app.invigilator.core.linking.LinkingRepository
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LinkingCompletionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val linkingRepository: LinkingRepository = mockk()

    private fun savedState(
        studentUid: String = "uid-student",
        consentId: String = "consent-abc",
    ) = SavedStateHandle(
        mapOf("studentUid" to studentUid, "studentDisplayName" to "Test Student", "consentId" to consentId),
    )

    private fun viewModel(
        studentUid: String = "uid-student",
        consentId: String = "consent-abc",
    ) = LinkingCompletionViewModel(linkingRepository, savedState(studentUid, consentId))

    @Test
    fun `completeLinking succeeds — navigateToParentHome becomes true`() = runTest {
        coEvery { linkingRepository.completeLinking("uid-student", "consent-abc") } returns Result.success(Unit)

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.navigateToParentHome)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `completeLinking SessionExpired — error message matches`() = runTest {
        coEvery { linkingRepository.completeLinking(any(), any()) } returns
            Result.failure(LinkingError.SessionExpired)

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.navigateToParentHome)
        assertEquals(
            "Your linking session has expired. Please ask the student for a new code and try again.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `completeLinking AlreadyLinked — error message matches`() = runTest {
        coEvery { linkingRepository.completeLinking(any(), any()) } returns
            Result.failure(LinkingError.AlreadyLinked)

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.navigateToParentHome)
        assertEquals(
            "This student is already linked to a parent. If this is wrong, contact support.",
            vm.uiState.value.error,
        )
    }

    @Test
    fun `completeLinking generic error — error message is generic`() = runTest {
        coEvery { linkingRepository.completeLinking(any(), any()) } returns
            Result.failure(RuntimeException("unexpected"))

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.navigateToParentHome)
        assertEquals("Could not complete linking. Please try again.", vm.uiState.value.error)
    }

    @Test
    fun `retry resets loading and retries completeLinking`() = runTest {
        coEvery { linkingRepository.completeLinking(any(), any()) } returnsMany listOf(
            Result.failure(RuntimeException("first attempt failed")),
            Result.success(Unit),
        )

        val vm = viewModel()
        // After first attempt: error state
        assertFalse(vm.uiState.value.navigateToParentHome)
        assertEquals("Could not complete linking. Please try again.", vm.uiState.value.error)

        vm.retry()
        // After retry success: navigate
        assertTrue(vm.uiState.value.navigateToParentHome)
        assertNull(vm.uiState.value.error)
    }
}
