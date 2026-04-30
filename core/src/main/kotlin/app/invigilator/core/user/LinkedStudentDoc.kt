package app.invigilator.core.user

import com.google.firebase.Timestamp

/** Mirror of /users/{parentUid}/linkedStudents/{studentUid}. */
data class LinkedStudentDoc(
    val studentUid: String = "",
    val studentDisplayName: String = "",
    val linkedAt: Timestamp = Timestamp.now(),
    val linkType: String = "",       // "parent_minor" | "parent_adult_optin"
    val consentRecordId: String = "",
)
