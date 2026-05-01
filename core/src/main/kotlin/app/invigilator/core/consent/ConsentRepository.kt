package app.invigilator.core.consent

import kotlinx.coroutines.flow.Flow

interface ConsentRepository {
    /**
     * Resolves [type] + current version + [language] to the consent text and its SHA-256 hash.
     * Falls back to "en" if [language] is not available.
     * Returns null if no asset is found for any language.
     */
    fun getConsentText(type: ConsentType, language: String): ConsentTextResult?

    /**
     * Writes the consent record to Firestore. Excludes server-set fields (signedAt, ipHash).
     * The Cloud Function `onConsentCreate` populates those fields and validates the hash.
     * Returns the consentId.
     */
    suspend fun recordConsent(doc: ConsentDoc): Result<String>

    /**
     * Returns a Flow that emits true once the Cloud Function has stamped signedAt on the
     * given consent document. The Flow never completes on its own — the caller must apply
     * a timeout (e.g. withTimeoutOrNull) and cancel the collection when done.
     */
    fun awaitServerTimestamp(consentId: String): Flow<Boolean>

    /** Marks a consent record as withdrawn. Does not delete — DPDP requires audit trail. */
    suspend fun withdrawConsent(consentId: String, uid: String): Result<Unit>
}
