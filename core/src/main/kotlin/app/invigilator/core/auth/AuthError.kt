package app.invigilator.core.auth

sealed class AuthError(message: String) : Exception(message) {
    data object InvalidPhone : AuthError("Invalid phone number")
    data object InvalidOtp : AuthError("Wrong code")
    data object OtpExpired : AuthError("Code expired")
    data object QuotaExceeded : AuthError("Too many attempts. Try later.")
    data object UserAlreadyExists : AuthError("An account with this number already exists")
    data class Network(override val cause: Throwable) : AuthError("Network problem")
    data class Unknown(override val cause: Throwable) : AuthError("Unexpected error")
}
