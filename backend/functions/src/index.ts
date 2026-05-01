import * as admin from "firebase-admin";

admin.initializeApp();

export { onConsentCreate } from "./onConsentCreate";
export { claimLinkingCode } from "./claimLinkingCode";

// TODO Sprint 3: onStudentActivated — send parent a push notification
// when their student's account becomes active
