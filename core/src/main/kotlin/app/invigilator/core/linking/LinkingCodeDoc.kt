package app.invigilator.core.linking

import com.google.firebase.Timestamp

/** Mirror of /linkingCodes/{code}. The code itself is also the Firestore document ID. */
data class LinkingCodeDoc(
    val code: String = "",
    val studentUid: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val expiresAt: Timestamp = Timestamp.now(),
    val claimedBy: String? = null,
    val claimedAt: Timestamp? = null,
)
