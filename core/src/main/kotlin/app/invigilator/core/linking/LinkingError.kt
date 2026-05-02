package app.invigilator.core.linking

sealed class LinkingError(message: String) : Exception(message) {
    data object CodeNotFound : LinkingError("Code not found — check the digits with your student.")
    data object CodeExpired : LinkingError("This code has expired. Ask your student for a new one.")
    data object CodeAlreadyClaimed : LinkingError("This student is already linked to a parent.")
    data object InvalidCodeFormat : LinkingError("A linking code must be exactly 6 digits.")
    data object NotSignedIn : LinkingError("Not signed in.")
    data object BadRequest : LinkingError("Invalid request.")
    data object SessionExpired : LinkingError("Linking session expired.")
    data object AlreadyLinked : LinkingError("Student already linked.")
    data object NotAuthorized : LinkingError("Not authorized.")
    data class Network(override val cause: Throwable) : LinkingError("Network problem")
    data class Unknown(override val cause: Throwable) : LinkingError("Unexpected error")
}
