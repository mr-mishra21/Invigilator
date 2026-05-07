package app.invigilator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.intervention.AppLanguage
import app.invigilator.core.intervention.AppLanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageRepo: AppLanguageRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = languageRepo.observeLanguage()
        .map { lang -> SettingsUiState(currentLanguage = lang) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            languageRepo.setLanguage(language)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepo.signOut()
        }
    }
}
