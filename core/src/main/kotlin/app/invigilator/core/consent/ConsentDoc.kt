package app.invigilator.core.consent

import com.google.firebase.Timestamp

/**
 * Mirror of /consents/{consentId}. Fields [signedAt] and [ipHash] are
 * intentionally absent from client writes — they are set by the Cloud Function
 * `onConsentCreate`. See [toClientWriteMap] for the safe write payload.
 */
data class ConsentDoc(
    val consentId: String = "",
    val consentType: String = "",
    val consentVersion: String = "",
    val consentTextHash: String = "",
    val consentLanguage: String = "en",
    val consenterUid: String = "",
    val consenterRole: String = "",
    val subjectUid: String = "",
    val signatureText: String = "",
    /** Server-set by Cloud Function — never written by the client. */
    val signedAt: Timestamp? = null,
    val deviceFingerprint: String = "",
    /** Server-set by Cloud Function — never written by the client. */
    val ipHash: String? = null,
    val withdrawn: Boolean = false,
    val withdrawnAt: Timestamp? = null,
) {
    /**
     * Returns the map to pass to Firestore on create. Excludes [signedAt] and [ipHash]
     * so the security rule `!('signedAt' in request.resource.data.keys())` is satisfied.
     */
    fun toClientWriteMap(): Map<String, Any?> = mapOf(
        "consentId" to consentId,
        "consentType" to consentType,
        "consentVersion" to consentVersion,
        "consentTextHash" to consentTextHash,
        "consentLanguage" to consentLanguage,
        "consenterUid" to consenterUid,
        "consenterRole" to consenterRole,
        "subjectUid" to subjectUid,
        "signatureText" to signatureText,
        "deviceFingerprint" to deviceFingerprint,
        "withdrawn" to withdrawn,
    )
}
