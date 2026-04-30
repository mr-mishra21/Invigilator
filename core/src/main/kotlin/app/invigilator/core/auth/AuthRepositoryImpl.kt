package app.invigilator.core.auth

import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
internal class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val activityHolder: ActivityHolder,
) : AuthRepository {

    // Mutable state shared between sendOtp and verifyOtp within one sign-in session.
    @Volatile private var verificationId: String? = null
    @Volatile private var forceResendToken: PhoneAuthProvider.ForceResendingToken? = null

    override suspend fun sendOtp(phoneE164: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val activity = activityHolder.get()
                ?: run {
                    cont.resume(Result.failure(AuthError.Unknown(IllegalStateException("No active Activity"))))
                    return@suspendCancellableCoroutine
                }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Silent/instant verification — safe to ignore here; verifyOtp handles it.
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    cont.resume(Result.failure(e.toAuthError()))
                }

                override fun onCodeSent(
                    id: String,
                    token: PhoneAuthProvider.ForceResendingToken,
                ) {
                    verificationId = id
                    forceResendToken = token
                    cont.resume(Result.success(Unit))
                }
            }

            val optionsBuilder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            forceResendToken?.let { optionsBuilder.setForceResendingToken(it) }

            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        }

    override suspend fun verifyOtp(otp: String): Result<String> {
        val id = verificationId
            ?: return Result.failure(AuthError.Unknown(IllegalStateException("No OTP verification in progress")))
        return try {
            val credential = PhoneAuthProvider.getCredential(id, otp)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val uid = result.user?.uid
                ?: return Result.failure(AuthError.Unknown(Exception("Firebase returned no UID")))
            Result.success(uid)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(AuthError.InvalidOtp)
        } catch (e: Exception) {
            Result.failure(e.toAuthError())
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        firebaseAuth.signOut()
    }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it.toAuthError()) })

    override val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(
                if (auth.currentUser != null) AuthState.SignedIn(auth.currentUser!!.uid)
                else AuthState.SignedOut
            )
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is AuthError -> this
        is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidOtp
        else -> AuthError.Unknown(this)
    }

    private fun FirebaseException.toAuthError(): AuthError {
        val msg = message ?: ""
        return when {
            msg.contains("quota", ignoreCase = true) -> AuthError.QuotaExceeded
            msg.contains("network", ignoreCase = true) -> AuthError.Network(this)
            msg.contains("invalid", ignoreCase = true) -> AuthError.InvalidPhone
            else -> AuthError.Unknown(this)
        }
    }
}
