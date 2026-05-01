package app.invigilator.core.user

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference

/**
 * Mirror of the /users/{uid} Firestore document. Default values are required
 * so Firestore's toObject<UserDoc>() can construct an instance via reflection.
 */
data class UserDoc(
    val uid: String = "",
    val role: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val dateOfBirth: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now(),
    val accountStatus: String = AccountStatus.PENDING_CONSENT.firestoreValue,
    val consentRefs: List<DocumentReference> = emptyList(),
    /** Populated when a minor student is linked to a parent. Null for parents and adult students. */
    val parentUid: String? = null,
)
