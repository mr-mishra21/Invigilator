# Claude Code Prompt — Sprint 2

Save this file alongside `SPRINT_2_SPEC.md` in your `Invigilator` folder. Both files need to be in the project root before you start the Claude Code session.

When you're ready, paste everything inside the fenced block below as your first message to Claude Code.

**Important: this sprint is meant to be split across 2 sessions.** The prompt explicitly tells Claude Code to stop after Phase 3 and ask for confirmation. Do not skip that pause — it's the natural checkpoint where you and I review progress before pushing into the consent legal work.

---

```
This is Sprint 2 of the Invigilator project. Read these files in full
before writing any code, in this order:

1. README.md
2. ARCHITECTURE.md
3. SPRINT_2_SPEC.md  ← the master spec for this sprint

SPRINT_2_SPEC.md is the source of truth for what to build. If anything
below conflicts with it, follow the spec and flag the conflict. Do not
deviate from the patterns in Section 6 of the spec — those patterns
must be copied exactly into every ViewModel, repository, and screen.

You will work in PHASES. After each phase, run `./gradlew assembleDebug
&& ./gradlew testDebugUnitTest`, fix any failures, and commit the work
with a descriptive message before moving to the next phase. After
PHASE 3, STOP and wait for explicit confirmation to continue. Do not
auto-proceed past Phase 3.

═══════════════════════════════════════════════════════════════════
PHASE 1 — Domain layer in :core
═══════════════════════════════════════════════════════════════════

Build only the :core module additions, no UI yet.

1.1 Add libraries to gradle/libs.versions.toml as needed (Firebase Auth,
    Firestore, Functions, Crashlytics; navigation-compose with
    type-safe routes via kotlinx.serialization; turbine for tests).
    Apply kotlinx-serialization plugin where needed.

1.2 Create the package structure in :core exactly as specified in
    Section 5 of SPRINT_2_SPEC.md.

1.3 Create the data classes:
    - UserDoc, LinkedStudentDoc, ConsentDoc, LinkingCodeDoc
    - Annotate with @Serializable where they need to round-trip
      through Firestore via kotlinx.serialization (or use Firestore's
      built-in @PropertyName approach — pick one and be consistent).

1.4 Create the enums and sealed classes:
    - UserRole (PARENT, STUDENT)
    - AccountStatus (PENDING_CONSENT, ACTIVE, SUSPENDED)
    - ConsentType (ADULT_STUDENT_SELF, PARENT_FOR_MINOR,
      PARENT_MONITORING_ADULT_STUDENT, PARENT_TERMS_OF_SERVICE)
    - AuthState (Loading, SignedOut, SignedIn(uid))
    - AuthError (sealed class — see spec section 6)
    - LinkingError (sealed class — analogous shape)

1.5 Create the repository interfaces:
    - AuthRepository, UserRepository, ConsentRepository,
      LinkingRepository
    - All in :core, following the pattern in spec section 6.

1.6 Create the implementations as `internal class` with @Inject
    constructors:
    - AuthRepositoryImpl (wraps FirebaseAuth + PhoneAuthProvider)
    - UserRepositoryImpl (wraps Firestore /users)
    - ConsentRepositoryImpl (wraps Firestore /consents + reads
      consent text from assets, computes SHA-256 hash)
    - LinkingRepositoryImpl (wraps /linkingCodes + claimLinkingCode
      callable)

1.7 Create CoreModule with @Binds bindings for all four interfaces.

1.8 Create the consent assets directory and add ALL 12 files specified
    in Section 6 of the spec:
    - 3 consent types (AdultStudentSelfConsent, ParentForMinorConsent,
      ParentTermsOfService) × 4 languages (en, as, hi, bn).
    - For en: use the drafts at the end of SPRINT_2_SPEC.md verbatim.
    - For as, hi, bn: prefix the EN content with the line
      "[DRAFT — NOT FOR PRODUCTION — translation pending professional
      review]" and provide your best translation. Make this prefix
      VERY visible.

1.9 Create a Gradle task `verifyConsentHashes` that reads each consent
    asset, computes SHA-256, and writes a generated Kotlin file
    `ConsentHashes.kt` containing the map. Wire this task to run
    before `compileDebugKotlin`. The point: if anyone modifies the
    asset without bumping the version, downstream code that compares
    against EXPECTED_HASHES will detect it.

1.10 Write unit tests:
     - PhoneFormatterTest (Indian numbers: "9876543210",
       "+919876543210", "+91 9876 543 210", "919876543210" —
       all should normalize to "+919876543210"; "1234" should fail)
     - ConsentRepositoryTest (resolution of type+version+lang to
       text; hash matches expected; missing language falls back to en)
     - LinkingCodeGeneratorTest (codes are exactly 6 digits including
       leading zeros; randomness check over 1000 generations)

After Phase 1: commit "feat(core): domain layer for auth, consent,
linking". Run all tests. Verify build is green.

═══════════════════════════════════════════════════════════════════
PHASE 2 — Firebase project setup documentation
═══════════════════════════════════════════════════════════════════

Note: actual Firebase console setup is done by the human (me), not by
you. Your job is to produce the runbook.

2.1 Create docs/firebase-setup.md with the exact step-by-step from
    Section 8 of the spec, expanded with screenshots' described
    locations (e.g. "click 'Build' in the left nav, then 'Authentication'").

2.2 Create firestore.rules at the project root with the exact rules
    from Section 3 of the spec.

2.3 Create the backend/ folder structure:
       backend/
         functions/
           src/
             index.ts
             onConsentCreate.ts
             claimLinkingCode.ts
             consentHashes.ts   ← generated/checked-in mirror
                                  of the Android-side hashes
           package.json
           tsconfig.json
         firebase.json          ← functions + firestore config
         .firebaserc

2.4 Implement the two Cloud Functions per Section 4 of the spec
    (TypeScript, Node 20, region asia-south1).

2.5 Create scripts/sync-consent-hashes.sh — a shell script that reads
    the assets, computes SHA-256, and updates BOTH the Android
    generated file AND backend/functions/src/consentHashes.ts so
    server and client see the same expected hashes.

2.6 Create docs/cloud-functions-deployment.md with instructions to
    deploy the functions (npm install, firebase login, firebase use
    <project-id>, firebase deploy --only functions).

After Phase 2: commit "feat(backend): firestore rules and consent
audit cloud functions".

═══════════════════════════════════════════════════════════════════
PHASE 3 — Auth UI flow (signup only, no consent yet)
═══════════════════════════════════════════════════════════════════

3.1 Add Hilt-Navigation-Compose. Set up InvigilatorNavHost with
    type-safe routes (Section 6 of spec). For now wire only:
    Splash → RoleSelect → PhoneEntry → OtpEntry → DobEntry →
    NameEntry → (placeholder) Home.

3.2 Build SplashScreen + SplashViewModel. Resolves auth state:
    - signed out → RoleSelect
    - signed in + active → role-specific home
    - signed in + pending_consent → resume at the right onboarding step

3.3 Build RoleSelectScreen + ViewModel. Two large buttons: "I am a
    parent" / "I am a student". On select, navigate to PhoneEntry
    with the role.

3.4 Build PhoneEntryScreen + ViewModel. India country code prefilled
    (+91), 10-digit phone field, validate as user types, submit
    sends OTP via AuthRepository, navigate to OtpEntry.

3.5 Build OtpEntryScreen + ViewModel. 6-digit OTP field, 60-second
    resend timer, "Wrong number?" back button, on success:
    - if role == student → DobEntry
    - if role == parent → NameEntry directly (no DOB)

3.6 Build DobEntryScreen + ViewModel (student only). Date picker.
    Compute age. Branch on age:
    - >=18 → adult path: NameEntry
    - <18 → minor path: NameEntry, but mark account
      pending_consent for parent linking

3.7 Build NameEntryScreen + ViewModel. Single text field, submit
    creates the /users/{uid} document with the right role,
    accountStatus = "pending_consent" (always — even for adult
    students; consent is the next phase). Navigate to a placeholder
    screen that says "Phase 4: consent flow not yet implemented".

3.8 Use Material 3, sentence-case throughout, generous spacing,
    big tap targets (min 48dp). No icons we don't need. Plain
    typography. Keep it boring — we're not designing a brand here,
    we're shipping a working app.

3.9 Set the app's default locale to English for now. We'll layer in
    AS/HI/BN once the auth flow is complete.

3.10 Write unit tests for every ViewModel using Turbine to verify
     state transitions on each event.

After Phase 3: commit "feat(app): phone-OTP signup flow with role
and DOB branching". Run lint + tests. Sideload to a real device and
verify the full signup flow works (use Firebase test phone numbers
to skip real SMS).

╔═══════════════════════════════════════════════════════════════════╗
║                                                                   ║
║                          STOP HERE                                ║
║                                                                   ║
║  Push to GitHub and wait for explicit confirmation from me        ║
║  before continuing to Phase 4. Print a summary of:                ║
║   - what you built                                                ║
║   - any deviations from the spec and why                          ║
║   - any errors you hit and how you fixed them                     ║
║   - any TODOs you left for me                                     ║
║                                                                   ║
╚═══════════════════════════════════════════════════════════════════╝

═══════════════════════════════════════════════════════════════════
PHASE 4 — Consent flow and account activation  (after my green light)
═══════════════════════════════════════════════════════════════════

[do not start Phase 4 without explicit confirmation]

4.1 Build ConsentScreen + ViewModel. Parameterized by ConsentType:
    - Loads the right asset for the active locale
    - Renders the text in a scrollable view; user MUST scroll to
      bottom before "I agree" enables (this is a DPDP best practice
      — proves they had the chance to read it)
    - Asks for full-name signature
    - Submit creates the /consents/{id} document via
      ConsentRepository
    - On success: update /users/{uid} accountStatus to "active"
      (for adult self-consent path) OR write the linkedStudent doc
      (for parent-for-minor path)

4.2 For adult students (Flow A): after NameEntry, navigate to
    Consent(AdultStudentSelfConsent). On consent success → activate
    account → StudentHome.

4.3 For minor students (Flow B/C): after NameEntry, navigate to
    StudentShareCodeScreen. Show the generated 6-digit code.

4.4 Build StudentShareCodeScreen + ViewModel. On entry, calls
    LinkingRepository.generateCode() which writes to /linkingCodes.
    Display code prominently. Show "expires in MM:SS" countdown.
    "Get a new code" button to regenerate. Listen on Firestore
    for the linkingCodes doc to be claimed → navigate to
    StudentLinkingPending → eventually StudentHome when parent
    consent completes.

4.5 For parents (Flow E): after NameEntry, navigate to consent for
    ParentTermsOfService → ParentHome (empty state).

4.6 Build ParentEnterCodeScreen + ViewModel. From parent home,
    "Add a student" → enter code → calls claimLinkingCode callable.
    On success, fetch student details, show
    "Confirm: <name>, age <age>", continue to Consent screen
    (ParentForMinorConsent or ParentMonitoringAdultStudent based
    on student age).

4.7 On parent consent submit:
    - Create /consents/{id} via ConsentRepository
    - Wait for server to populate signedAt (Cloud Function trigger)
    - Create /users/{parentUid}/linkedStudents/{studentUid} doc
      referencing the consent
    - Update student's account_status to "active"
    - Navigate parent back to ParentHome with the new student visible

4.8 Build ParentHomeScreen and StudentHomeScreen. Both placeholder.
    Parent: list of linked students. Student: "Today's session"
    placeholder card. Both with Settings menu containing Logout.

4.9 Implement Logout. Clears Firebase Auth state, navigates to
    RoleSelect.

4.10 Localize strings.xml into AS, HI, BN. Use AI4Bharat or your
     best Indian-language knowledge. Mark any uncertain translations
     with a TODO comment.

4.11 End-to-end test on two devices: one parent, one student (minor).
     Both can complete linking in <2 minutes. Verify Firestore docs.

4.12 Run all tests, lint, final commit "feat: complete sprint 2
     auth and consent flow".

═══════════════════════════════════════════════════════════════════
GLOBAL CONSTRAINTS
═══════════════════════════════════════════════════════════════════

- Do NOT commit google-services.json or any Firebase service
  account keys.
- Do NOT use string literals for routes; use the type-safe routes
  in Section 6 of the spec.
- Do NOT add new top-level dependencies without listing them in
  libs.versions.toml.
- Do NOT use LiveData. Do NOT use RxJava. Do NOT use Java.
- Do NOT skip writing tests for ViewModels.
- If a step in this prompt seems wrong, say so before doing it.
- Push to git remote `origin` after every committed phase.

When in doubt about the spec, ask me. Do not improvise around the
spec to "unblock yourself."
```

---

## What you should do *while* Claude Code works

Sprint 2 has Firebase setup that requires *your* hands, not Claude Code's. Do these in parallel:

### Before starting the session

1. **Create the Firebase project** at console.firebase.google.com.
   - Project name: `Invigilator` (or similar — your call)
   - Region: `asia-south1` (Mumbai)
   - Disable Google Analytics for now (we don't need it; we have Crashlytics)

2. **Add an Android app to the project.**
   - Package: `app.invigilator`
   - SHA-1: get it from Android Studio → Gradle → `signingReport` task, or run `./gradlew signingReport` in terminal. Copy the debug variant SHA-1.

3. **Download `google-services.json`** and save to `app/google-services.json` in your project. Do NOT commit it (`.gitignore` already excludes it).

4. **Enable Phone Authentication.** Build → Authentication → Sign-in method → Phone → Enable.

5. **Add test phone numbers.** Same Phone screen, scroll down to "Phone numbers for testing" and add:
   ```
   +91 99999 99991  →  123456
   +91 99999 99992  →  654321
   +91 99999 99993  →  111111
   ```
   Use these during development to skip real SMS. Real SMS costs free-tier quota and is slow.

6. **Enable Firestore.** Build → Firestore Database → Create database → Start in **production mode** (not test mode — we want our rules enforced from day one) → region `asia-south1`.

7. **Enable Cloud Functions.** Build → Functions. You'll need to be on the Blaze (pay-as-you-go) plan. There's a generous free tier; you won't pay anything in development. Add a payment method but expect ₹0 bills until real users come.

8. **Install Firebase CLI on your Mac:**
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase use --add  # select your invigilator project
   ```

### During the session

When Claude Code finishes Phase 1, it'll commit and run tests. You don't need to do anything except let it work.

When it finishes Phase 2, **you deploy the rules and functions:**
```bash
firebase deploy --only firestore:rules
cd backend/functions
npm install
cd ../..
firebase deploy --only functions
```

When it finishes Phase 3 and stops, **test the signup flow yourself on your phone** with one of the test phone numbers. You should be able to:
- Choose "Student"
- Enter `+91 99999 99991`
- Enter OTP `123456` (the test code)
- Pick a date of birth
- Enter a name
- Land on the "Phase 4 not yet implemented" placeholder

If that works end-to-end, **bring me the summary Claude Code printed** and I'll green-light Phase 4. If it doesn't work, bring me the error.

### After Phase 4

The end-of-sprint test is in Section 10 of the spec. Two real devices, real-feeling flow. If linking happens in under two minutes and Firestore shows correct documents, we have a working signup with legally defensible consent.

That's a real product foundation. Sprint 3 then builds the session timer and the foreground app detector — the actual *invigilation* part.
