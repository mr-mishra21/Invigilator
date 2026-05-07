package app.invigilator.ui.settings

import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.intervention.AppLanguage
import app.invigilator.core.intervention.AppLanguageRepository
import app.invigilator.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val languageRepo: AppLanguageRepository = mockk()
    private val authRepo: AuthRepository = mockk()

    private fun viewModel(): SettingsViewModel {
        every { languageRepo.observeLanguage() } returns flowOf(AppLanguage.ENGLISH)
        coEvery { languageRepo.currentLanguage() } returns AppLanguage.ENGLISH
        coEvery { languageRepo.isTtsAvailable(any()) } returns true
        coEvery { languageRepo.setLanguage(any()) } returns Unit
        coEvery { authRepo.signOut() } returns Result.success(Unit)
        return SettingsViewModel(languageRepo, authRepo)
    }

    @Test
    fun initial_state_reflects_current_repository_value() {
        every { languageRepo.observeLanguage() } returns flowOf(AppLanguage.HINDI)
        coEvery { languageRepo.currentLanguage() } returns AppLanguage.HINDI
        coEvery { languageRepo.isTtsAvailable(any()) } returns true
        coEvery { authRepo.signOut() } returns Result.success(Unit)
        val vm = SettingsViewModel(languageRepo, authRepo)

        assertEquals(AppLanguage.HINDI, vm.state.value.currentLanguage)
    }

    @Test
    fun state_observes_language_repository_changes() {
        every { languageRepo.observeLanguage() } returns flowOf(AppLanguage.ASSAMESE)
        coEvery { languageRepo.currentLanguage() } returns AppLanguage.ASSAMESE
        coEvery { languageRepo.isTtsAvailable(any()) } returns true
        coEvery { authRepo.signOut() } returns Result.success(Unit)
        val vm = SettingsViewModel(languageRepo, authRepo)

        assertEquals(AppLanguage.ASSAMESE, vm.state.value.currentLanguage)
    }

    @Test
    fun setLanguage_calls_repository_with_chosen_language() {
        val vm = viewModel()
        vm.setLanguage(AppLanguage.BENGALI)

        coVerify { languageRepo.setLanguage(AppLanguage.BENGALI) }
    }

    @Test
    fun signOut_calls_authRepo_signOut() {
        val vm = viewModel()
        vm.signOut()

        coVerify { authRepo.signOut() }
    }
}
