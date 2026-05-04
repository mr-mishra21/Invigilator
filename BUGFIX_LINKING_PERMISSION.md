# Bug Fix Session — Parent Linking PERMISSION_DENIED

This is a focused bug-fix session. Read the spec, do exactly the work described,
nothing more. Do not start Sprint 3.

═══════════════════════════════════════════════════════════════════════
THE BUG
═══════════════════════════════════════════════════════════════════════

When a parent signs the ParentForMinorConsent and the app tries to complete
the linking, two Firestore writes fail with PERMISSION_DENIED:

  1. Write to /users/{parentUid}/linkedStudents/{studentUid} fails because
     consentRecordId is blank — Phase 4's known TODO that was deferred.
  2. Write to /users/{studentUid} (to set accountStatus="active" and
     parentUid) fails because security rules forbid one user writing to
     another user's document.

Logcat shows both failures. The user sees "Failed to link the student.
Please try again." after a long pause following "I agree" on consent.

═══════════════════════════════════════════════════════════════════════
THE FIX (high-level)
═══════════════════════════════════════════════════════════════════════

Move linking completion from a client-side batch write to a Cloud Function
that runs with admin privileges and does both writes atomically. This is
the same architectural pattern as claimLinkingCode — consistent and secure.

Three changes:

  1. New Cloud Function: completeLinking(studentUid, consentId)
  2. Wire ConsentScreen to surface the consentId on completion (currently
     it returns Unit; needs to return the consentId so the caller can
     pass it to the linking step).
  3. LinkingCompletionViewModel calls completeLinking() instead of doing
     the batch write itself. Remove the rollback logic since the function
     is now atomic.

═══════════════════════════════════════════════════════════════════════
STEP 0 — Read the codebase first
═══════════════════════════════════════════════════════════════════════

Before changing anything, read:

  - SPRINT_2_SPEC.md sections 4 (Cloud Functions) and 6 (security rules)
  - SPRINT_2_PHASE_4_SPEC.md Part 5 (parent consent + batch write)
  - backend/functions/src/claimLinkingCode.ts (the existing pattern to follow)
  - core/src/main/kotlin/app/invigilator/core/linking/LinkingRepository.kt
  - core/src/main/kotlin/app/invigilator/core/linking/LinkingRepositoryImpl.kt
  - The current ConsentScreen, ConsentViewModel, ConsentRoute
  - The current LinkingCompletionViewModel and any callers
  - firestore.rules

Print a short summary of what you found before making changes:
  - Where the current batch write happens (file + function name)
  - How consentId currently flows out of ConsentScreen (or doesn't)
  - What rule(s) are blocking the writes

═══════════════════════════════════════════════════════════════════════
STEP 1 — Add the completeLinking Cloud Function
═══════════════════════════════════════════════════════════════════════

Create backend/functions/src/completeLinking.ts. Follow the pattern of
claimLinkingCode.ts exactly — same region, same error handling, same
import structure, same export style.

The function:

  - Auth check: must be called by an authenticated user (the parent)
  - Input validation: studentUid (string, non-empty), consentId (string,
    non-empty)
  - Verify a valid consent exists at /consents/{consentId} where:
      consenterUid == auth.uid (parent calling)
      subjectUid == studentUid
      consentType == "ParentForMinorConsent"
      signedAt is set (server has stamped it)
      invalidated != true
  - Verify the parent claimed a linking code for this student. Look up
    the most recent /linkingCodes/* doc with claimedBy == auth.uid AND
    studentUid == input studentUid. If none exists in the last hour,
    reject (HttpsError "failed-precondition", "Linking session expired").
  - Verify the student's user doc exists and has accountStatus="pending_consent".
    If status is already "active", reject (HttpsError "already-exists",
    "Student already linked").

  - Then perform a Firestore TRANSACTION (not just a batch — we want
    read-then-write atomicity):
      - Read /users/{studentUid} inside the transaction
      - If accountStatus is no longer "pending_consent" (race condition),
        abort with appropriate error
      - Write /users/{parentUid}/linkedStudents/{studentUid} with:
          studentUid
          studentDisplayName: from the read student doc
          linkedAt: serverTimestamp()
          linkType: "parent_minor"
          consentRecordId: the validated consentId
      - Update /users/{studentUid} with:
          accountStatus: "active"
          parentUid: auth.uid

  - Return: { success: true, studentUid, parentUid }

Add the function to backend/functions/src/index.ts exports.

Build and lint the TypeScript: `cd backend/functions && npm run build`.
Fix any errors before proceeding.

═══════════════════════════════════════════════════════════════════════
STEP 2 — Update Firestore security rules
═══════════════════════════════════════════════════════════════════════

The Cloud Function uses Admin SDK and bypasses rules — but we should
still tighten the rules so a malicious client can't do these writes
directly:

  - /users/{parentUid}/linkedStudents/{studentUid}: change `allow create`
    rule to `allow create: if false`. Only the Cloud Function (admin)
    creates these now.
  - /users/{uid} update rule: keep as-is. Adding a special exception for
    "parent updating student's accountStatus" would be brittle. The Cloud
    Function handles it.

Update firestore.rules. Print the diff. The human will deploy with
`firebase deploy --only firestore:rules` after verifying.

═══════════════════════════════════════════════════════════════════════
STEP 3 — Surface consentId from ConsentScreen
═══════════════════════════════════════════════════════════════════════

ConsentRepository.recordConsent() returns Result<String> where the string
is the consentId. Confirm this is true; if not, fix it.

In ConsentViewModel:
  - Add `consentId: String? = null` to ConsentUiState
  - When recordConsent succeeds, store the returned consentId in the state
  - When awaitServerTimestamp completes, set isComplete = true (this is
    likely already happening; just don't lose the consentId)

In ConsentRoute:
  - Change the `onComplete: () -> Unit` callback to
    `onComplete: (consentId: String) -> Unit`
  - When state.isComplete && state.consentId != null, invoke
    onComplete(state.consentId) in a LaunchedEffect

In InvigilatorNavHost (or wherever ConsentRoute is hosted), update the
parent-flow caller:
  - The PARENT_FOR_MINOR consent destination's onComplete now receives a
    consentId
  - Pass that consentId into the next step (LinkingCompletion route or
    direct call)

The other two consent types (ADULT_STUDENT_SELF and PARENT_TOS) don't
need the consentId — they activate the consenter's own account directly.
But the callback signature is now (String) -> Unit for consistency; the
adult-student and parent-tos callers can simply ignore the parameter.

═══════════════════════════════════════════════════════════════════════
STEP 4 — Replace LinkingCompletionViewModel batch write with the callable
═══════════════════════════════════════════════════════════════════════

In LinkingRepository (interface):
  - Remove `createLinkedStudentDoc()` and any direct Firestore mutation
    methods related to linking completion
  - Add: `suspend fun completeLinking(studentUid: String, consentId: String): Result<Unit>`

In LinkingRepositoryImpl:
  - Implement completeLinking() as a Cloud Function callable invocation
    (FirebaseFunctions.getInstance("asia-south1").getHttpsCallable("completeLinking"))
  - Map function errors to LinkingError sealed class:
      "unauthenticated" → LinkingError.NotSignedIn
      "invalid-argument" → LinkingError.BadRequest
      "failed-precondition" → LinkingError.SessionExpired
      "already-exists" → LinkingError.AlreadyLinked
      "permission-denied" → LinkingError.NotAuthorized
      anything else → LinkingError.Unknown

In LinkingCompletionViewModel:
  - Remove the entire batch-write + rollback logic
  - Replace with a single call to linkingRepository.completeLinking(
    studentUid, consentId)
  - On Result.Success → state.isComplete = true → caller navigates to
    ParentHome
  - On Result.Failure → map LinkingError to a user-friendly message,
    set state.error, allow retry

The user-facing error strings:
  - SessionExpired: "Your linking session has expired. Please ask the
    student for a new code and try again."
  - AlreadyLinked: "This student is already linked to a parent. If this
    is wrong, contact support."
  - NotSignedIn / NotAuthorized / Unknown: "Could not complete linking.
    Please try again." (generic)
  - BadRequest: same generic message

Add these to strings.xml in EN, AS, HI, BN.

═══════════════════════════════════════════════════════════════════════
STEP 5 — Clean up the existing orphan linkedStudent document
═══════════════════════════════════════════════════════════════════════

There is an orphan /users/{parentUid}/linkedStudents/{studentUid} doc
in Firestore from earlier broken test runs. The human will delete this
manually from the Firebase Console BEFORE testing. Add a note in the
session summary reminding them.

The specific orphan UIDs from the logs:
  parentUid: wfwfcJ1sDUXiNEwEDdvbZrhJOp72
  studentUid: vgL8qFF2DpQNXsviMRVZYbF7iQB2

Also instruct the human to:
  - Reset the student's accountStatus to "pending_consent" if it's not
    already (look at /users/vgL8qFF2DpQNXsviMRVZYbF7iQB2)
  - Clear parentUid on the same student doc

═══════════════════════════════════════════════════════════════════════
STEP 6 — Tests
═══════════════════════════════════════════════════════════════════════

Update the existing LinkingCompletionViewModel tests:
  - Remove tests that asserted batch-write behavior, rollback semantics
  - Add tests for the new flow:
      - completeLinking succeeds → isComplete=true
      - completeLinking returns SessionExpired → error message matches
      - completeLinking returns AlreadyLinked → error message matches
      - completeLinking returns generic error → error message is generic

Add a TypeScript test for the Cloud Function would be nice but is not
required for this fix. Skip if it adds significant time.

Run all tests: `./gradlew testDebugUnitTest`. Lint: `./gradlew lint`.
Both must pass.

═══════════════════════════════════════════════════════════════════════
STEP 7 — Commit and push
═══════════════════════════════════════════════════════════════════════

Single commit: "fix(linking): move parent linking completion to Cloud Function"

Then `git push origin main`. The push is mandatory — do not end the session
with unpushed commits. The previous OneDrive incident was caused by exactly
that lapse.

═══════════════════════════════════════════════════════════════════════
DEPLOYMENT — for the human, listed in summary
═══════════════════════════════════════════════════════════════════════

After commit, the human needs to:

  1. Delete the orphan linkedStudent doc in Firestore Console
  2. Reset the orphan student's accountStatus to pending_consent and
     clear parentUid
  3. Deploy rules: `firebase deploy --only firestore:rules`
  4. Deploy functions: `firebase deploy --only functions`
  5. Reinstall app on both phones: `./gradlew :app:installDebug`
  6. Re-test Flow B end to end with two fresh accounts

Print these instructions clearly in the final summary.

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- Do not change anything outside the linking + consent surfacing scope.
- Do not refactor unrelated code.
- Do not start Sprint 3 work.
- The push at the end is mandatory.
- If anything in this prompt seems wrong, say so before doing it.

When done, print:
  - Files modified (list)
  - Tests added/changed
  - Manual steps the human must take (the deployment + Firestore cleanup)
  - Any deviations from this prompt and why
  - Confirmation that git push succeeded
