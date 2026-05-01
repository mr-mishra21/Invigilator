package app.invigilator.core.user

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Creates the /users/{uid} document on first sign-in. */
    suspend fun createUser(user: UserDoc): Result<Unit>

    /** Returns the current user doc, or null if not found. */
    suspend fun getUser(uid: String): Result<UserDoc?>

    /** Streams the user doc for real-time updates (used to detect accountStatus changes). */
    fun observeUser(uid: String): Flow<UserDoc?>

    /** Updates the accountStatus field only. */
    suspend fun updateAccountStatus(uid: String, status: AccountStatus): Result<Unit>

    /**
     * Sets accountStatus = "active" and optionally writes parentUid.
     * Used after consent is recorded.
     */
    suspend fun activateAccount(uid: String, parentUid: String? = null): Result<Unit>

    /** Adds a consent document reference to the user's consentRefs list. */
    suspend fun appendConsentRef(uid: String, consentPath: String): Result<Unit>
}
