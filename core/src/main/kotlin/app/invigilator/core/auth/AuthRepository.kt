package app.invigilator.core.auth

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Sends an OTP to [phoneE164]. Succeeds when Firebase confirms the code was dispatched. */
    suspend fun sendOtp(phoneE164: String): Result<Unit>

    /** Verifies the OTP entered by the user. Returns the Firebase UID on success. */
    suspend fun verifyOtp(otp: String): Result<String>

    suspend fun signOut(): Result<Unit>

    /** Emits the current auth state and then any subsequent changes. Never throws. */
    val authState: Flow<AuthState>
}
