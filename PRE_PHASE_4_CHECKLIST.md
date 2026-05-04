# Pre-Phase-4 Checklist

Before we write or execute Phase 4, we need to close three gaps. Each gap has a clear "definition of done." Do not skip any of them — Phase 4 builds on top of all three.

This document mixes work *you* do (Firebase Console, real phone) with work *Claude Code* does (deploy functions, write smoke tests). Each task is clearly labeled.

---

## Group A — Verify Phase 1–3 is solid

### A.1 — Re-run all unit tests cleanly  *(YOU)*

In your VS Code terminal:

```bash
./gradlew clean
./gradlew test --rerun-tasks
```

**Definition of done:**
- `BUILD SUCCESSFUL`
- Output mentions zero failed tests across all modules
- The "15/16 with one warning" ambiguity from Phase 1 is resolved (either the warning is gone, or you can paste the exact warning text here so I can confirm it's harmless)

If anything fails, **stop here** and bring me the output. Do not move to Group B.

### A.2 — Confirm the lint is clean  *(YOU)*

```bash
./gradlew lint
```

**Definition of done:**
- Either zero issues, or only "informational" issues (no "error" or "warning" severity)
- If there are warnings, paste the lint report path (`app/build/reports/lint-results-debug.html`) — open it in a browser and tell me the count by severity

---

## Group B — Deploy and exercise Cloud Functions

### B.1 — Confirm the Blaze plan is active  *(YOU)*

Cloud Functions require the pay-as-you-go (Blaze) plan. Free tier has a generous quota (2M invocations/month free); you'll pay ₹0 in development. But the plan must be enabled.

1. Open Firebase Console → your `invigilator-78dc2` project.
2. Bottom-left, click "Upgrade" or "Modify plan."
3. Select **Blaze** and add a payment method. Set a **budget alert at ₹500/month** to be safe — this is a hard limit Firebase will warn you about, not a charge.

**Definition of done:** the Console shows "Plan: Blaze" in the project settings.

### B.2 — Initialize the Firebase Functions project locally  *(CLAUDE CODE — but check first)*

Open a terminal in the `invigilator/backend/functions` folder and run:

```bash
ls
cat package.json
```

**Definition of done:**
- The folder exists with a populated `package.json`, `tsconfig.json`, and `src/` directory
- If any are missing, paste the output here — Claude Code will need to redo Phase 2's backend scaffold

### B.3 — Deploy the Cloud Functions  *(YOU, with Claude Code on standby)*

```bash
cd backend/functions
npm install               # first time only
npm run build             # if there's a build script; otherwise: npx tsc
cd ../..
firebase deploy --only functions
```

This typically takes 2–5 minutes. The first deployment is the slowest because Firebase has to provision the function infrastructure.

**Common first-deploy errors and fixes:**

| Error | Fix |
|---|---|
| `Error: HTTP Error: 403, Cloud Functions API has not been used` | Enable the Cloud Functions API in the [Google Cloud Console](https://console.cloud.google.com/apis/library/cloudfunctions.googleapis.com) for your `invigilator-78dc2` project, wait 1 minute, retry |
| `Error: Cloud Build API has not been used` | Same fix, but for the Cloud Build API |
| `Error: artifactregistry.googleapis.com has not been used` | Same fix, for Artifact Registry API |
| `Error: missing required permissions` | You may not be project owner; check IAM roles in Console |
| Functions deploy but show "v1" instead of "v2" | Check `firebase.json` — should specify `"runtime": "nodejs20"` |

If you hit any of these and the fix isn't obvious, paste the full error here.

**Definition of done:**
- Console output ends with `Deploy complete!`
- Firebase Console → Functions tab shows `onConsentCreate` and `claimLinkingCode` listed, both with status "active" and region `asia-south1`

### B.4 — Smoke-test the Cloud Functions  *(CLAUDE CODE)*

This is a small new piece of work for Claude Code: a one-shot script that calls `claimLinkingCode` with bogus data and verifies the function responds with the expected error. We're not testing correctness — we're testing that the function is *reachable* and *behaves at all*.

The prompt for Claude Code is at the bottom of this document under "Claude Code prompt for B.4 + B.5."

**Definition of done:**
- A new file `scripts/smoke-test-functions.sh` exists
- Running it produces output proving `claimLinkingCode` is reachable (e.g. responds with `not-found` for a fake code) — not a 404 from the network layer
- Running it also produces a `/consents/{testId}` write that triggers `onConsentCreate`, which we observe in the Functions log

### B.5 — Set up a CI guard for consent hash drift  *(CLAUDE CODE)*

The single-direction hash-sync failure mode I flagged earlier needs a fix. Specifically: if someone edits a `.txt` consent file and forgets to re-run `scripts/sync-consent-hashes.sh`, the Android side will regenerate but `backend/functions/src/consentHashes.ts` will be stale, and production will silently break.

The guard is a CI check that:
1. Runs `scripts/sync-consent-hashes.sh` in dry-run mode
2. Compares the freshly-computed hashes against the committed `consentHashes.ts`
3. Fails the build if they differ

This is a 30-line addition to `.github/workflows/android-ci.yml`. Prompt for Claude Code is below.

**Definition of done:**
- New CI step exists and passes against current code
- Manually corrupting `consentHashes.ts` causes the CI step to fail (Claude Code should test this, then revert)

---

## Group C — Test OTP signup end-to-end on a real phone

### C.1 — Add test phone numbers to Firebase  *(YOU)*

Skip if already done.

1. Firebase Console → Authentication → Sign-in method → Phone
2. Scroll to "Phone numbers for testing"
3. Add three numbers:
   - `+91 99999 99991` → code `123456`
   - `+91 99999 99992` → code `654321`
   - `+91 99999 99993` → code `111111`
4. Save

**Definition of done:** all three test numbers are listed in the Console.

### C.2 — Verify the SHA-1 fingerprint is registered  *(YOU)*

```bash
./gradlew signingReport
```

Look for the `debug` variant. Copy the SHA-1 line.

Then in Firebase Console:
1. Project settings (gear icon, top-left) → "Your apps"
2. Click on the Android app `app.invigilator`
3. Scroll to "SHA certificate fingerprints"
4. If your SHA-1 is not listed, click "Add fingerprint" and paste it
5. **Re-download `google-services.json`** and replace `app/google-services.json` (this step is easy to forget — the existing JSON may not include the new fingerprint)

**Definition of done:** the SHA-1 you saw in `signingReport` appears in the Firebase Console's fingerprints list, and the latest `google-services.json` is in `app/`.

### C.3 — Run a full signup flow on your real phone  *(YOU)*

Connect your Android phone via USB with USB debugging enabled. Then:

```bash
./gradlew installDebug
```

Open the app on your phone and walk through this sequence. **Have Firebase Console → Firestore → Data tab open in your browser at the same time so you can watch documents appear.**

1. Tap "I am a Student"
2. Pick a date of birth that makes you under 18 (e.g. `2010-01-01`) — we want the minor branch
3. Phone: `9999999991` (the formatter should normalize to `+919999999991`)
4. OTP: `123456`
5. Name: `Test Student`
6. Should land on the "Phase 4 not yet implemented" placeholder

While you do this, watch Firestore. You should see:

| Step | Firestore change |
|---|---|
| OTP verified | (no Firestore write — auth only) |
| Name submitted | New document at `/users/{uid}` with `role: "student"`, `accountStatus: "pending_consent"`, `dateOfBirth`, `displayName: "Test Student"`, `phoneNumber: "+919999999991"` |

**Definition of done:**
- All five steps complete on your phone with no error toasts
- The `/users/{uid}` document exists in Firestore with all the right fields
- Logout works (clears auth, returns to RoleSelect)
- Re-launch app → SplashScreen routes you correctly (signed-in but pending → onboarding resume; signed-out → role select)

If anything fails, **bring me a screenshot or the exact error.** Don't try to debug it alone — Phase 3 is fresh enough that Claude Code can help fix it before we forget the context.

### C.4 — Repeat the same flow with the *adult* branch and the *parent* branch  *(YOU)*

We need to confirm all three onboarding paths work, not just one.

| Test run | Role | DOB | Test phone | Expected `/users/{uid}` doc |
|---|---|---|---|---|
| 1 | Student | 2010-01-01 (minor) | +91 99999 99991 | `role: "student"`, `dateOfBirth: 2010-01-01`, `accountStatus: "pending_consent"` |
| 2 | Student | 2000-01-01 (adult) | +91 99999 99992 | `role: "student"`, `dateOfBirth: 2000-01-01`, `accountStatus: "pending_consent"` |
| 3 | Parent | (no DOB asked) | +91 99999 99993 | `role: "parent"`, `dateOfBirth: null`, `accountStatus: "pending_consent"` |

After each run, **delete the test user from Firebase Console** (Auth → Users → delete; Firestore → /users → delete the doc) before the next run. Otherwise the second run will fail with "user already exists."

**Definition of done:** all three runs land on the placeholder home screen and produce the expected Firestore document. Adult vs minor distinction is correctly captured in `dateOfBirth`.

---

## After all three groups are done

Bring me back:
- ✅ confirmation that A, B, C are all green
- The output of `firebase functions:log --limit 20` after running the smoke test
- A screenshot or text dump of the three `/users/{uid}` documents from C.4

I'll then write the Phase 4 spec and prompt.

---

## Claude Code prompt for B.4 + B.5

Paste the block below as a *single message* to Claude Code. This is a smaller session than Phase 3 — should be 30–60 minutes.

```
Pre-flight work before Sprint 2 Phase 4. Read PRE_PHASE_4_CHECKLIST.md
in the project root for context. Do exactly the work in groups B.4 and
B.5 of that checklist — nothing else. Do not start Phase 4.

═══════════════════════════════════════════════════════════════════
TASK 1 — Smoke test for Cloud Functions (group B.4)
═══════════════════════════════════════════════════════════════════

1.1 Create scripts/smoke-test-functions.sh — a bash script that:
    - Loads the Firebase project ID from .firebaserc
    - Uses `firebase functions:shell` OR `curl` against the functions
      callable endpoint (your choice; pick whichever is more reliable)
    - Calls claimLinkingCode with code "000000" (a code that does not
      exist) and asserts the response is a `not-found` error, NOT a
      404/500/network error. The point is to prove the function is
      reachable.
    - Prints PASS or FAIL, exits with appropriate status code

1.2 Add a small Node/TypeScript helper at scripts/test-write-consent.ts
    that, given a Firebase service-account JSON path:
    - Authenticates as admin
    - Writes a fake /consents/{id} document with consentTextHash =
      a known-bad value
    - Polls the document for 30 seconds
    - Asserts onConsentCreate stamped `signedAt` AND set
      `invalidated: true` (because the hash is bad)
    - Cleans up the test document
    - Prints PASS or FAIL

1.3 Update README in scripts/ to document how to run both, including
    the prerequisite: a service-account JSON downloaded from
    Firebase Console → Project Settings → Service accounts →
    Generate new private key. Make it clear this file MUST be in
    .gitignore (it should be already; verify).

1.4 Run both scripts yourself to confirm they work. If they don't,
    fix them. If they fail because functions aren't deployed yet,
    print a clear "Functions not deployed — deploy first" message
    rather than a cryptic error.

═══════════════════════════════════════════════════════════════════
TASK 2 — CI guard for consent hash drift (group B.5)
═══════════════════════════════════════════════════════════════════

2.1 Modify scripts/sync-consent-hashes.sh to support a `--check`
    flag. When `--check` is passed, the script computes the hashes
    but does NOT write the file; instead it diffs against the
    existing backend/functions/src/consentHashes.ts and exits 1
    if they differ.

2.2 Add a step to .github/workflows/android-ci.yml that runs
    `bash scripts/sync-consent-hashes.sh --check` on every push and
    PR. Place it BEFORE the Android build steps so it fails fast.

2.3 Test it locally:
    - Run the CI step → should pass
    - Manually corrupt one entry in consentHashes.ts → run the
      step → confirm it exits 1 with a clear message about which
      consent file's hash drifted
    - Revert the corruption → run again → confirm it passes

2.4 Commit as "chore(ci): guard against consent hash drift between
    Android and Cloud Functions"

═══════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════

- Do not commit any service-account JSON.
- Do not deploy functions yourself — that's the human's job.
- Do not start Phase 4. If you finish both tasks early, stop and
  print a summary.
- If the existing `scripts/sync-consent-hashes.sh` doesn't have the
  structure to support `--check` cleanly, refactor it — but keep
  backward compatibility with calling it without arguments.

When done, print:
  - what you built
  - any deviations and why
  - any errors you hit and how you fixed them
  - confirmation that smoke tests work and CI guard works
```
