import * as admin from "firebase-admin";

admin.initializeApp();

export { onConsentCreate } from "./onConsentCreate";
export { claimLinkingCode } from "./claimLinkingCode";
