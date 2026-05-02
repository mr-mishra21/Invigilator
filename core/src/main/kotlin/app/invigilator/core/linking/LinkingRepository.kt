package app.invigilator.core.linking

import kotlinx.coroutines.flow.Flow

data class ClaimResult(
    val studentUid: String,
    val studentDisplayName: String,
    val studentDateOfBirthMillis: Long?,
)

interface LinkingRepository {
    /**
     * Generates a random 6-digit code, writes it to /linkingCodes/{code} with a 30-minute TTL,
     * and returns the code string (always exactly 6 digits, leading zeros preserved).
     */
    suspend fun generateCode(studentUid: String): Result<String>

    /**
     * Streams the linking code document so the student screen can react when a parent claims it.
     * Emits null if the doc has been deleted (expired/purged).
     */
    fun observeLinkingCode(code: String): Flow<LinkingCodeDoc?>

    /**
     * Streams the /users/{parentUid}/linkedStudents sub-collection in real time.
     */
    fun observeLinkedStudents(parentUid: String): Flow<List<app.invigilator.core.user.LinkedStudentDoc>>

    /**
     * Calls the `claimLinkingCode` Cloud Function as the authenticated parent.
     * Returns the student's display name and DOB so the parent can confirm identity.
     */
    suspend fun claimCode(code: String): Result<ClaimResult>

    /**
     * Calls the `completeLinking` Cloud Function. The function writes both the
     * linkedStudents subcollection document and activates the student's account
     * atomically with Admin SDK privileges.
     */
    suspend fun completeLinking(studentUid: String, consentId: String): Result<Unit>
}
