import * as admin from "firebase-admin";
import * as functions from "firebase-functions/v1";
import { HttpsError } from "firebase-functions/v1/https";

const db = admin.firestore();

/**
 * HTTPS callable: parent calls this to claim a student's 6-digit linking code.
 *
 * Input:  { code: string }  — the 6-digit code
 * Output: { studentUid, studentDisplayName, studentDateOfBirthMillis? }
 *
 * The function runs inside a Firestore transaction so two parents cannot
 * claim the same code concurrently. On success it marks the code as claimed
 * (server timestamp) and returns enough student info for the parent confirm screen.
 */
export const claimLinkingCode = functions
  .region("asia-south1")
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new HttpsError("unauthenticated", "Must be signed in to claim a linking code.");
    }

    const { code } = data as { code?: unknown };
    if (typeof code !== "string" || !/^\d{6}$/.test(code)) {
      throw new HttpsError("invalid-argument", "A linking code must be exactly 6 digits.");
    }

    const parentUid = context.auth.uid;
    const codeRef = db.collection("linkingCodes").doc(code);

    return db.runTransaction(async (tx) => {
      const codeSnap = await tx.get(codeRef);

      if (!codeSnap.exists) {
        throw new HttpsError("not-found", "Code not found — check the digits with your student.");
      }

      const codeData = codeSnap.data()!;

      if (codeData.claimedBy != null) {
        throw new HttpsError("already-exists", "This student is already linked to a parent.");
      }

      if (codeData.expiresAt.toMillis() < Date.now()) {
        throw new HttpsError("deadline-exceeded", "This code has expired. Ask your student for a new one.");
      }

      // Prevent a student from claiming their own code
      if (codeData.studentUid === parentUid) {
        throw new HttpsError("permission-denied", "A student cannot claim their own linking code.");
      }

      const studentRef = db.collection("users").doc(codeData.studentUid);
      const studentSnap = await tx.get(studentRef);

      if (!studentSnap.exists) {
        throw new HttpsError("not-found", "Student account not found.");
      }

      tx.update(codeRef, {
        claimedBy: parentUid,
        claimedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      const studentData = studentSnap.data()!;
      const dob: admin.firestore.Timestamp | null = studentData.dateOfBirth ?? null;

      return {
        studentUid: codeData.studentUid,
        studentDisplayName: studentData.displayName as string,
        studentDateOfBirthMillis: dob ? dob.toMillis() : null,
      };
    });
  });
