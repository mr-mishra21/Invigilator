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
     * Calls the `claimLinkingCode` Cloud Function as the authenticated parent.
     * Returns the student's display name and DOB so the parent can confirm identity.
     */
    suspend fun claimCode(code: String): Result<ClaimResult>

    /**
     * Creates the /users/{parentUid}/linkedStudents/{studentUid} document after parent consent.
     */
    suspend fun createLinkedStudentRecord(
        parentUid: String,
        doc: app.invigilator.core.user.LinkedStudentDoc,
    ): Result<Unit>
}
