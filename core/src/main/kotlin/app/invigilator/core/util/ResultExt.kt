package app.invigilator.core.util

import app.invigilator.core.auth.AuthError
import app.invigilator.core.linking.LinkingError

/**
 * Converts a [Throwable] to a human-readable message for display in the UI.
 * ViewModels call this on the failure branch of a [Result].
 */
fun Throwable.toUserMessage(): String = when (this) {
    is AuthError -> message ?: "Authentication error"
    is LinkingError -> message ?: "Linking error"
    is IllegalArgumentException -> message ?: "Invalid input"
    else -> "Something went wrong. Please try again."
}
