package app.invigilator.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.invigilator.core.auth.AuthRepository
import app.invigilator.core.auth.AuthState
import app.invigilator.core.user.AccountStatus
import app.invigilator.core.user.UserRepository
import app.invigilator.core.user.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashDestination {
    data object Onboarding : SplashDestination
    data object ParentHome : SplashDestination
    data object StudentHome : SplashDestination
}

data class SplashUiState(
    val destination: SplashDestination? = null,
)

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val authState = authRepository.authState
                .filter { it !is AuthState.Loading }
                .first()

            val destination = when (authState) {
                is AuthState.SignedIn -> resolveSignedInDestination(authState.uid)
                is AuthState.SignedOut -> SplashDestination.Onboarding
                is AuthState.Loading -> SplashDestination.Onboarding
            }
            _uiState.update { it.copy(destination = destination) }
        }
    }

    private suspend fun resolveSignedInDestination(uid: String): SplashDestination {
        return userRepository.getUser(uid).fold(
            onSuccess = { doc ->
                when {
                    doc == null -> SplashDestination.Onboarding
                    doc.role == UserRole.PARENT.firestoreValue &&
                        doc.accountStatus == AccountStatus.ACTIVE.firestoreValue ->
                        SplashDestination.ParentHome

                    doc.role == UserRole.STUDENT.firestoreValue &&
                        doc.accountStatus == AccountStatus.ACTIVE.firestoreValue ->
                        SplashDestination.StudentHome

                    // pending_consent or incomplete signup → back to onboarding
                    else -> SplashDestination.Onboarding
                }
            },
            onFailure = { SplashDestination.Onboarding },
        )
    }
}
