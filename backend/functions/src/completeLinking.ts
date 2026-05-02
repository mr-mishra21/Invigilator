import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v1";
import { HttpsError } from "firebase-functions/v1/https";

const db = admin.firestore();

/**
 * HTTPS callable: parent calls this after signing ParentForMinorConsent to complete
 * the minor-linking flow. Runs with Admin SDK so it can write to both the parent's
 * linkedStudents subcollection and the student's user doc atomically.
 *
 * Input:  { studentUid: string, consentId: string }
 * Output: { success: true, studentUid, parentUid }
 */
export const completeLinking = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in to complete linking.");
    }

    const { studentUid, consentId } = data as { studentUid?: unknown; consentId?: unknown };
    if (typeof studentUid !== "string" || studentUid.trim() === "") {
      throw new HttpsError("invalid-argument", "studentUid must be a non-empty string.");
    }
    if (typeof consentId !== "string" || consentId.trim() === "") {
      throw new HttpsError("invalid-argument", "consentId must be a non-empty string.");
    }

    const parentUid = context.auth.uid;

    // 1. Verify the consent record exists and is valid.
    const consentSnap = await db.collection("consents").doc(consentId).get();
    if (!consentSnap.exists) {
      throw new HttpsError("failed-precondition", "Consent record not found.");
    }
    const consentData = consentSnap.data()!;
    if (consentData.consenterUid !== parentUid) {
      throw new HttpsError("permission-denied", "Consent record does not belong to the caller.");
    }
    if (consentData.subjectUid !== studentUid) {
      throw new HttpsError("failed-precondition", "Consent record subject does not match studentUid.");
    }
    if (consentData.consentType !== "ParentForMinorConsent") {
      throw new HttpsError("failed-precondition", "Consent record is not a ParentForMinorConsent.");
    }
    if (!consentData.signedAt) {
      throw new HttpsError("failed-precondition", "Consent record has not been confirmed by the server yet.");
    }
    if (consentData.invalidated === true) {
      throw new HttpsError("failed-precondition", "Consent record has been invalidated.");
    }

    // 2. Verify the parent has a recent claimed linking code for this student (within the last hour).
    const oneHourAgo = admin.firestore.Timestamp.fromMillis(Date.now() - 60 * 60 * 1000);
    const codeQuery = await db.collection("linkingCodes")
      .where("claimedBy", "==", parentUid)
      .where("studentUid", "==", studentUid)
      .get();

    const recentCode = codeQuery.docs.find((doc) => {
      const claimedAt = doc.data().claimedAt as admin.firestore.Timestamp | null;
      return claimedAt != null && claimedAt.toMillis() >= oneHourAgo.toMillis();
    });

    if (!recentCode) {
      throw new HttpsError(
        "failed-precondition",
        "Linking session expired. Please ask the student for a new code and try again.",
      );
    }

    // 3. Verify student exists and is in a linkable state (outside transaction for a fast pre-check).
    const studentRef = db.collection("users").doc(studentUid);
    const preCheckSnap = await studentRef.get();
    if (!preCheckSnap.exists) {
      throw new HttpsError("not-found", "Student account not found.");
    }
    if (preCheckSnap.data()!.accountStatus === "active") {
      throw new HttpsError("already-exists", "Student already linked.");
    }

    // 4. Atomic transaction: read-then-write to avoid race conditions.
    return db.runTransaction(async (tx) => {
      const studentSnap = await tx.get(studentRef);

      if (!studentSnap.exists) {
        throw new HttpsError("not-found", "Student account not found.");
      }

      const studentData = studentSnap.data()!;
      if (studentData.accountStatus !== "pending_consent") {
        if (studentData.accountStatus === "active") {
          throw new HttpsError("already-exists", "Student already linked.");
        }
        throw new HttpsError(
          "failed-precondition",
          `Unexpected student accountStatus: ${studentData.accountStatus}`,
        );
      }

      const linkedStudentRef = db
        .collection("users").doc(parentUid)
        .collection("linkedStudents").doc(studentUid);

      tx.set(linkedStudentRef, {
        studentUid,
        studentDisplayName: (studentData.displayName as string) ?? "",
        linkedAt: admin.firestore.FieldValue.serverTimestamp(),
        linkType: "parent_minor",
        consentRecordId: consentId,
      });

      tx.update(studentRef, {
        accountStatus: "active",
        parentUid,
      });

      return { success: true, studentUid, parentUid };
    });
  });
