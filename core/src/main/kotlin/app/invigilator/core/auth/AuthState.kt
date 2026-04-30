package app.invigilator.core.auth

sealed class AuthState {
    data object Loading : AuthState()
    data object SignedOut : AuthState()
    data class SignedIn(val uid: String) : AuthState()
}
