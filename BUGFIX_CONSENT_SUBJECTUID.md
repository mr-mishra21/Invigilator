# Bug Fix Session — Consent subjectUid for ParentForMinorConsent

This is a small, surgical fix. Do exactly what's described. Do not refactor.

═══════════════════════════════════════════════════════════════════════
THE BUG
═══════════════════════════════════════════════════════════════════════

When a parent signs ParentForMinorConsent, the consent document is written
with:
  consenterUid: [parent's uid] ✓ (correct)
  subjectUid: [parent's uid] ✗ (WRONG — should be [student's uid])

Root cause: ConsentRepository.recordConsent() does not accept a subjectUid
parameter. It defaults to auth.uid (the parent), which is correct for
ADULT_STUDENT_SELF and PARENT_TOS (where consenter = subject), but wrong
for PARENT_FOR_MINOR (where consenter ≠ subject).

When completeLinking Cloud Function validates the consent, it checks:
  if (consentDoc.subjectUid != studentUid) reject("consent not for this student")

The function correctly rejects with PERMISSION_DENIED / generic error because
the consent's subjectUid is the parent, not the student.

Solution: Thread the studentUid through to the consent write as subjectUid.

═══════════════════════════════════════════════════════════════════════
STEP 1 — Update ConsentRepository interface
═══════════════════════════════════════════════════════════════════════

In core/src/main/kotlin/.../core/consent/ConsentRepository.kt:

Change:
```kotlin
suspend fun recordConsent(
    type: ConsentType,
    version: String,
    lang: String,
    signatureText: String,
): Result<String>
```

To:
```kotlin
suspend fun recordConsent(
    type: ConsentType,
    version: String,
    lang: String,
    signatureText: String,
    subjectUid: String? = null,  // NEW: uid of the consent subject
): Result<String>
```

Add a comment:
```
// subjectUid: if provided, writes this as the subject of the consent.
// If null, defaults to auth.uid (the consenter). Use for PARENT_FOR_MINOR
// to pass the student's uid so the consent records who it's about.
```

═══════════════════════════════════════════════════════════════════════
STEP 2 — Update ConsentRepositoryImpl
═══════════════════════════════════════════════════════════════════════

In core/src/main/kotlin/.../core/consent/ConsentRepositoryImpl.kt:

Find the recordConsent() implementation. At the point where the consent doc
is created and written to Firestore, change:

```kotlin
val consentDoc = ConsentDoc(
    consentId = consentId,
    consentType = type,
    consentVersion = version,
    consentTextHash = textHash,
    consenterUid = auth.uid,
    subjectUid = auth.uid,  // ← CHANGE THIS LINE
    ...
)
```

To:

```kotlin
val finalSubjectUid = subjectUid ?: auth.uid
val consentDoc = ConsentDoc(
    consentId = consentId,
    consentType = type,
    consentVersion = version,
    consentTextHash = textHash,
    consenterUid = auth.uid,
    subjectUid = finalSubjectUid,  // ← USE THE PARAMETER
    ...
)
```

Where `subjectUid` is the new parameter passed in.

═══════════════════════════════════════════════════════════════════════
STEP 3 — Thread subjectUid through ConsentRoute and ConsentScreen
═══════════════════════════════════════════════════════════════════════

In app/src/main/kotlin/.../app/ui/consent/ConsentRoute.kt (or wherever
ConsentRoute is defined):

The route currently looks something like:
```kotlin
@Composable
fun ConsentRoute(
    type: ConsentType,
    onComplete: (consentId: String) -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ConsentScreen(state, onEvent = viewModel::onEvent)
    // ... navigation on isComplete
}
```

Change to:
```kotlin
@Composable
fun ConsentRoute(
    type: ConsentType,
    onComplete: (consentId: String) -> Unit,
    subjectUid: String? = null,  // NEW
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    // ... rest unchanged
}
```

The viewModel needs this too. In ConsentViewModel, find where it reads
SavedStateHandle arguments. Add the subjectUid:

```kotlin
private val subjectUid: String? = savedStateHandle.get("subjectUid")
```

Then pass it to recordConsent in the AgreedTapped event handler:
```kotlin
consentRepository.recordConsent(
    type = type,
    version = version,
    lang = language,
    signatureText = signature,
    subjectUid = subjectUid,  // NEW
)
```

═══════════════════════════════════════════════════════════════════════
STEP 4 — Pass studentUid when navigating to ParentForMinorConsent
═══════════════════════════════════════════════════════════════════════

Find where ConfirmStudentScreen navigates to ConsentScreen with type
PARENT_FOR_MINOR. This is likely in ConfirmStudentScreen, ConfirmStudentRoute,
or the parent linking flow. Search for:
  ConsentRoute or navigate(... PARENT_FOR_MINOR or Route.Consent

When you find it, you'll see something like:
```kotlin
navigate(Route.Consent(type = ConsentType.PARENT_FOR_MINOR))
```

Or:
```kotlin
ConsentRoute(
    type = ConsentType.PARENT_FOR_MINOR,
    onComplete = { consentId -> ... }
)
```

Change to pass the studentUid. You should have studentUid available in
the ConfirmStudentRoute or LinkingViewModel at this point (it came through
the enteCode → claimLinkingCode flow).

```kotlin
navigate(Route.Consent(type = ConsentType.PARENT_FOR_MINOR, subjectUid = studentUid))
```

Or:
```kotlin
ConsentRoute(
    type = ConsentType.PARENT_FOR_MINOR,
    subjectUid = studentUid,  // NEW
    onComplete = { consentId -> ... }
)
```

═══════════════════════════════════════════════════════════════════════
STEP 5 — Verify the routing and types
═══════════════════════════════════════════════════════════════════════

If Route.Consent is a @Serializable data class, add the subjectUid parameter
there too:

```kotlin
data class Consent(
    val type: ConsentType,
    val subjectUid: String? = null,  // NEW
) : Route
```

═══════════════════════════════════════════════════════════════════════
STEP 6 — Tests
═══════════════════════════════════════════════════════════════════════

Update ConsentViewModelTest:
  - Any test that calls recordConsent should verify that when subjectUid
    is provided, it's passed through (mock the repository and assert the call)
  - Add a specific test: "ParentForMinorConsent records student as subject"
    where you pass subjectUid=studentUid and verify the mock recordConsent
    was called with that value

No need to write new tests if existing ones already cover the flow. Just
make sure nothing breaks.

Run tests: `./gradlew testDebugUnitTest`
Lint: `./gradlew lint`
Both must pass.

═══════════════════════════════════════════════════════════════════════
STEP 7 — Build and verify
═══════════════════════════════════════════════════════════════════════

`./gradlew assembleDebug`

Must succeed. If there are type mismatches or navigation issues, fix them
and re-build.

═══════════════════════════════════════════════════════════════════════
STEP 8 — Commit and push
═══════════════════════════════════════════════════════════════════════

Single commit: "fix(consent): pass studentUid as subject in ParentForMinorConsent"

Then: `git push origin main`

Mandatory. Do not end with unpushed commits.

═══════════════════════════════════════════════════════════════════════
DEPLOYMENT — for the human
═══════════════════════════════════════════════════════════════════════

After commit:
  1. `firebase deploy --only firestore:rules functions`
  2. Reinstall app: `./gradlew :app:installDebug`
  3. Test Flow B end to end with fresh accounts (no orphan docs this time)

═══════════════════════════════════════════════════════════════════════
CONSTRAINTS
═══════════════════════════════════════════════════════════════════════

- Do not change ConsentScreen's UI or any other non-consent code.
- Do not refactor the consent flow — only add the parameter.
- The default value (null) ensures ADULT_STUDENT_SELF and PARENT_TOS still
  work without passing anything new.
- Push is mandatory.

When done, print:
  - Files modified
  - Any test changes
  - Confirmation git push succeeded
