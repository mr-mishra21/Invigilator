/**
 * test-write-consent.ts
 *
 * Integration smoke-test for the onConsentCreate Cloud Function.
 *
 * What it does:
 *   1. Writes a consent document with a deliberately bad consentTextHash.
 *   2. Polls Firestore for up to 30 s waiting for the onConsentCreate trigger
 *      to set both `signedAt` (server timestamp) and `invalidated: true`.
 *   3. Deletes the test document.
 *   4. Prints PASS / FAIL and exits with 0 / 1.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json \
 *     npx ts-node scripts/test-write-consent.ts
 *
 * Prerequisites:
 *   - A service-account JSON with Firestore read/write access.
 *   - firebase-admin in node_modules (run `npm install` inside backend/functions).
 *   - npx ts-node available globally (`npm install -g ts-node typescript`).
 */

import * as admin from "firebase-admin";
import * as fs from "fs";
import * as path from "path";

// ── Init ─────────────────────────────────────────────────────────────────────

const FIREBASERC_PATH = path.join(__dirname, "..", "backend", ".firebaserc");
const firebaserc = JSON.parse(fs.readFileSync(FIREBASERC_PATH, "utf-8"));
const PROJECT_ID: string = firebaserc.projects.default;

admin.initializeApp({ projectId: PROJECT_ID });
const db = admin.firestore();

// ── Config ────────────────────────────────────────────────────────────────────

const TEST_DOC_ID = `smoke-test-${Date.now()}`;
const POLL_INTERVAL_MS = 2_000;
const TIMEOUT_MS = 30_000;

// ── Helpers ───────────────────────────────────────────────────────────────────

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function cleanup(ref: admin.firestore.DocumentReference): Promise<void> {
  try {
    await ref.delete();
    console.log(`  Cleaned up test document: consents/${TEST_DOC_ID}`);
  } catch (e) {
    console.warn(`  Warning: could not delete test document: ${e}`);
  }
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main(): Promise<void> {
  const ref = db.collection("consents").doc(TEST_DOC_ID);

  console.log(`Project     : ${PROJECT_ID}`);
  console.log(`Test doc    : consents/${TEST_DOC_ID}`);
  console.log(`Timeout     : ${TIMEOUT_MS / 1000} s`);
  console.log("");

  // Step 1: write consent with deliberately wrong hash
  const badHash = "0000000000000000000000000000000000000000000000000000000000000000";
  await ref.set({
    uid: "smoke-test-uid",
    consentType: "AdultStudentSelfConsent",
    consentVersion: "v1.0",
    language: "en",
    consentTextHash: badHash,
    // signedAt intentionally omitted — trigger must set it
  });
  console.log(`Wrote consent document with bad hash: ${badHash}`);
  console.log("Polling for onConsentCreate to process...");
  console.log("");

  // Step 2: poll for signedAt + invalidated: true
  const deadline = Date.now() + TIMEOUT_MS;
  let pass = false;

  while (Date.now() < deadline) {
    await sleep(POLL_INTERVAL_MS);
    const snap = await ref.get();
    if (!snap.exists) {
      console.log("  Document no longer exists (unexpected). Aborting.");
      break;
    }
    const data = snap.data()!;
    const hasSignedAt = data.signedAt !== undefined && data.signedAt !== null;
    const isInvalidated = data.invalidated === true;

    process.stdout.write(
      `  signedAt=${hasSignedAt ? "✓" : "✗"}  invalidated=${isInvalidated ? "✓" : "✗"}\n`
    );

    if (hasSignedAt && isInvalidated) {
      pass = true;
      break;
    }
  }

  // Step 3: clean up
  await cleanup(ref);
  console.log("");

  if (pass) {
    console.log("PASS — onConsentCreate set signedAt and invalidated:true for bad hash");
    process.exit(0);
  } else {
    console.log(
      "FAIL — onConsentCreate did not set both signedAt and invalidated:true within " +
        `${TIMEOUT_MS / 1000} s`
    );
    console.log("       Check that functions are deployed and the trigger is active.");
    process.exit(1);
  }
}

main().catch((err) => {
  console.error("FAIL — unhandled error:", err);
  process.exit(1);
});
