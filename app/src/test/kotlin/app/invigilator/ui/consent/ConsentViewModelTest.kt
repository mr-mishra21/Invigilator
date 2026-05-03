package app.invigilator.ui.consent

import androidx.lifecycle.SavedStateHandle
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.consent.ConsentDoc
import app.invigilator.core.consent.ConsentRepository
import app.invigilator.core.consent.ConsentTextResult
import app.invigilator.core.consent.ConsentType
import app.invigilator.core.user.UserRepository
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConsentViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val consentRepository: ConsentRepository = mockk()
    private val authRepository: AuthRepository = mockk()
    private val userRepository: UserRepository = mockk()

    private fun savedState(
        type: String = ConsentType.ADULT_STUDENT_SELF.firestoreValue,
        studentUid: String = "",
    ) = SavedStateHandle(mapOf("type" to type, "studentUid" to studentUid))

    private fun viewModel(
        type: String = ConsentType.ADULT_STUDENT_SELF.firestoreValue,
        studentUid: String = "",
    ) = ConsentViewModel(consentRepository, authRepository, userRepository, savedState(type, studentUid))

    private fun stubText(type: ConsentType = ConsentType.ADULT_STUDENT_SELF) {
        every {
            consentRepository.getConsentText(type, any())
        } returns ConsentTextResult(text = "Consent text body", hash = "abc123")
    }

    @Test
    fun `signature required — canAgree is false when signature is empty`() = runTest {
        stubText()
        val vm = viewModel()
        vm.onEvent(ConsentEvent.ScrolledToEnd)
        // signature is still blank
        assertFalse(vm.uiState.value.canAgree)
    }

    @Test
    fun `scroll required — canAgree is false until scrolled to end`() = runTest {
        stubText()
        val vm = viewModel()
        vm.onEvent(ConsentEvent.SignatureChanged("Test User"))
        // hasScrolledToEnd is still false
        assertFalse(vm.uiState.value.canAgree)
    }

    @Test
    fun `canAgree becomes true when scrolled to end AND signature non-empty`() = runTest {
        stubText()
        val vm = viewModel()
        vm.onEvent(ConsentEvent.ScrolledToEnd)
        vm.onEvent(ConsentEvent.SignatureChanged("Test User"))
        assertTrue(vm.uiState.value.canAgree)
    }

    @Test
    fun `consent completes on success path`() = runTest {
        stubText()
        every { authRepository.currentUserId } returns "uid-test"
        coEvery { consentRepository.recordConsent(any<ConsentDoc>()) } returns Result.success("consent-123")
        every { consentRepository.awaitServerTimestamp("consent-123") } returns flowOf(true)
        coEvery { userRepository.activateAccount(any(), any()) } returns Result.success(Unit)

        val vm = viewModel()
        vm.onEvent(ConsentEvent.ScrolledToEnd)
        vm.onEvent(ConsentEvent.SignatureChanged("Test User"))
        vm.onEvent(ConsentEvent.AgreedTapped)

        assertTrue(vm.uiState.value.isComplete)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `consent timeout shows error and clears loading`() {
        // UnconfinedTestDispatcher ignores delays, so use StandardTestDispatcher here
        // so withTimeoutOrNull is virtualized and advanceTimeBy works.
        val testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        runTest(testDispatcher) {
            stubText()
            every { authRepository.currentUserId } returns "uid-test"
            coEvery { consentRepository.recordConsent(any<ConsentDoc>()) } returns Result.success("consent-timeout")
            // Suspends indefinitely — simulates trigger not firing before timeout
            every { consentRepository.awaitServerTimestamp("consent-timeout") } returns flow<Boolean> { awaitCancellation() }
            coEvery { consentRepository.withdrawConsent("consent-timeout", "uid-test") } returns Result.success(Unit)

            val vm = viewModel()
            vm.onEvent(ConsentEvent.ScrolledToEnd)
            vm.onEvent(ConsentEvent.SignatureChanged("Test User"))
            vm.onEvent(ConsentEvent.AgreedTapped)

            // Run the launched coroutine until it suspends inside withTimeoutOrNull
            runCurrent()
            // Advance past the 30-second timeout
            advanceTimeBy(31_000)
            // Let the cleanup (withdrawConsent + state update) complete
            runCurrent()

            assertNotNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.isComplete)
        }
    }

    @Test
    fun `recordConsent failure shows error`() = runTest {
        stubText()
        every { authRepository.currentUserId } returns "uid-test"
        coEvery { consentRepository.recordConsent(any<ConsentDoc>()) } returns Result.failure(RuntimeException("network"))

        val vm = viewModel()
        vm.onEvent(ConsentEvent.ScrolledToEnd)
        vm.onEvent(ConsentEvent.SignatureChanged("Test User"))
        vm.onEvent(ConsentEvent.AgreedTapped)

        assertNotNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.isComplete)
    }

    @Test
    fun `ParentForMinorConsent records student uid as subjectUid, not parent uid`() = runTest {
        every {
            consentRepository.getConsentText(ConsentType.PARENT_FOR_MINOR, any())
        } returns ConsentTextResult(text = "Parent consent text", hash = "hash-pfm")
        every { authRepository.currentUserId } returns "uid-parent"

        val docSlot = slot<ConsentDoc>()
        coEvery { consentRepository.recordConsent(capture(docSlot)) } returns Result.success("consent-pfm")
        every { consentRepository.awaitServerTimestamp("consent-pfm") } returns flowOf(true)

        val vm = viewModel(
            type = ConsentType.PARENT_FOR_MINOR.firestoreValue,
            studentUid = "uid-student",
        )
        vm.onEvent(ConsentEvent.ScrolledToEnd)
        vm.onEvent(ConsentEvent.SignatureChanged("Parent Name"))
        vm.onEvent(ConsentEvent.AgreedTapped)

        assertEquals("uid-parent", docSlot.captured.consenterUid)
        assertEquals("uid-student", docSlot.captured.subjectUid)
    }

    @Test
    fun `language change reloads consent text`() = runTest {
        every {
            consentRepository.getConsentText(ConsentType.ADULT_STUDENT_SELF, "en")
        } returns ConsentTextResult(text = "English text", hash = "hash-en")
        every {
            consentRepository.getConsentText(ConsentType.ADULT_STUDENT_SELF, "hi")
        } returns ConsentTextResult(text = "Hindi text", hash = "hash-hi")

        val vm = viewModel()
        assertEquals("English text", vm.uiState.value.consentText)

        vm.onEvent(ConsentEvent.LanguageChanged("hi"))
        assertEquals("Hindi text", vm.uiState.value.consentText)
        assertEquals("hi", vm.uiState.value.language)
    }
}
