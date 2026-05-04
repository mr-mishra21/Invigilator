# Sprint 2 Phase 4 — Consent, Linking, and Account Activation

This is the final phase of Sprint 2. It closes the three known bugs from Phase 3,
then implements the complete consent and parent–student linking flow.

By the end of this phase, two people with phones can go from strangers to a
linked parent–student pair in under two minutes, with a legally-traceable
consent record in Firestore.

---

## Part 0 — Bug fixes (do these FIRST, before any new feature code)

These bugs were found in pre-flight testing. Fix them and commit before
proceeding to Part 1.

### Bug 1 — phoneNumber is empty in all /users/{uid} documents (CRITICAL)

**Root cause:** `UserRepositoryImpl.createUser()` is probably not reading the
authenticated user's phone number from `FirebaseAuth.getInstance().currentUser?.phoneNumber`
before writing the Firestore document.

**Fix:** In `UserRepositoryImpl`, after OTP verification, read the phone number
from `FirebaseAuth.currentUser.phoneNumber` (which Firebase Auth populates after
phone-OTP sign-in) and include it in the user document write.

The fix is 2–3 lines. If `currentUser?.phoneNumber` is null for some reason, log
a Timber warning and fall back to the phone string that was passed through the
onboarding flow — that phone string is E.164-formatted by `PhoneFormatter` and
stored in the nav argument chain.

**Verification:** After the fix, create a new test user and confirm `phoneNumber`
is populated (e.g. `"+919876543210"`) in Firestore. Not empty, not null.

### Bug 2 — No logout from placeholder home screens

**Fix:** Add a Settings icon (or a three-dot overflow menu) to both
`StudentHomeScreen` and `ParentHomeScreen` (even as placeholders). Tapping it
shows one option: "Log out". On confirm, call `AuthRepository.signOut()` and
navigate back to `RoleSelect`, clearing the back stack entirely so the user
cannot press back into the app.

Use `AlertDialog` for the logout confirm ("Log out?", "You'll need to sign in
again." → Cancel / Log out).

### Bug 3 — SplashScreen does not route correctly on re-launch

**Fix:** The `SplashViewModel.resolveSignedInDestination()` function has a TODO
for `pending_consent` routing. Implement it:

```
pending_consent + role == student + age < 18 → StudentShareCode route
pending_consent + role == student + age >= 18 → Consent(AdultStudentSelfConsent)
pending_consent + role == parent → Consent(ParentTermsOfService)
active + role == student → StudentHome
active + role == parent → ParentHome
suspended → RoleSelect (with a "Your account has been suspended" snackbar)
```

The user's DOB (to recalculate age) and role are already in their `/users/{uid}`
Firestore document. Read them in the SplashViewModel before routing.

**Commit after Part 0:** `fix: phone number saved to Firestore, logout, splash routing`

---

## Part 1 — The consent flow state machine

This is the conceptual core of Phase 4. Understand it before reading the UI
sections.

Every user goes through consent exactly once per consent type. The state machine:

```
                         ┌─────────────────────────────┐
                         │       AUTH COMPLETE          │
                         │  (OTP verified, name entered) │
                         └──────────────┬──────────────┘
                                        │
                      ┌─────────────────┼─────────────────┐
                      ▼                 ▼                  ▼
               role=student        role=student        role=parent
               age >= 18           age < 18
                      │                 │                  │
                      ▼                 ▼                  ▼
            ConsentScreen         StudentShare        ConsentScreen
          (ADULT_STUDENT_         CodeScreen         (PARENT_TOS)
            SELF, v1.0)               │                   │
                      │               │ [code claimed      │ [signed]
                      │ [signed]       │  by parent]        │
                      │               ▼                    ▼
                      │        ConsentScreen          ParentHome
                      │      (PARENT_FOR_MINOR,     (pending students
                      │          v1.0)                   list)
                      │               │
                      │               │ [parent signs]
                      ▼               ▼
                StudentHome ← [student status flipped to "active"]
               (session ready)
```

The student and parent halves of the minor linking flow run on **two separate
devices simultaneously**. The student waits on `StudentShareCodeScreen` while
the parent goes through their consent. The student's app listens to Firestore
in real time and transitions automatically when the parent signs.

---

## Part 2 — ConsentScreen (shared, parameterized)

`ConsentScreen` is used for all three consent types. It is fully parameterized.

### What it must do

1. Load the consent text from assets for the given `ConsentType`, current
   `consentVersion`, and device locale (fallback to `en`).
2. Display the text in a `LazyColumn` (not a `ScrollView` — lazy so it works
   with long texts on small phones).
3. Track whether the user has scrolled to the very end. The "I agree" button is
   **disabled and grayed** until they have. Use `LazyListState.isScrolledToEnd()`:
   ```kotlin
   val listState = rememberLazyListState()
   val atBottom by remember {
       derivedStateOf {
           val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
           lastVisible?.index == listState.layoutInfo.totalItemsCount - 1
       }
   }
   ```
4. Show a "Full name" text field (for e-signature). Must be non-empty for
   "I agree" to enable.
5. Show a language selector if the user's phone locale isn't one of our four
   (as, hi, bn, en). Allow switching between EN, AS, HI, BN inline.
6. On "I agree" tap:
   - Disable the button and show a loading indicator
   - Call `ConsentRepository.recordConsent(type, version, lang, signatureText)`
   - Wait for the Cloud Function to stamp `signedAt` (poll the document with
     a 30-second timeout, checking every 2 seconds)
   - On success, invoke the completion callback provided by the caller
   - On timeout or error, show an inline error, re-enable the button

### ConsentViewModel

```kotlin
sealed interface ConsentEvent {
    data class SignatureChanged(val text: String) : ConsentEvent
    data class LanguageChanged(val lang: String) : ConsentEvent
    data object ScrolledToEnd : ConsentEvent
    data object AgreedTapped : ConsentEvent
}

data class ConsentUiState(
    val consentText: String = "",
    val consentType: ConsentType,
    val consentVersion: String,
    val language: String = "en",
    val signature: String = "",
    val hasScrolledToEnd: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,   // true → caller should navigate away
)
```

`isComplete = true` is the signal for the parent Route composable to navigate.
The ViewModel never navigates itself.

### The signedAt polling contract

After `ConsentRepository.recordConsent()` writes the Firestore document, it
returns the document ID. The ViewModel then calls
`ConsentRepository.awaitServerTimestamp(consentId)` which returns a
`Flow<Boolean>` that emits `true` when `signedAt` is populated. The ViewModel
races this Flow against a 30-second timeout:

```kotlin
val serverConfirmed = withTimeoutOrNull(30_000) {
    consentRepository.awaitServerTimestamp(consentId).first { it }
} != null
```

If `serverConfirmed` is false, show error: "Something went wrong confirming your
consent. Try again." The document should be deleted on timeout so the user can
retry cleanly.

---

## Part 3 — Adult student path (Flow A)

After `NameEntryScreen` completes for an adult (age ≥ 18) student:
- Navigate to `ConsentScreen` with `ConsentType.ADULT_STUDENT_SELF`
- On `isComplete = true`:
  - Call `UserRepository.activateAccount(uid)` (writes `accountStatus: "active"`)
  - Navigate to `StudentHome`, clearing back stack

That's it. No linking code, no waiting. The student is immediately active.

---

## Part 4 — Minor student path, student side (Flow B/C)

After `NameEntryScreen` completes for a minor (age < 18) student:
- Navigate to `StudentShareCodeScreen`

### StudentShareCodeScreen

This screen does the most complex real-time work in the entire app.

**On entry:**
1. `LinkingViewModel.generateCode()` → writes to `/linkingCodes/{code}` via
   `LinkingRepository` → stores the code in ViewModel state
2. Display the code in large, readable format: `4  7  2  9  1  3`
   (spaced out so it's easy to read over a phone call)
3. Display "Expires in MM:SS" countdown. Start at 30:00.
4. "Get a new code" button (enabled any time, not just on expiry)
5. Instructions: "Share this code with your parent. They need to enter it in
   the Invigilator app."

**While waiting:**
- `LinkingViewModel` listens to the `/linkingCodes/{code}` document in Firestore
- When `claimedBy` is set (parent has entered the code), update state to show
  "Your parent is reviewing your consent..." spinner
- `LinkingViewModel` also listens to the student's own `/users/{uid}` document
- When `accountStatus` changes to `"active"`, navigate to `StudentHome`

**On "Get a new code":**
- Generate a new code (new 6-digit number, new 30-min TTL)
- The old code document remains but is now orphaned (the student is no longer
  listening to it). The nightly cleanup Cloud Function handles it.

**Real-time listeners must be cancelled** (in `onCleared()`) to prevent memory
leaks and spurious Firestore reads after the user navigates away.

```kotlin
// In LinkingViewModel
private var codeListener: ListenerRegistration? = null
private var statusListener: ListenerRegistration? = null

override fun onCleared() {
    codeListener?.remove()
    statusListener?.remove()
}
```

---

## Part 5 — Minor student path, parent side (Flow B/C)

After `ParentHome` exists and the parent taps "Add a student":
- Navigate to `EnterCodeScreen`

### EnterCodeScreen

- Six individual digit input boxes (not one long text field) — this is the
  standard UX for code entry in Indian apps; it's friendlier than typing
  "472913" into a plain field
- Auto-advance to next box after each digit
- Paste from clipboard should populate all 6 boxes
- "Confirm" button → call `claimLinkingCode` Cloud Function via
  `LinkingRepository.claimCode(code)`

**On success from `claimLinkingCode`:** the function returns
`{studentUid, studentDisplayName, dateOfBirthMillis}`.
Navigate to `ConfirmStudentScreen`.

### ConfirmStudentScreen

A simple confirmation screen before the parent consents:

```
You are about to link:

    Aarav Mishra
    Age: 11

Is this your child?

[Not my child]    [Yes, continue]
```

Age is computed from `dateOfBirthMillis`. "Not my child" pops back to
`EnterCodeScreen`. "Yes, continue" navigates to `ConsentScreen` with
`ConsentType.PARENT_FOR_MINOR`.

### After parent consent is complete

On `ConsentScreen.isComplete = true` for `PARENT_FOR_MINOR`:

The parent-side ViewModel (or a dedicated `LinkingCompletionViewModel`) must
execute a **three-step sequence** that must all succeed or all surface an error:

1. Call `LinkingRepository.createLinkedStudentDoc(parentUid, studentUid, consentId)`
   → writes `/users/{parentUid}/linkedStudents/{studentUid}`
2. Call `UserRepository.activateAccount(studentUid)`
   → writes `accountStatus: "active"` on the *student's* document

These two writes can be done as a Firestore batch (not a transaction, since we
don't need read-then-write semantics — we just need atomic success/failure).

3. Navigate parent to `ParentHome`

**Error handling:** if either write fails, surface an error on the parent's
screen with a "Try again" button. Do not leave a half-written state silently —
a linked student doc without an active account status is a ghost that will
confuse everyone. If step 1 succeeds but step 2 fails, attempt a rollback of
step 1. If rollback also fails, log to Crashlytics with high severity — this
is a data integrity issue that needs manual inspection.

---

## Part 6 — Parent ToS consent and ParentHome

### Parent Terms of Service

Parents are different from students: they don't have a linking code and don't
wait for anyone. After `NameEntryScreen` for a parent:
- Navigate to `ConsentScreen` with `ConsentType.PARENT_TERMS_OF_SERVICE`
- On `isComplete = true`:
  - Activate the parent's account
  - Navigate to `ParentHome`

### ParentHome (placeholder but functional)

```
My Students                             [+ Add]

─────────────────────────────────────────
  No students linked yet.
  Tap + to add your first student.
─────────────────────────────────────────
```

If students exist (after linking), show a simple list:
```
  ● Aarav Mishra       pending_consent
  ● Priya Sharma       active
```

The `active` / `pending_consent` badge is read from the linked student's
document. This is the first real data-driven UI in the app — it should refresh
automatically via a Firestore real-time listener.

"Add a student" button → `EnterCodeScreen`.

Overflow menu (three dots, top-right) → "Log out".

### StudentHome (placeholder but functional)

```
Good morning, Aarav 👋

Today's study session
────────────────────
  Not started yet.
  [Start session]   ← placeholder button, does nothing in Sprint 2
────────────────────

Linked to: Test Parent
```

The "Linked to" line is read from `/users/{studentUid}/linkedStudents` — wait,
that subcollection is on the *parent's* doc. Instead, store a `parentUid` field
on the student's user doc when linking is complete. Simple, avoids a cross-user
subcollection read.

Add `parentUid: String?` to `UserDoc` and populate it in
`UserRepository.activateAccount(studentUid, parentUid)`.

Overflow menu → "Log out".

---

## Part 7 — Firestore additions for Part 4–6

### New field on /users/{uid}

Add `parentUid: String?` — null for parents and unlinked adult students,
populated for linked minor students.

### Security rules update

Add one rule: a student can *read* (not write) the user document of their
linked parent, to display "Linked to: Test Parent" on StudentHome.

```javascript
// In the /users/{uid} match block, extend the read rule:
allow read: if request.auth != null
            && (request.auth.uid == uid
                || isLinkedParentOf(request.auth.uid, uid));

function isLinkedParentOf(requesterUid, studentUid) {
  // requester is the parent, student is the uid being read
  return exists(/databases/$(database)/documents/users/$(requesterUid)/linkedStudents/$(studentUid));
}
```

### New Cloud Function: `onStudentActivated` (optional, Sprint 3+)

We don't need this now. Leave a TODO in `backend/functions/src/index.ts`:
```typescript
// TODO Sprint 3: onStudentActivated — send parent a push notification
// when their student's account becomes active
```

---

## Part 8 — Testing requirements

Unit tests (run with `./gradlew testDebugUnitTest`):

- `ConsentViewModelTest`:
  - `signatureRequired` — verify "I agree" is disabled when signature is empty
  - `scrollRequiredBeforeAgreeing` — verify "I agree" is disabled until scrolled to end
  - `consentCompletes` — mock `ConsentRepository.recordConsent()` success +
    mock `awaitServerTimestamp()` returning true → `isComplete = true`
  - `consentTimeoutShowsError` — mock `awaitServerTimestamp()` never returning →
    after 30s simulated time → `error != null`, `isLoading = false`

- `LinkingViewModelTest`:
  - `codeGenerated` — `generateCode()` sets code in state, starts countdown
  - `countdownExpires` — after 30 minutes simulated time, code is marked expired
  - `parentClaimedCode` — Firestore listener fires `claimedBy = parentUid` →
    `state.parentClaiming = true`
  - `studentActivated` — Firestore listener fires `accountStatus = "active"` →
    `state.isActivated = true`

- `SplashViewModelTest` (extend existing):
  - Add test for each of the 5 routing branches introduced in Part 0 Bug 3

---

## Part 9 — Definition of done

✅ Bug 0.1 fixed: all three users created in C.4 now have `phoneNumber` populated
✅ Bug 0.2 fixed: logout works from both home screens on a real phone
✅ Bug 0.3 fixed: relaunching app routes to the right screen without error

✅ Flow A (adult student, end to end, one phone):
   - Signs up, sees consent, scrolls, types name, taps agree
   - Firestore: `/consents/{id}` exists with `signedAt` populated by server
   - Firestore: `/users/{uid}` shows `accountStatus: "active"`
   - App: navigates to StudentHome

✅ Flow B (minor student + existing parent, two phones, under 2 minutes):
   - Student phone: code appears, waits
   - Parent phone: enters code, sees student name + age, reads consent, signs
   - Student phone: automatically advances to StudentHome
   - Firestore: consent + linkedStudent docs exist; student's accountStatus = active

✅ Flow C (minor student + new parent, two phones):
   - Student phone: same as Flow B student side
   - Parent phone: fresh install, signs up as parent, enters code, consents
   - Same outcome as Flow B

✅ Flow E (parent first, student later):
   - Parent signs up, consents to ToS, sees empty ParentHome
   - "Add a student" → code entry screen is visible and functional
   - Parent and student complete linking via Flow B/C after this point

✅ Relaunch routing works for all account states
✅ All unit tests pass
✅ `./gradlew lint` clean
✅ Committed and pushed to GitHub
