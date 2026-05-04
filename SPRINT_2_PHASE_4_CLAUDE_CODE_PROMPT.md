# Claude Code Prompt — Sprint 2 Phase 4

Paste everything inside the fenced block below as your first message to
Claude Code. This is a longer session than previous phases — expect 3–5 hours.
Read SPRINT_2_PHASE_4_SPEC.md fully before starting.

---

```
This is Sprint 2 Phase 4 of the Invigilator project. Read these files
in full before writing a single line of code, in this order:

  1. README.md
  2. ARCHITECTURE.md
  3. SPRINT_2_SPEC.md
  4. SPRINT_2_PHASE_4_SPEC.md  ← the authoritative spec for this session

The spec is the law. If anything below conflicts with the spec, follow
the spec and flag the conflict. Preserve all patterns from Phase 3:
StateFlow<UiState>, sealed events, stateless screens + stateful Routes,
repositories returning Result<T>, no LiveData, no Java.

Work in parts, exactly as the spec names them. After each part, run
`./gradlew assembleDebug testDebugUnitTest` and fix any failures before
moving to the next part. Commit each part separately.

═══════════════════════════════════════════════════════════════════════
PART 0 — Bug fixes (do first, commit before anything else)
═══════════════════════════════════════════════════════════════════════

Fix the three bugs in SPRINT_2_PHASE_4_SPEC.md Part 0 in order.

For Bug 1 (phoneNumber empty):
- Find where UserRepositoryImpl calls Firestore to create the user doc.
- The phone number is available at FirebaseAuth.getInstance().currentUser
  ?.phoneNumber after OTP verification. Read it there.
- If it is null (should not happen in normal flow), read it from the
  OnboardingViewModel's state — the phone string is E.164-formatted by
  PhoneFormatter and flows through nav arguments.
- Verify by checking the actual code path end to end, not just by
  assuming the fix. Add a Timber.w() log if phoneNumber is null so we
  catch it in development.

For Bug 2 (no logout):
- Add overflow menu to both ParentHomeScreen and StudentHomeScreen.
- Logout clears auth and navigates to RoleSelect, popping the entire
  back stack.

For Bug 3 (splash routing):
- Read SPRINT_2_PHASE_4_SPEC.md Part 0 Bug 3 for the complete
  routing table.
- The SplashViewModel needs to read the user doc from Firestore, not
  just check Firebase Auth state. Role, accountStatus, and dateOfBirth
  are all needed to route correctly.
- Add unit tests for all 5 routing branches.

Commit: "fix: phone number in Firestore, logout, splash routing"

═══════════════════════════════════════════════════════════════════════
PART 1 — ConsentScreen and ConsentViewModel (shared component)
═══════════════════════════════════════════════════════════════════════

Build ConsentScreen and ConsentViewModel per spec Part 2.

1.1 ConsentViewModel:
    - Takes ConsentType and consentVersion as SavedStateHandle args.
    - On init, loads consent text from assets via ConsentRepository,
      computes language from device locale (fallback to "en").
    - Exposes ConsentUiState per the spec.
    - On AgreedTapped: calls ConsentRepository.recordConsent(), then
      polls awaitServerTimestamp() with a 30-second timeout.
    - On timeout: deletes the incomplete consent doc from Firestore,
      sets error state.

1.2 ConsentRepository additions:
    - `suspend fun recordConsent(type, version, lang, signature): Result<String>`
      (returns consentId)
    - `fun awaitServerTimestamp(consentId: String): Flow<Boolean>`
      (emits true when signedAt is populated, never emits false,
      the caller handles timeout externally)

1.3 ConsentScreen:
    - LazyColumn for consent text.
    - Scroll-to-end detection per the spec (derivedStateOf on
      LazyListState).
    - Full-name text field.
    - Language selector row (4 chips: EN / AS / HI / BN).
    - "I agree" button disabled until scrolled to end AND signature
      non-empty.
    - Loading state disables button and shows CircularProgressIndicator.
    - Error shows as red inline text below the button, not a Snackbar
      (the user needs to see it while looking at the button).

1.4 ConsentRoute wires the ViewModel to the screen. It takes an
    `onComplete: () -> Unit` lambda — the caller decides what to do
    after consent. This keeps ConsentScreen reusable for all 3 consent
    types.

1.5 Unit tests per spec Part 8.

Commit: "feat(consent): ConsentScreen and ConsentViewModel"

═══════════════════════════════════════════════════════════════════════
PART 2 — Adult student path (Flow A)
═══════════════════════════════════════════════════════════════════════

Wire up the adult student path per spec Part 3.

2.1 After NameEntry for age >= 18 student:
    navigate to Consent(ADULT_STUDENT_SELF).

2.2 On consent complete:
    - Call UserRepository.activateAccount(uid) — writes
      accountStatus: "active" to /users/{uid}.
    - Navigate to StudentHome, clearing the back stack.

2.3 StudentHome placeholder (full spec in Part 6, but build the
    structural version now so Flow A has somewhere to land):
    - Greeting with displayName.
    - "Today's session" card — placeholder, says "Coming in Sprint 3".
    - "Linked to: [parent name]" line — only if parentUid is non-null.
    - Overflow menu with "Log out".

2.4 Verify Flow A end to end on a device.

Commit: "feat: adult student consent and activation (Flow A)"

═══════════════════════════════════════════════════════════════════════
PART 3 — StudentShareCodeScreen (minor student waiting side)
═══════════════════════════════════════════════════════════════════════

Build the student side of the minor linking flow per spec Part 4.

3.1 LinkingViewModel (new):
    - generateCode(): calls LinkingRepository.generateCode(), which
      writes to /linkingCodes/{code} and returns the 6-digit string.
    - Starts a 30-minute countdown (1-second ticks via Flow.ticker or
      a coroutine with delay(1000)).
    - Listens to /linkingCodes/{code} Firestore document:
        - when claimedBy != null → state.parentClaiming = true
    - Listens to /users/{uid} Firestore document:
        - when accountStatus == "active" → state.isActivated = true
    - Cancel both listeners in onCleared().

3.2 StudentShareCodeScreen:
    - Large spaced-digit display: "4  7  2  9  1  3"
      Use a Row of 6 individual Text composables, each in a Card.
    - "Expires in MM:SS" text, counting down.
    - "Get a new code" button.
    - When state.parentClaiming == true: overlay a semi-transparent
      banner "Your parent is reviewing the consent form..."
    - When state.isActivated == true: navigate to StudentHome.

3.3 Route: after NameEntry for age < 18 student → StudentShareCode.

3.4 Unit tests per spec Part 8 (LinkingViewModelTest).

Commit: "feat: StudentShareCodeScreen and linking wait state"

═══════════════════════════════════════════════════════════════════════
PART 4 — EnterCodeScreen and ConfirmStudentScreen (parent claiming)
═══════════════════════════════════════════════════════════════════════

Build the parent side of the minor linking flow per spec Part 5.

4.1 EnterCodeScreen:
    - Six individual single-digit TextField boxes in a Row.
    - Each box: maxLength=1, numeric keyboard, auto-advance on input.
    - Paste from clipboard: detect if clipboard contains 6 digits,
      auto-fill all boxes.
    - "Confirm" button enabled when all 6 digits are filled.
    - On submit: call LinkingRepository.claimCode(code) (the
      claimLinkingCode Cloud Function callable).
    - Error states per spec Section 9 of SPRINT_2_SPEC.md:
      not-found, expired, already-used.

4.2 ConfirmStudentScreen:
    - Shows student display name and computed age.
    - "Not my child" → pop back to EnterCode.
    - "Yes, continue" → navigate to Consent(PARENT_FOR_MINOR).

4.3 On PARENT_FOR_MINOR consent complete:
    - Write /users/{parentUid}/linkedStudents/{studentUid} via
      LinkingRepository.createLinkedStudentDoc().
    - Write accountStatus: "active" AND parentUid on the student's
      /users/{studentUid} doc via UserRepository.activateAccount().
    - Do both as a Firestore batch write (not individual calls).
    - On batch success → navigate to ParentHome.
    - On batch failure → show error, offer retry. Log to Timber.e().
      Do NOT navigate away with a half-complete state.

4.4 Add parentUid: String? field to UserDoc and update
    UserRepository.activateAccount() signature to accept it.
    Update the Firestore security rule per spec Part 7 so a student
    can read their parent's user doc.

Commit: "feat: parent code entry, confirm, and consent (Flow B/C)"

═══════════════════════════════════════════════════════════════════════
PART 5 — Parent ToS consent and ParentHome (Flow E)
═══════════════════════════════════════════════════════════════════════

5.1 After NameEntry for a parent:
    → Consent(PARENT_TERMS_OF_SERVICE)
    → On complete: activate parent account → ParentHome

5.2 ParentHome per spec Part 6:
    - Real-time list of linked students from Firestore
      (listen to /users/{parentUid}/linkedStudents).
    - Empty state with "Add a student" CTA.
    - Each student row shows displayName + accountStatus badge.
    - "Add a student" FAB or button → EnterCodeScreen.
    - Overflow menu → Log out.

5.3 Cancel Firestore listeners in ViewModel.onCleared().

Commit: "feat: parent ToS consent, ParentHome with real-time student list"

═══════════════════════════════════════════════════════════════════════
PART 6 — Final integration pass
═══════════════════════════════════════════════════════════════════════

6.1 Deploy updated firestore.rules (the security rule from spec Part 7
    allowing a student to read their parent's user doc). If you can
    run `firebase deploy --only firestore:rules` yourself, do it and
    confirm. If not, update the file and note that the human needs to
    deploy.

6.2 Verify all four flows work on a real device (or emulator):
    Flow A: adult student, single phone.
    Flow B: minor student + existing parent, two phones (or two
            emulators/accounts).
    Flow C: minor student + new parent, two phones.
    Flow E: parent first, student later.

6.3 Run the full test suite: `./gradlew testDebugUnitTest lint`
    Fix any failures.

6.4 Final commit: "feat(sprint-2): complete consent and linking flows"

Print a summary:
  - What was built in each part
  - Any deviations from the spec and why
  - Any errors hit and how fixed
  - What needs manual steps (Firebase deploy, etc.)
  - Known limitations or TODOs for Sprint 3
```

---

## What to do while Claude Code works

### After Part 0 is committed

Reinstall the app on your phone and repeat C.4 from the checklist with two
fresh accounts. Verify:
- `phoneNumber` is now populated in Firestore on both accounts.
- Logout returns to RoleSelect.
- Killing and relaunching the app routes back to the right screen.

Don't tell Claude Code the result — just confirm these for yourself. If any
of the three are still broken, stop Claude Code and paste the output here.

### After Part 1 is committed

You don't need to test it yet — ConsentScreen is wired up but not reachable
until Part 2. Just let Claude Code continue.

### After Part 2 is committed

Test Flow A end-to-end with a fresh account on your phone:

1. Sign up as student, DOB = 2000-01-01 (adult).
2. Enter name.
3. Consent screen appears. Try tapping "I agree" before scrolling — it should
   be disabled.
4. Scroll to the very bottom of the consent text.
5. Type your full name in the signature field.
6. Tap "I agree". It should show a spinner for a few seconds while it waits
   for the Cloud Function to stamp `signedAt`.
7. Navigate to StudentHome.

Check Firestore:
- `/consents/{id}` exists with `signedAt` populated (server timestamp) and
  `consentTextHash` matching one of your hash values.
- `/users/{uid}` shows `accountStatus: "active"`.

If the spinner hangs forever: the Cloud Function didn't fire. Check the
Firebase Console → Functions → Logs tab for `onConsentCreate` errors.

### After Part 3 is committed

Test the minor student side in isolation:

1. Sign up as student, DOB = 2015-03-01 (minor).
2. After name entry, the share-code screen should appear with a 6-digit code.
3. Verify the countdown is ticking.
4. Verify a document appears in Firestore at `/linkingCodes/{code}`.
5. Tap "Get a new code" — a different code should appear.

Don't complete the linking yet — wait until Part 4 is done.

### After Part 4 is committed

This is the big two-phone test. You need two devices (or one device +
Firebase Emulator).

**Device A (student):** Sign up as minor student, get to the share-code screen.
**Device B (parent):** Sign up as parent (or use existing test parent account).
→ Add student → enter the code from Device A → confirm student name and age →
read and sign the consent.

Watch both phones. Device A's waiting screen should automatically update
("Your parent is reviewing...") and then navigate to StudentHome when linking
is complete.

Check Firestore for all the right documents (consent, linkedStudent, student
accountStatus = active, parentUid on student doc).

### After Part 5 is committed

The full end-to-end suite should work. Run all four flows:

- Flow A: one phone, adult student.
- Flow B: two phones, minor student + already-signed-up parent.
- Flow C: two phones, minor student + parent who signs up fresh.
- Flow E: two phones, parent signs up first, student follows.

**Expected Firestore state after one complete Flow B:**
```
/users/{parentUid}
  role: "parent"
  accountStatus: "active"
  phoneNumber: "+91xxxxxxxxxx"
  parentUid: null
  dateOfBirth: null

/users/{parentUid}/linkedStudents/{studentUid}
  studentUid: "..."
  studentDisplayName: "..."
  linkedAt: Timestamp
  linkType: "parent_minor"
  consentRecordId: "/consents/{id}"

/users/{studentUid}
  role: "student"
  accountStatus: "active"
  parentUid: "{parentUid}"
  dateOfBirth: Timestamp (a date before 2008)
  phoneNumber: "+91xxxxxxxxxx"

/consents/{parentConsentId}
  consentType: "ParentForMinorConsent"
  consentVersion: "v1.0"
  signedAt: Timestamp (server-written, not null)
  consenterUid: {parentUid}
  subjectUid: {studentUid}
  signatureText: "Full Name of Parent"
  invalidated: false (or field absent)

/consents/{parentTosConsentId}
  consentType: "ParentTermsOfService"
  consentVersion: "v1.0"
  signedAt: Timestamp
  consenterUid: {parentUid}
```

If all of that matches, Sprint 2 is done. Bring me the summary Claude Code
prints plus one screenshot of the Firestore tree.

---

## After Sprint 2 is complete

Sprint 3 is where the product actually becomes an Invigilator:

- Foreground app detection (UsageStatsManager) during a session
- The session start/stop flow
- Distraction detection logic
- The first stage of escalation: on-screen voice nudge

That's where we start needing `PACKAGE_USAGE_STATS` permission (user must grant
it from Android Settings), the foreground service, and the first Accessibility
Service scaffold. It's the highest-Android-expertise phase of the MVP.

When Claude Code finishes Phase 4 and you've verified all four flows, come back
here. We'll plan Sprint 3 with the same level of care we've given Sprint 2.
