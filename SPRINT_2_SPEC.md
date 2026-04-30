# Sprint 2 — Auth foundation and DPDP consent

**Goal:** A working, legally-defensible signup and consent flow for parents and students. End of sprint = anyone can install the app, create an account in their role, and (if a parent–student pair) link with a 6-digit code and record verifiable consent.

**Estimated effort:** 4–6 hours of focused Claude Code work, possibly split across two sessions. The Firebase phone-auth setup typically eats 30–60 minutes of that on its own.

---

## 1. The user flows we're building

### Flow A — Adult student (18+), no parent link

1. Open app → "Get started"
2. Choose role: "Student"
3. Enter date of birth → app calculates age → ≥18 → adult path
4. Enter phone number → receive OTP → enter OTP → authenticated
5. Enter display name
6. Show consent text (`AdultStudentSelfConsent_v1.0`) → student taps "I agree" → record stored
7. Land on placeholder home: "Today's session" screen
8. Optional: "Link a parent" button visible in profile (Sprint 3+)

### Flow B — Minor student (under 18), parent already exists

1. Open app → "Get started"
2. Choose role: "Student"
3. Enter date of birth → <18 → minor path
4. Enter phone, OTP, name → authenticated, but in `pending_consent` state
5. App generates a 6-digit linking code (e.g. `4-7-2-9-1-3`) and shows it on screen
6. Student tells parent the code (in person, phone call, WhatsApp — out of band)
7. Student waits on "Waiting for parent..." screen
8. Parent (already an Invigilator user) opens app → "Add a student" → enters code
9. Parent sees student's name + age, taps "Continue to consent"
10. Parent reads consent text (`ParentForMinorConsent_v1.0`), enters their full name as a text "signature", taps "I consent"
11. Both sides observe linkage complete → student moves to active state, parent sees student in their list

### Flow C — Minor student, parent has not signed up yet

Same as Flow B steps 1–7. Then:

8. Parent opens app fresh (no account) → "I am a parent" → does phone-OTP signup
9. App asks "Do you have a code from your student?" → yes → enters code
10. Continues from Flow B step 9.

### Flow D — Adult student who *opts in* to parent monitoring

After Flow A completes, student taps "Link a parent" → generates code → parent enters code → parent sees student → parent consents (`ParentMonitoringAdultStudent_v1.0`) → student receives in-app prompt: "Your parent has requested monitoring access. Approve?" → student approves with re-auth (a fresh OTP) → linkage complete.

This flow is **deferred to Sprint 3.** For Sprint 2 we build the data model to support it but don't ship the UI.

### Flow E — Parent signs up first, then a student

1. Parent installs app → "Get started" → "Parent"
2. Phone-OTP, name, agree to ToS
3. Land on placeholder "My students" — empty state
4. "Add a student" button → "Enter the 6-digit code from your student" — but they don't have one yet
5. Show alternative: "Don't have a code? Ask your child to install Invigilator and choose 'Student' to get one."
6. Parent waits, child later sends code, parent enters it, continues from Flow B step 9.

---

## 2. Data model — every Firestore write this sprint produces

### `/users/{uid}` — created at signup

```kotlin
data class UserDoc(
    val uid: String,
    val role: String,                // "parent" | "student"
    val displayName: String,
    val phoneNumber: String,         // E.164 format, e.g. "+919876543210"
    val dateOfBirth: Timestamp?,     // null for parents (we don't ask)
    val createdAt: Timestamp,
    val accountStatus: String,       // "pending_consent" | "active" | "suspended"
    val consentRefs: List<DocumentReference> = emptyList()  // pointers to /consents/{id}
)
```

The `accountStatus` field is the gate. A `pending_consent` student account can authenticate but cannot start a study session. Only `active` accounts proceed to the main app.

### `/users/{parentUid}/linkedStudents/{studentUid}` — created when parent links a student

```kotlin
data class LinkedStudentDoc(
    val studentUid: String,
    val studentDisplayName: String,
    val linkedAt: Timestamp,
    val linkType: String,            // "parent_minor" | "parent_adult_optin"
    val consentRecordId: String      // pointer to /consents/{id}
)
```

### `/consents/{consentId}` — the legally-significant audit record

```kotlin
data class ConsentDoc(
    val consentId: String,
    val consentType: String,         // "AdultStudentSelfConsent"
                                     // | "ParentForMinorConsent"
                                     // | "ParentMonitoringAdultStudent"
                                     // | "ParentTermsOfService"
    val consentVersion: String,      // e.g. "v1.0"
    val consentTextHash: String,     // SHA-256 of the exact text shown
    val consentLanguage: String,     // "as" | "hi" | "bn" | "en"
    val consenterUid: String,        // who signed
    val consenterRole: String,       // "parent" | "student"
    val subjectUid: String,          // who the consent is *about*
                                     // (= consenterUid for self-consent)
    val signatureText: String,       // full name typed by user as e-signature
    val signedAt: Timestamp,         // server timestamp, never client-provided
    val deviceFingerprint: String,   // androidId-derived hash, NOT raw androidId
    val ipHash: String?,             // SHA-256 of IP, set by Cloud Function
    val withdrawn: Boolean = false,  // true after user revokes
    val withdrawnAt: Timestamp? = null
)
```

**Critical:** `signedAt` and `ipHash` are written by a Cloud Function, never by the client. A malicious client could otherwise forge timestamps. Client writes everything except those two fields; a Firestore trigger or HTTPS callable populates them on creation.

### `/linkingCodes/{code}` — short-lived, server-generated

```kotlin
data class LinkingCodeDoc(
    val code: String,                // the 6 digits, also the doc ID
    val studentUid: String,
    val createdAt: Timestamp,
    val expiresAt: Timestamp,        // 30 minutes from creation
    val claimedBy: String? = null,   // parent UID once consumed
    val claimedAt: Timestamp? = null
)
```

Codes are **single-use, 30-minute expiry, regenerable**. If a parent doesn't claim within 30 min, student taps "Get new code." Old code becomes invalid. A scheduled Cloud Function purges expired unclaimed codes nightly.

---

## 3. Firestore security rules

Place in `firestore.rules`. Test with the Firebase emulator before deploying.

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {

    // Users can read/write only their own user doc
    match /users/{uid} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow create: if request.auth != null
                    && request.auth.uid == uid
                    && request.resource.data.role in ['parent', 'student'];
      allow update: if request.auth != null
                    && request.auth.uid == uid
                    // Don't allow changing role or createdAt after creation
                    && request.resource.data.role == resource.data.role
                    && request.resource.data.createdAt == resource.data.createdAt;
      allow delete: if false;  // soft-delete only, via Cloud Function
    }

    // A parent can read their linked students subcollection
    match /users/{parentUid}/linkedStudents/{studentUid} {
      allow read: if request.auth != null && request.auth.uid == parentUid;
      allow create: if request.auth != null
                    && request.auth.uid == parentUid
                    // Must reference a real consent record
                    && request.resource.data.consentRecordId is string;
      allow update, delete: if false;  // immutable; unlink via Cloud Function
    }

    // Consents are immutable audit records
    match /consents/{consentId} {
      allow read: if request.auth != null
                  && (request.auth.uid == resource.data.consenterUid
                      || request.auth.uid == resource.data.subjectUid);
      allow create: if request.auth != null
                    && request.auth.uid == request.resource.data.consenterUid
                    // signedAt and ipHash must be absent on client write —
                    // Cloud Function populates them
                    && !('signedAt' in request.resource.data.keys())
                    && !('ipHash' in request.resource.data.keys());
      allow update: if request.auth != null
                    && request.auth.uid == resource.data.consenterUid
                    // Only allow toggling withdrawn flag
                    && request.resource.data.diff(resource.data).affectedKeys()
                       .hasOnly(['withdrawn', 'withdrawnAt']);
      allow delete: if false;
    }

    // Linking codes — students create, parents read+claim, server cleans up
    match /linkingCodes/{code} {
      allow read: if request.auth != null;  // parent reads to validate
      allow create: if request.auth != null
                    && request.auth.uid == request.resource.data.studentUid;
      allow update: if request.auth != null
                    // Only the parent claiming can update, and only to set claim fields
                    && resource.data.claimedBy == null
                    && request.resource.data.claimedBy == request.auth.uid;
      allow delete: if false;  // server purges
    }
  }
}
```

**Why these rules matter:** Without them, any authenticated user could read any consent record, forge a `signedAt` timestamp, or claim someone else's linking code. These rules are the difference between "we have a database" and "we have a defensible legal posture."

---

## 4. Cloud Functions — what runs server-side

Two functions for this sprint. Both go in `backend/functions/src/`.

### `onConsentCreate` — Firestore trigger

Trigger: `onCreate` of any `/consents/{consentId}` document.

```typescript
export const onConsentCreate = functions
  .region('asia-south1')
  .firestore.document('consents/{consentId}')
  .onCreate(async (snap, context) => {
    const data = snap.data();

    // Server-write the trusted timestamp
    const updates = {
      signedAt: admin.firestore.FieldValue.serverTimestamp(),
      // ipHash is set in an HTTPS callable instead, since triggers don't see client IPs
    };

    // Validate the consent text hash matches a known version
    const expectedHash = CONSENT_VERSION_HASHES[data.consentType]?.[data.consentVersion];
    if (!expectedHash || expectedHash !== data.consentTextHash) {
      // Mark the consent as invalid and log
      await snap.ref.update({ ...updates, invalidated: true, invalidationReason: 'hash_mismatch' });
      console.error(`Consent ${context.params.consentId}: hash mismatch`);
      return;
    }

    await snap.ref.update(updates);
  });
```

The hash check is **important.** It guarantees that the consent text the user saw was the official versioned text, not something a tampered client showed. The expected hashes are committed in source.

### `claimLinkingCode` — HTTPS callable

```typescript
export const claimLinkingCode = functions
  .region('asia-south1')
  .https.onCall(async (data, context) => {
    if (!context.auth) throw new HttpsError('unauthenticated', 'Must be signed in');
    const { code } = data;
    if (!/^\d{6}$/.test(code)) throw new HttpsError('invalid-argument', 'Bad code');

    const codeRef = db.collection('linkingCodes').doc(code);

    return db.runTransaction(async (tx) => {
      const codeSnap = await tx.get(codeRef);
      if (!codeSnap.exists) throw new HttpsError('not-found', 'Code not found');
      const codeData = codeSnap.data()!;

      if (codeData.claimedBy) throw new HttpsError('already-exists', 'Code already used');
      if (codeData.expiresAt.toMillis() < Date.now()) {
        throw new HttpsError('deadline-exceeded', 'Code expired');
      }

      // Look up student
      const studentSnap = await tx.get(db.collection('users').doc(codeData.studentUid));
      if (!studentSnap.exists) throw new HttpsError('not-found', 'Student not found');

      tx.update(codeRef, {
        claimedBy: context.auth!.uid,
        claimedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return {
        studentUid: codeData.studentUid,
        studentDisplayName: studentSnap.data()!.displayName,
        studentDateOfBirth: studentSnap.data()!.dateOfBirth,
      };
    });
  });
```

Returning the student's display name and DOB lets the parent screen show "Confirm: Aarav Mishra, age 16" before consenting. This is good UX (parent confirms it's their actual kid) and good legal hygiene (no surprise consents).

---

## 5. Module organization in :core

All of this lives in `:core` (with the exception of UI screens which live in `:app`).

```
core/
└── src/main/kotlin/app/invigilator/core/
    ├── auth/
    │   ├── AuthRepository.kt            // interface
    │   ├── AuthRepositoryImpl.kt        // Firebase Auth wrapper
    │   ├── AuthError.kt                 // sealed class
    │   └── AuthState.kt                 // sealed class for state machine
    ├── consent/
    │   ├── ConsentRepository.kt
    │   ├── ConsentRepositoryImpl.kt
    │   ├── ConsentType.kt               // enum
    │   ├── ConsentVersion.kt            // const ConsentVersions object
    │   └── ConsentTextProvider.kt       // resolves type + version + locale to text
    ├── linking/
    │   ├── LinkingRepository.kt
    │   ├── LinkingRepositoryImpl.kt
    │   └── LinkingError.kt
    ├── user/
    │   ├── UserRepository.kt
    │   ├── UserRepositoryImpl.kt
    │   ├── UserRole.kt                  // enum: PARENT, STUDENT
    │   └── AccountStatus.kt             // enum: PENDING_CONSENT, ACTIVE, SUSPENDED
    ├── di/
    │   └── CoreModule.kt                // Hilt @Module @InstallIn(SingletonComponent::class)
    └── util/
        ├── Result.kt                    // typealias kotlin.Result, helpers
        └── PhoneFormatter.kt            // E.164 normalization for Indian numbers
```

UI screens live in `:app`:

```
app/src/main/kotlin/app/invigilator/
├── ui/
│   ├── auth/
│   │   ├── PhoneEntryScreen.kt
│   │   ├── OtpEntryScreen.kt
│   │   ├── PhoneEntryViewModel.kt
│   │   └── OtpEntryViewModel.kt
│   ├── onboarding/
│   │   ├── RoleSelectScreen.kt
│   │   ├── DobEntryScreen.kt
│   │   ├── NameEntryScreen.kt
│   │   └── OnboardingViewModel.kt
│   ├── consent/
│   │   ├── ConsentScreen.kt             // generic, parameterized by ConsentType
│   │   └── ConsentViewModel.kt
│   ├── linking/
│   │   ├── ShareCodeScreen.kt           // student-side, shows code
│   │   ├── EnterCodeScreen.kt           // parent-side, enters code
│   │   ├── LinkingViewModel.kt
│   │   └── LinkingPendingScreen.kt      // student-side, "waiting"
│   ├── home/
│   │   ├── ParentHomeScreen.kt          // placeholder "My students"
│   │   └── StudentHomeScreen.kt         // placeholder "Today's session"
│   └── nav/
│       ├── InvigilatorNavHost.kt
│       └── Routes.kt                    // sealed class of typed routes
```

---

## 6. Patterns to follow — read these carefully

These patterns establish the conventions every future sprint copies.

### ViewModel pattern

```kotlin
@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneEntryUiState())
    val uiState: StateFlow<PhoneEntryUiState> = _uiState.asStateFlow()

    fun onEvent(event: PhoneEntryEvent) {
        when (event) {
            is PhoneEntryEvent.PhoneChanged -> _uiState.update { it.copy(phone = event.phone, error = null) }
            PhoneEntryEvent.SubmitClicked -> sendOtp()
        }
    }

    private fun sendOtp() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.sendOtp(uiState.value.phone)
                .onSuccess { _uiState.update { it.copy(isLoading = false, otpSent = true) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.toUserMessage()) } }
        }
    }
}

data class PhoneEntryUiState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val otpSent: Boolean = false,
    val error: String? = null,
)

sealed interface PhoneEntryEvent {
    data class PhoneChanged(val phone: String) : PhoneEntryEvent
    data object SubmitClicked : PhoneEntryEvent
}
```

**Rules:**
- ViewModels expose exactly one `StateFlow<UiState>`.
- ViewModels accept events through one `onEvent(event)` method.
- ViewModels never throw. They convert errors to messages on the UI state.
- ViewModels never reference Compose, Android framework, or Firebase types directly.

### Repository pattern

```kotlin
interface AuthRepository {
    suspend fun sendOtp(phoneE164: String): Result<Unit>
    suspend fun verifyOtp(otp: String): Result<UserId>
    suspend fun signOut(): Result<Unit>
    val authState: Flow<AuthState>
}

sealed class AuthError(message: String) : Exception(message) {
    data object InvalidPhone : AuthError("Invalid phone number")
    data object InvalidOtp : AuthError("Wrong code")
    data object OtpExpired : AuthError("Code expired")
    data object QuotaExceeded : AuthError("Too many attempts. Try later.")
    data class Network(val cause: Throwable) : AuthError("Network problem")
    data class Unknown(val cause: Throwable) : AuthError("Unexpected error")
}
```

**Rules:**
- Repositories return `Result<T>` (Kotlin's stdlib `Result`), never throw across the boundary.
- Repositories live in `:core`, are interfaces. Implementations are `internal class FooRepositoryImpl @Inject constructor(...) : FooRepository`.
- Implementations are bound via Hilt `@Binds` in `CoreModule`.
- Repositories expose `Flow<T>` for streams (auth state, user changes), `suspend fun ... : Result<T>` for one-shot operations.

### Compose screen pattern

```kotlin
// Stateless: receives state and event handler. Easy to preview.
@Composable
fun PhoneEntryScreen(
    state: PhoneEntryUiState,
    onEvent: (PhoneEntryEvent) -> Unit,
    modifier: Modifier = Modifier,
) { /* layout only */ }

// Stateful: wires up the ViewModel. Used by NavHost.
@Composable
fun PhoneEntryRoute(
    onOtpSent: () -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.otpSent) {
        if (state.otpSent) onOtpSent()
    }
    PhoneEntryScreen(state = state, onEvent = viewModel::onEvent)
}
```

### Navigation pattern

```kotlin
sealed interface Route {
    @Serializable data object RoleSelect : Route
    @Serializable data class PhoneEntry(val role: String) : Route
    @Serializable data class OtpEntry(val role: String, val phone: String) : Route
    @Serializable data class DobEntry(val role: String) : Route
    @Serializable data object NameEntry : Route
    @Serializable data class Consent(val type: String) : Route
    @Serializable data object StudentShareCode : Route
    @Serializable data object ParentEnterCode : Route
    @Serializable data object StudentLinkingPending : Route
    @Serializable data object ParentHome : Route
    @Serializable data object StudentHome : Route
}
```

Use **type-safe Compose navigation** (Navigation 2.9+). No string routes. No `Bundle` argument extraction.

### Localization pattern

Every user-facing string in resource files:

```
core/src/main/res/values/strings.xml          # English (default)
core/src/main/res/values-as/strings.xml       # Assamese
core/src/main/res/values-hi/strings.xml       # Hindi
core/src/main/res/values-bn/strings.xml       # Bengali
```

Consent texts are **not** in `strings.xml` (they're versioned legal documents). They live in:

```
core/src/main/assets/consents/
    AdultStudentSelfConsent_v1.0_en.txt
    AdultStudentSelfConsent_v1.0_as.txt
    AdultStudentSelfConsent_v1.0_hi.txt
    AdultStudentSelfConsent_v1.0_bn.txt
    ParentForMinorConsent_v1.0_en.txt
    ParentForMinorConsent_v1.0_as.txt
    ParentForMinorConsent_v1.0_hi.txt
    ParentForMinorConsent_v1.0_bn.txt
    ParentTermsOfService_v1.0_en.txt
    ParentTermsOfService_v1.0_as.txt
    ParentTermsOfService_v1.0_hi.txt
    ParentTermsOfService_v1.0_bn.txt
```

**Drafts of the consent texts are at the end of this document.** Have a lawyer review before commercial launch — but the drafts are good enough for development and internal pilot.

`ConsentTextProvider` reads the asset file, computes its SHA-256 (this is the hash that goes into the consent record), and returns both the text and hash. The expected hashes are baked into a Kotlin constant at build time so we know if anyone tampers with the asset file:

```kotlin
object ConsentVersions {
    val CURRENT = mapOf(
        ConsentType.ADULT_STUDENT_SELF to "v1.0",
        ConsentType.PARENT_FOR_MINOR to "v1.0",
        ConsentType.PARENT_TERMS_OF_SERVICE to "v1.0",
    )

    // SHA-256 of each consent file. Generated at build time by a Gradle task
    // that reads the assets and writes to a generated Kotlin file.
    // If the asset is modified without bumping the version, build fails.
    val EXPECTED_HASHES = mapOf(
        "AdultStudentSelfConsent_v1.0_en" to "...",
        // etc
    )
}
```

---

## 7. Testing requirements

Unit tests (live in module's `src/test/`):

- `ConsentRepositoryTest` — verifies hash computation, type/version/language resolution
- `LinkingCodeGeneratorTest` — verifies 6-digit codes are random enough, no leading zeros stripped
- `PhoneFormatterTest` — verifies Indian phone normalization (10-digit, +91-prefix, +91 with space, etc.)
- `OnboardingStateMachineTest` — verifies the role + age + linking branching gives the right next-screen
- All ViewModels — verify state transitions on events (use `Turbine` for `StateFlow`)

No instrumented tests this sprint. We'll add them when we add the app blocker (Sprint 4).

---

## 8. Firebase setup checklist (do before running)

Claude Code should produce a `docs/firebase-setup.md` with:

1. Create Firebase project named "Invigilator" in `asia-south1` (Mumbai).
2. Add Android app with package `app.invigilator`.
3. Download `google-services.json`, place at `app/google-services.json` (gitignored).
4. Enable Phone Authentication in Auth tab.
5. Add the SHA-1 fingerprint of your debug keystore (`./gradlew signingReport` produces it).
6. Enable Firestore in Native mode, region `asia-south1`.
7. Deploy security rules: `firebase deploy --only firestore:rules`.
8. Deploy Cloud Functions: `firebase deploy --only functions`.
9. Test phone numbers (for development, no SMS used): add `+91 99999 99991 → 123456` etc. in Auth → Sign-in method → Phone → Phone numbers for testing.

The test phone numbers are critical. Real SMS verification eats free-tier quota fast and is slow during development.

---

## 9. Failure modes to handle gracefully

The spec calls these out explicitly so they don't get forgotten:

| Failure | What user sees | What we do |
|---|---|---|
| Invalid phone format | Inline red error under field | Don't enable submit button |
| OTP send fails (network) | Snackbar "Could not send code. Try again." | Keep input, allow retry |
| OTP wrong | Inline error, OTP field clears | Allow retry up to 5 times before lockout |
| OTP expired | "Code expired. Get a new one." with button | Issue new code, restart 60s timer |
| User already exists with that phone | "An account exists. Sign in instead?" | Switch to sign-in flow seamlessly |
| Code not found | "Wrong code. Check with your student." | Stay on screen |
| Code expired | "This code expired. Ask for a new one." | Stay on screen |
| Code already claimed | "This student is already linked to a parent." | Show contact-support note |
| Linked student is over 18 (when parent is consenting "for minor") | Block flow with explanation | Suggest "ParentMonitoringAdultStudent" path (Sprint 3) |
| Consent text hash mismatch | Server rejects, client shows generic error | Log to Crashlytics with high severity |
| User taps back during consent | Confirm dialog "Cancel signup?" | If yes, sign out and return to start |

---

## 10. Definition of done

✅ All four user flows (A, B, C, E) work on a real device, end to end.
✅ Firestore documents are created correctly per Section 2.
✅ Security rules deployed and tested with the emulator.
✅ Cloud Functions deployed and tested.
✅ Consent texts in 4 languages (EN, AS, HI, BN) load correctly.
✅ Hash check works (manually corrupt an asset, verify build fails).
✅ All unit tests pass.
✅ `./gradlew lint` is clean.
✅ Two devices: parent on one, student on the other, can complete linking in <2 minutes.
✅ Logout works. Re-login goes to correct home screen based on `accountStatus`.
✅ Git commits exist and are pushed to GitHub.

---

## Appendix — Consent text drafts

These are **drafts for development use.** They are *not* a substitute for a lawyer's review before commercial launch. The drafts are written in plain English; localization comes after legal review of the English versions.

### `AdultStudentSelfConsent_v1.0_en.txt`

```
Invigilator — Student Consent (18+)

I confirm that I am 18 years of age or older.

By tapping "I agree", I consent to the following:

1. Invigilator will run on this device and may detect when I open
   distracting apps during a study session that I have started.

2. During an active session, Invigilator may close distracting apps
   on my behalf.

3. Invigilator will keep a log of my study sessions on its servers
   for up to 90 days. This log includes session times, app usage
   events during sessions, and AI tutor interactions. The log does
   not include the contents of my screen, photos, or audio recordings.

4. I can stop any session at any time by tapping the stop button
   in Invigilator.

5. I can delete my account and all associated data at any time
   from the Settings screen.

6. I am consenting on my own behalf. No parent, school, or other
   party has signed this consent for me.

I have read and understood the above.

Consent version: v1.0
```

### `ParentForMinorConsent_v1.0_en.txt`

```
Invigilator — Parental Consent for a Minor

I confirm that I am the parent or legal guardian of the student
whose account I am about to link to my own.

I understand that:

1. Invigilator is an educational supervision app. It will run on
   the student's device and may, during sessions I or the student
   start, detect and close distracting apps.

2. Invigilator will collect a log of the student's study sessions,
   including session times, app usage events during sessions, and
   AI tutor interactions. This log will be stored on Invigilator's
   servers in India for up to 90 days, then automatically deleted.

3. Invigilator does NOT collect: the contents of the student's
   screen, photos, audio, location, contacts, or browsing history
   outside of study sessions.

4. If the student uses Invigilator's optional camera mode, the camera
   feed is processed only on the student's device. Camera video is
   never uploaded, stored, or transmitted.

5. I will receive notifications when Invigilator detects that the
   student is persistently distracted during a session.

6. I can revoke this consent at any time from the Parent dashboard.
   When I revoke, the student's account becomes inactive and all
   associated data is deleted within 7 days.

7. The student has been informed in age-appropriate language that
   this app supervises their study time.

By typing my full name below and tapping "I consent", I am providing
verifiable parental consent under Section 9 of India's Digital
Personal Data Protection Act, 2023.

Consent version: v1.0
```

### `ParentTermsOfService_v1.0_en.txt`

```
Invigilator — Parent Account Terms

By creating a parent account, I agree to:

1. Use Invigilator only to supervise students for whom I am the
   parent or legal guardian.

2. Provide truthful information about my identity and my relationship
   to any linked student.

3. Pay any subscription fees I sign up for, on the schedule shown
   at signup.

4. Not share my account credentials with any other person.

5. Use Invigilator's notifications and reports for the welfare of
   the student, not for any other purpose.

I understand that Invigilator is provided by [Your registered
business name and address — to be filled before launch] and that
my use is governed by the laws of India.

Consent version: v1.0
```

The Assamese, Hindi, and Bengali translations should be done by a fluent translator (ideally a lawyer-translator) before pilot launch. **Do not use machine translation for legal documents.** For development, Claude Code can produce rough translations clearly marked `[DRAFT — NOT FOR PRODUCTION]` at the top of each file.

---

## End of spec

The Claude Code prompt that hands off this entire document is in a separate file: `SPRINT_2_CLAUDE_CODE_PROMPT.md`.
