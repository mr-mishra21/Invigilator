import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v1";
import { CONSENT_VERSION_HASHES } from "./consentHashes";

/**
 * Firestore trigger: runs whenever a new /consents/{consentId} document is created.
 *
 * Responsibilities:
 *   1. Write the trusted server timestamp for signedAt (client must omit this field).
 *   2. Validate that consentTextHash matches the known hash for that type+version+language.
 *      If it doesn't match, mark the record as invalidated and log with high severity.
 *
 * The ipHash field is intentionally NOT set here because Firestore triggers do not
 * expose the client's IP address. If IP logging is needed, switch consent creation
 * to an HTTPS callable that sets ipHash before writing the Firestore document.
 */
export const onConsentCreate = functions
  .region("asia-south1")
  .firestore.document("consents/{consentId}")
  .onCreate(async (snap, context) => {
    const data = snap.data();
    const consentId = context.params.consentId;

    const expectedHash =
      CONSENT_VERSION_HASHES[data.consentType]?.[data.consentVersion]?.[data.consentLanguage];

    const baseUpdates: Record<string, unknown> = {
      signedAt: admin.firestore.FieldValue.serverTimestamp(),
    };

    if (!expectedHash) {
      functions.logger.error("Consent hash lookup failed — unknown type/version/language", {
        consentId,
        consentType: data.consentType,
        consentVersion: data.consentVersion,
        consentLanguage: data.consentLanguage,
      });
      await snap.ref.update({
        ...baseUpdates,
        invalidated: true,
        invalidationReason: "unknown_type_version_language",
      });
      return;
    }

    if (expectedHash !== data.consentTextHash) {
      functions.logger.error("Consent text hash mismatch — possible tampered client", {
        consentId,
        consentType: data.consentType,
        consentVersion: data.consentVersion,
        consentLanguage: data.consentLanguage,
        expectedHash,
        receivedHash: data.consentTextHash,
      });
      await snap.ref.update({
        ...baseUpdates,
        invalidated: true,
        invalidationReason: "hash_mismatch",
      });
      return;
    }

    await snap.ref.update(baseUpdates);
    functions.logger.info("Consent record finalised", { consentId });
  });
