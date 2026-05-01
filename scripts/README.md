# Scripts

Utility scripts for the Invigilator project. Run all scripts from the **repository root**.

---

## `sync-consent-hashes.sh`

Computes SHA-256 of every consent asset file in
`core/src/main/assets/consents/` and rewrites
`backend/functions/src/consentHashes.ts` with the new values.

**Run after:** editing any consent text file or bumping a consent version in
`ConsentVersions.kt`. Then redeploy Cloud Functions.

```bash
# Regenerate and write consentHashes.ts
scripts/sync-consent-hashes.sh

# Only check for drift (CI mode) — exits 1 if hashes are out of sync, no file written
scripts/sync-consent-hashes.sh --check
```

The Android build's `verifyConsentHashes` Gradle task handles the Kotlin-side
`ConsentHashes.kt` automatically; this script only updates the Cloud Functions side.

---

## `smoke-test-functions.sh`

Verifies that deployed Cloud Functions are reachable and returning
application-level errors (not HTTP 5xx/network errors).

**Test:** calls `claimLinkingCode` with code `"000000"` (never exists) and asserts
the response is a Firebase `NOT_FOUND` error.

**Prerequisites:**
- Functions must already be deployed to Firebase (`firebase deploy --only functions`
  inside `backend/`).
- `curl` must be installed.

```bash
scripts/smoke-test-functions.sh
```

Exits `0` on PASS, `1` on FAIL.

---

## `test-write-consent.ts`

Integration smoke-test for the `onConsentCreate` Cloud Function trigger.

**What it does:**
1. Writes a `/consents/{id}` document with a deliberately bad `consentTextHash`.
2. Polls Firestore for up to 30 s waiting for the trigger to set `signedAt` and
   `invalidated: true`.
3. Deletes the test document.
4. Prints PASS / FAIL.

**Prerequisites:**
- Functions must be deployed (`onConsentCreate` trigger must be active).
- A Firebase **service account** JSON with Firestore read/write permissions.  
  **Never commit service-account JSON files.** Add them to `.gitignore`.
- `ts-node` and `typescript` installed globally:
  ```bash
  npm install -g ts-node typescript
  ```
- `firebase-admin` available in `backend/functions/node_modules`:
  ```bash
  cd backend/functions && npm install
  ```

```bash
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json \
  npx ts-node scripts/test-write-consent.ts
```

Exits `0` on PASS, `1` on FAIL.

### Security note

Service-account JSON files grant broad access to your Firebase project.  
Verify `service-account*.json` and `*-credentials.json` appear in your root
`.gitignore` before running this script:

```bash
grep -E 'service.account|credentials' .gitignore
```

---

## Adding new scripts

- Place scripts in this directory.
- Add an entry to this README.
- Make shell scripts executable: `chmod +x scripts/your-script.sh`.
- Prefer explicit `set -euo pipefail` in bash scripts.
